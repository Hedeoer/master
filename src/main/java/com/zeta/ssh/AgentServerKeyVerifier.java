package com.zeta.ssh; // 请替换为你的包名

import org.apache.sshd.client.config.hosts.KnownHostEntry;
import org.apache.sshd.client.config.hosts.KnownHostHashValue;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.io.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 自定义服务器密钥验证器 (ServerKeyVerifier) 实现。
 * 主要用于在SSH客户端连接服务器时，验证服务器提供的主机公钥。
 * 如果在本地的 {@code known_hosts} 文件中找不到匹配的服务器公钥，
 * 并且配置允许 ({@code autoAcceptNewKeys = true})，则会自动将新的服务器公钥添加到 {@code known_hosts} 文件中。
 * <p>
 * **安全警告:** 自动接受和添加未知的服务器密钥存在中间人攻击 (MITM) 的风险。
 * 此机制应仅在完全信任网络环境和首次连接到已知服务器的受控场景下使用，
 * 或者作为一种“学习模式”并在之后切换到更严格的验证器。
 * </p>
 */
public class AgentServerKeyVerifier implements ServerKeyVerifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentServerKeyVerifier.class);

    /** known_hosts 文件的路径 */
    private final Path knownHostsPath;
    /** 是否强制执行严格的文件和目录权限 (例如，目录 0700, 文件 0600) */
    private final boolean strictPermissions;
    /** 是否自动接受并添加新的、未知的服务器密钥到 known_hosts 文件 */
    private final boolean autoAcceptNewKeys;

    /**
     * 构造函数，使用默认设置 (强制严格权限并自动接受新密钥)。
     *
     * @param knownHostsPath known_hosts 文件的路径。
     */
    public AgentServerKeyVerifier(Path knownHostsPath) {
        this(knownHostsPath, true, true);
    }

    /**
     * 构造函数。
     *
     * @param knownHostsPath    known_hosts 文件的路径。
     * @param strictPermissions 是否强制执行严格的文件和目录权限。
     * @param autoAcceptNewKeys 是否自动接受并添加新的、未知的服务器密钥。
     */
    public AgentServerKeyVerifier(Path knownHostsPath, boolean strictPermissions, boolean autoAcceptNewKeys) {
        this.knownHostsPath = Objects.requireNonNull(knownHostsPath, "Known hosts path cannot be null");
        this.strictPermissions = strictPermissions;
        this.autoAcceptNewKeys = autoAcceptNewKeys;
    }

    /**
     * 验证服务器提供的主机公钥。
     * 此方法由 SSH 客户端在接收到服务器主机密钥后调用。
     *
     * @param clientSession 当前的客户端会话。
     * @param remoteAddress 服务器的实际网络地址。
     * @param serverKey     服务器提供的主机公钥。
     * @return 如果服务器密钥被接受（已知或已成功添加），则返回 {@code true}；否则返回 {@code false}。
     */
    @Override
    public boolean verifyServerKey(ClientSession clientSession, SocketAddress remoteAddress, PublicKey serverKey) {
        // 获取客户端尝试连接的地址信息
        SocketAddress connectSockAddr = clientSession.getConnectAddress();
        if (!(connectSockAddr instanceof InetSocketAddress)) {
            LOGGER.error("Connect address is not an InetSocketAddress: {}", connectSockAddr);
            return false; // 无法处理非 IP 地址
        }
        InetSocketAddress connectAddress = (InetSocketAddress) connectSockAddr;

        // 用户最初尝试连接的主机名或IP (未经DNS解析的)
        String originalHost = connectAddress.getHostString();
        int port = connectAddress.getPort();
        // 服务器的实际IP地址 (可能与originalHost不同，例如当originalHost是DNS名称时)
        String remoteIp = (remoteAddress instanceof InetSocketAddress)
                ? ((InetSocketAddress) remoteAddress).getAddress().getHostAddress()
                : null;

        // 获取服务器公钥的类型和指纹，用于日志记录
        String serverKeyType = KeyUtils.getKeyType(serverKey);
        String fingerprint = KeyUtils.getFingerPrint(serverKey);

        LOGGER.debug("Verifying server key for connection to '{}' (resolved to IP '{}', port {}). Server presented key type: {}, fingerprint: {}",
                originalHost, (remoteIp != null ? remoteIp : "N/A"), port, serverKeyType, fingerprint);

        try {
            // 步骤 1: 确保 known_hosts 文件的父目录存在并具有适当权限
            ensureKnownHostsParentDirectoryExists();

            // 步骤 2: 加载并检查现有的 known_hosts 条目
            if (Files.exists(knownHostsPath) && Files.size(knownHostsPath) > 0) {
                List<KnownHostEntry> allEntries; // KnownHostEntry.readKnownHostEntries 返回 List
                try (InputStream in = Files.newInputStream(knownHostsPath)) {
                    // 使用 Apache MINA SSHD 提供的 KnownHostEntry.readKnownHostEntries 读取和解析文件
                    allEntries = KnownHostEntry.readKnownHostEntries(in, true);
                } catch (IOException e) {
                    LOGGER.warn("Failed to read or parse known_hosts file at {}: {}. Assuming no valid entries.", knownHostsPath, e.getMessage(), e);
                    allEntries = Collections.emptyList(); // 如果读取失败，则视为空列表
                }

                boolean hostPatternMatchedAtLeastOnce = false; // 标记是否至少有一个主机模式匹配

                // 遍历 known_hosts 文件中的每一个条目
                for (KnownHostEntry entry : allEntries) {
                    // KnownHostEntry.isHostMatch 会处理普通主机名/IP、哈希主机名、带端口的模式等
                    boolean currentHostMatch = entry.isHostMatch(originalHost, port)
                            || (remoteIp != null && entry.isHostMatch(remoteIp, port));

                    if (currentHostMatch) {
                        hostPatternMatchedAtLeastOnce = true; // 找到一个与当前主机/IP匹配的条目
                        AuthorizedKeyEntry keyEntry = entry.getKeyEntry(); // 获取该条目中存储的公钥信息
                        if (keyEntry != null) {
                            try {
                                // 将存储的密钥数据解析为 PublicKey 对象
                                // clientSession 作为 SessionContext 提供必要的上下文
                                // PublicKeyEntryResolver.IGNORING 表示如果解析失败则忽略该条目
                                PublicKey knownKey = keyEntry.resolvePublicKey(clientSession, PublicKeyEntryResolver.IGNORING);
                                // 比较解析出的已知公钥与服务器当前提供的公钥
                                if (knownKey != null && KeyUtils.compareKeys(knownKey, serverKey)) {
                                    LOGGER.info("Server key for '{}:{}' (IP: {}) matches an entry in {}. Accepting.",
                                            originalHost, port, (remoteIp != null ? remoteIp : "N/A"), knownHostsPath);
                                    return true; // 公钥匹配，验证成功
                                }
                            } catch (IOException | GeneralSecurityException | RuntimeException e) {
                                // 捕获解析或比较公钥时可能发生的异常
                                LOGGER.warn("Failed to resolve or compare public key from known_hosts entry \"{}\" for host {}:{}: {}",
                                        entry.getConfigLine(), originalHost, port, e.getMessage());
                                // 继续检查下一个条目
                            }
                        }
                    }
                }

                // 如果至少有一个主机模式匹配了，但所有匹配条目中的公钥都不与当前服务器公钥相同
                if (hostPatternMatchedAtLeastOnce) {
                    LOGGER.warn("WARNING! Server key for '{}:{}' (IP: {}) has CHANGED or does not match existing entries in {}!",
                            originalHost, port, (remoteIp != null ? remoteIp : "N/A"), knownHostsPath);
                    LOGGER.warn("Presented key: {} ({}), Fingerprint: {}", serverKeyType, fingerprint);
                    return false; // 服务器密钥已更改，这是一个严重的安全警告，应拒绝连接
                }
            }

            // 步骤 3: 处理新密钥
            // 如果执行到这里，表示:
            //   a) known_hosts 文件不存在或为空。
            //   b) known_hosts 文件中没有找到任何与当前主机/IP匹配的主机模式条目。
            //   (注意：如果主机模式匹配但公钥不匹配的情况，上面已经返回 false 了)
            if (autoAcceptNewKeys) {
                // 如果配置允许自动接受新密钥
                LOGGER.info("No existing valid known_host entry found for '{}:{}' (IP: {}). Attempting to add new key to {}.",
                        originalHost, port, (remoteIp != null ? remoteIp : "N/A"), knownHostsPath);
                return addNewKeyToKnownHosts(originalHost, port, serverKey, fingerprint);
            } else {
                // 如果不允许自动接受新密钥
                LOGGER.warn("No existing valid known_host entry found for '{}:{}' (IP: {}) and autoAcceptNewKeys is false. Rejecting.",
                        originalHost, port, (remoteIp != null ? remoteIp : "N/A"));
                return false;
            }

        } catch (IOException e) {
            // 捕获在 ensureKnownHostsParentDirectoryExists 或其他文件操作中可能发生的IO异常
            LOGGER.error("IOException during server key verification for {}:{}: {}", originalHost, port, e.getMessage(), e);
            return false;
        } catch (Exception e) {
            // 捕获任何其他意外异常
            LOGGER.error("Unexpected exception during server key verification for {}:{}: {}", originalHost, port, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 将新的服务器公钥添加到 known_hosts 文件中。
     *
     * @param host        连接的主机名/IP。
     * @param port        连接的端口。
     * @param serverKey   要添加的服务器公钥。
     * @param fingerprint 公钥的指纹 (用于日志记录)。
     * @return 如果成功添加，则返回 {@code true}；否则返回 {@code false}。
     */
    private boolean addNewKeyToKnownHosts(String host, int port, PublicKey serverKey, String fingerprint) {
        String publicKeyString;
        try {
            // 获取密钥类型 (如 "ssh-rsa", "ssh-ed25519")
            String keyType = KeyUtils.getKeyType(serverKey);
            // 获取公钥的编码字节 (通常是 X.509 SubjectPublicKeyInfo ASN.1 DER 编码)
            byte[] keyBytes = serverKey.getEncoded();
            // 将编码字节转换为 Base64 字符串
            String b64Key = java.util.Base64.getEncoder().encodeToString(keyBytes);

            if (GenericUtils.isEmpty(keyType) || GenericUtils.isEmpty(b64Key)) {
                LOGGER.error("Failed to get type or encode server key for host: {}", host);
                return false;
            }
            // 构造 known_hosts 文件中公钥部分的标准格式: "key-type base64-encoded-key"
            publicKeyString = keyType + " " + b64Key;

        } catch (Exception e) {
            LOGGER.error("Error converting server key to string for host {}: {}", host, e.getMessage(), e);
            return false;
        }

        // 使用 KnownHostHashValue.createHostPattern 生成标准的主机模式字符串
        // (例如 "hostname" 或 "[hostname]:port" 如果端口非默认)
        String hostPattern = KnownHostHashValue.createHostPattern(host, port);
        // 构造完整的 known_hosts 条目行
        String entryLine = hostPattern + " " + publicKeyString;

        LOGGER.info("Adding new known_host entry: \"{}\" for key fingerprint {}", entryLine, fingerprint);

        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                // 以追加模式打开文件，如果文件不存在则创建
                Files.newOutputStream(knownHostsPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                StandardCharsets.UTF_8))) {
            // 写入条目行并添加行尾符
            writer.write(entryLine + IoUtils.EOL);
        } catch (IOException e) {
            LOGGER.error("Failed to write new entry to {}: {}", knownHostsPath, e.getMessage(), e);
            return false;
        }

        // 如果配置了严格权限，尝试设置新创建或追加后的文件权限为 0600 (rw-------)
        if (strictPermissions) {
            setFilePermissions(knownHostsPath, "rw-------");
        }

        LOGGER.info("Successfully added server key for {}:{} to {}.", host, port, knownHostsPath);
        return true;
    }

    /**
     * 确保 known_hosts 文件的父目录存在，并根据配置尝试设置其权限 (通常是 0700, rwx------)。
     *
     * @throws IOException 如果创建目录失败。
     */
    private void ensureKnownHostsParentDirectoryExists() throws IOException {
        Path parentDir = knownHostsPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            // 如果父目录不存在
            if (strictPermissions) {
                // 如果要求严格权限
                try {
                    // 尝试创建目录并设置 POSIX 权限
                    Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwx------"); // 0700
                    FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);
                    Files.createDirectories(parentDir, attr); // createDirectories 会创建所有不存在的父目录
                    LOGGER.info("Created directory {} with strict permissions.", parentDir);
                } catch (UnsupportedOperationException e) {
                    // 如果文件系统不支持 POSIX 权限 (例如某些 Windows 配置)
                    Files.createDirectories(parentDir); // 回退到不带权限创建
                    LOGGER.info("Created directory {} (POSIX permissions not supported).", parentDir);
                }
            } else {
                // 如果不要求严格权限
                Files.createDirectories(parentDir);
                LOGGER.info("Created directory {} (no strict permissions enforced).", parentDir);
            }
        } else if (parentDir != null && strictPermissions) {
            // 如果父目录已存在，并且要求严格权限，尝试确保其权限正确
            setDirectoryPermissions(parentDir, "rwx------");
        }
    }

    /**
     * 尝试将指定路径的文件的 POSIX 权限设置为给定的权限字符串。
     *
     * @param path       文件路径。
     * @param permString 权限字符串 (例如 "rw-------")。
     */
    private void setFilePermissions(Path path, String permString) {
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString(permString);
            Files.setPosixFilePermissions(path, perms);
            LOGGER.debug("Set permissions for {} to {}", path, permString);
        } catch (UnsupportedOperationException e) {
            // 文件系统不支持 POSIX 权限
            LOGGER.debug("POSIX permissions not supported for {}, cannot set permissions to {}.", path, permString);
        } catch (IOException e) {
            // 设置权限时发生 IO 错误
            LOGGER.warn("Could not set permissions for {} to {}: {}", path, permString, e.getMessage());
        }
    }

    /**
     * 尝试将指定路径的目录的 POSIX 权限设置为给定的权限字符串。
     *
     * @param path       目录路径。
     * @param permString 权限字符串 (例如 "rwx------")。
     */
    private void setDirectoryPermissions(Path path, String permString) {
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString(permString);
            Files.setPosixFilePermissions(path, perms);
            LOGGER.debug("Ensured permissions for directory {} are {}", path, permString);
        } catch (UnsupportedOperationException e) {
            // 文件系统不支持 POSIX 权限，静默忽略
        } catch (IOException e) {
            // 设置权限时发生 IO 错误
            LOGGER.warn("Could not ensure permissions for directory {}: {}. Continuing...", path, e.getMessage());
        }
    }
}