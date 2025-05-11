package com.zeta.ssh;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryDataResolver;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyEncryptionContext;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.common.util.security.bouncycastle.BouncyCastleSecurityProviderRegistrar;
import org.apache.sshd.common.util.security.eddsa.EdDSASecurityProviderRegistrar;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.Security;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

@Slf4j
public class ED25519KeyGenerator {

    static {
        // 静态初始化安全提供者
        Security.addProvider(new BouncyCastleProvider());
        SecurityUtils.registerSecurityProvider(new BouncyCastleSecurityProviderRegistrar());
        SecurityUtils.registerSecurityProvider(new EdDSASecurityProviderRegistrar());
    }

    /**
     * 生成 Ed25519 密钥对并保存到指定文件。如果密钥对已存在且有效，则不会重新生成。
     *
     * @param privateKeyPath 私钥文件的保存路径。
     * @param publicKeyPath  公钥文件的保存路径。
     * @param passphrase     用于加密私钥的密码短语，如果为 null 或空，则私钥不加密。
     * @param comment        公钥文件中的注释。
     * @return 是否生成了新的密钥对
     */
    public static boolean generateAndSaveEd25519KeyPair(Path privateKeyPath, Path publicKeyPath, String passphrase, String comment) {
        // 首先检查密钥对是否存在且有效
        if (areKeysValidAndMatched(privateKeyPath, publicKeyPath, passphrase)) {
            log.info("Valid Ed25519 key pair already exists, skipping generation");
            return false;
        }

        // 密钥对不存在或无效，生成新的密钥对
        log.info("Generating new Ed25519 key pair");
        createNewEd25519KeyPair(privateKeyPath, publicKeyPath, passphrase, comment);
        return true;
    }

    /**
     * 检查指定路径的Ed25519密钥对是否存在且配对
     *
     * @param privateKeyPath 私钥文件路径
     * @param publicKeyPath  公钥文件路径
     * @param passphrase     私钥密码
     * @return 如果密钥对存在且配对返回true，否则返回false
     */
    public static boolean areKeysValidAndMatched(Path privateKeyPath, Path publicKeyPath, String passphrase) {
        // 检查密钥文件是否存在
        boolean privateKeyExists = Files.exists(privateKeyPath);
        boolean publicKeyExists = Files.exists(publicKeyPath);

        // 如果任一文件不存在，则密钥对无效
        if (!privateKeyExists || !publicKeyExists) {
            if (!privateKeyExists) {
                log.info("Private key not found at: {}", privateKeyPath);
            }
            if (!publicKeyExists) {
                log.info("Public key not found at: {}", publicKeyPath);
            }
            return false;
        }

        // 两个文件都存在，检查它们是否配对
        log.info("Found existing key files, checking if they are a valid pair");
        return areKeysMatched(privateKeyPath, publicKeyPath, passphrase);
    }

    /**
     * 检查私钥和公钥是否配对
     *
     * @param privateKeyPath 私钥文件路径
     * @param publicKeyPath  公钥文件路径
     * @param passphrase     私钥密码
     * @return 如果密钥配对返回true，否则返回false
     */
    private static boolean areKeysMatched(Path privateKeyPath, Path publicKeyPath, String passphrase) {
        try {
            // 读取私钥
            FileInputStream inputStream = new FileInputStream(privateKeyPath.toFile());
            Collection<KeyPair> privateKeys = (Collection<KeyPair>) SecurityUtils.loadKeyPairIdentities(
                    null,
                    NamedResource.ofName(passphrase),
                    inputStream,
                    FilePasswordProvider.of(passphrase)
            );


            if (privateKeys.isEmpty()) {
                log.warn("No private keys could be loaded from: {}", privateKeyPath);
                return false;
            }

            // 读取公钥
            String publicKeyContent = Files.readString(publicKeyPath).trim();
            PublicKey loadedPublicKey = PublicKeyEntry.parsePublicKeyEntry(publicKeyContent)
                    .resolvePublicKey(null, Collections.emptyMap(), null);

            if (loadedPublicKey == null) {
                log.warn("Could not parse public key from: {}", publicKeyPath);
                return false;
            }

            // 检查私钥中是否有与公钥匹配的
            for (KeyPair keyPair : privateKeys) {
                if (keyPair.getPublic().equals(loadedPublicKey)) {
                    log.info("Public key and private key are a valid pair");
                    return true;
                }
            }

            log.warn("Public key does not match any of the private keys");
            return false;

        } catch (Exception e) {
            log.error("Error checking key pair match: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 创建新的 Ed25519 密钥对并保存到指定文件。
     *
     * @param privateKeyPath 私钥文件的保存路径。
     * @param publicKeyPath  公钥文件的保存路径。
     * @param passphrase     用于加密私钥的密码短语，如果为 null 或空，则私钥不加密。
     * @param comment        公钥文件中的注释。
     */
    private static void createNewEd25519KeyPair(Path privateKeyPath, Path publicKeyPath, String passphrase, String comment) {
        try (FileOutputStream fileOutputStream = new FileOutputStream(privateKeyPath.toFile())) {
            // 生成 Ed25519 密钥对
            KeyPair keyPair = SecurityUtils.getKeyPairGenerator(SecurityUtils.ED25519).generateKeyPair();
            log.info("Ed25519 KeyPair generated successfully.");

            // 确保父目录存在
            ensureDirectoryExists(privateKeyPath.getParent());
            ensureDirectoryExists(publicKeyPath.getParent());

            // 保存私钥 (使用 OpenSSH 格式)
            OpenSSHKeyPairResourceWriter keyPairWriter = new OpenSSHKeyPairResourceWriter();
            keyPairWriter.writePrivateKey(keyPair, comment, new OpenSSHKeyEncryptionContext(), fileOutputStream);
            log.info("Private key saved to: {}", privateKeyPath);
            setFilePermissions(privateKeyPath, "rw-------");

            // 保存公钥 (OpenSSH authorized_keys 格式)
            String publicKeyString = PublicKeyEntry.toString(keyPair.getPublic(), new PublicKeyEntryDataResolver() {
            });
            Files.writeString(publicKeyPath, publicKeyString, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Public key saved to: {}", publicKeyPath);
            setFilePermissions(publicKeyPath, "rw-r--r--");

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate or save Ed25519 key pair", e);
        }
    }

    private static void ensureDirectoryExists(Path dirPath) throws IOException {
        if (dirPath != null && !Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
            log.info("Created directory: {}", dirPath);
        }
    }

    private static void setFilePermissions(Path path, String permString) {
        try {
            if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString(permString);
                Files.setPosixFilePermissions(path, perms);
                log.info("Set permissions for {} to {}", path, permString);
            } else {
                log.info("POSIX permissions not supported on this system for {}. Skipping permission setting.", path);
            }
        } catch (UnsupportedOperationException e) {
            log.warn("POSIX permissions not supported for {}: {}", path, e.getMessage());
        } catch (IOException e) {
            log.error("Could not set permissions for {} to {}: {}", path, permString, e.getMessage());
        }
    }

    /**
     * 获取~/.ssh/id_ed25519.pub中存储的公钥
     * Path pub = Paths.get(System.getProperty("user.home"), ".ssh", "id_ed25519.pub");
     * @return 公钥
     */
    public static String getPublicKey() {
        String publicKey = "";

        Path pub = null;
        try {
            pub = Paths.get(System.getProperty("user.home"), ".ssh", "id_ed25519.pub");
            Path pri = Paths.get(System.getProperty("user.home"), ".ssh", "id_ed25519");

            // 检查如上目录中是否有Ed25519的公私钥，并且检查是否配对？如果检查不通过则生成新的公私钥
            generateAndSaveEd25519KeyPair(pri, pub, null, "测试");

            publicKey = Files.readString(pub).trim();

        } catch (IOException e) {
            log.error("无法获取{}的公钥，请检查！！", pub);
            throw new RuntimeException(e);
        }
        return publicKey;
    }
}