package com.zeta.ssh; // 假设包名

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.keyverifier.DefaultKnownHostsServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.PropertyResolverUtils;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.core.CoreModuleProperties;

import java.io.ByteArrayOutputStream; // 用于捕获命令输出
import java.io.IOException;
import java.nio.charset.StandardCharsets; // 用于流转换
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Collection;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SimpleSshClient {

    //客户端私钥文件的路径 (例如 ~/.ssh/id_rsa 或 ~/.ssh/id_ed25519)
    // !!! 确保这个文件存在并且是你的私钥 !!!
    private static final Path CLIENT_PRIVATE_KEY_PATH = Paths.get(System.getProperty("user.home"), ".ssh", "id_ed25519");
    // 如果私钥有密码，可以在这里设置
    private static final String CLIENT_PRIVATE_KEY_PASSPHRASE = null; // 或者 "your_passphrase"

    // 已知主机公钥存放地址  ~/.ssh/known_hosts
    private static final Path knownHostsPath = Paths.get(System.getProperty("user.home"), ".ssh", "known_hosts");

    public static void main(String[] args) { // 移除了 throws 声明，改为在内部处理

        // DefaultKnownHostsServerKeyVerifier 的 delegate 应该是一个 ServerKeyVerifier 实例
        // AgentServerKeyVerifier(knownHostsPath, strictPermissions, autoAcceptNewKeys)
        // 这里的参数：
        //   - knownHostsPath: known_hosts 文件路径
        //   - delegate strict: false (DefaultKnownHostsServerKeyVerifier 的严格模式，检查文件权限等)
        //   - AgentServerKeyVerifier strictPermissions: false (AgentServerKeyVerifier 内部的权限检查)
        //   - AgentServerKeyVerifier autoAcceptNewKeys: true (AgentServerKeyVerifier 自动添加新密钥)
        // 通常 DefaultKnownHostsServerKeyVerifier 的 strict 参数设为 true 会更安全，
        // 但如果 AgentServerKeyVerifier 会创建文件/目录，可能需要根据情况调整。
        // 这里我们假设 AgentServerKeyVerifier 负责 known_hosts 的创建和更新。
        // DefaultKnownHostsServerKeyVerifier 的第二个参数 strict 是指 DefaultKnownHostsServerKeyVerifier 自身的严格模式，
        // 它会检查 known_hosts 文件和父目录的权限。
        // AgentServerKeyVerifier 的构造函数参数控制其内部行为。
        // 如果 AgentServerKeyVerifier (delegate) 会处理所有情况 (包括添加新key)，
        // DefaultKnownHostsServerKeyVerifier 的 strict 模式可能不那么关键，但保持 true 更好。
        // 此处 DefaultKnownHostsServerKeyVerifier 的 strict 设为 false 是你代码中的写法。
        SshClient client = ClientBuilder.builder()
                .serverKeyVerifier(new DefaultKnownHostsServerKeyVerifier(new AgentServerKeyVerifier(knownHostsPath, false, true), false))
                .build();


        try { // 将 GeneralSecurityException 和 IOException 的处理移到 try-catch 块
            // 加载客户端的密钥对
            FilePasswordProvider passwordProvider = CLIENT_PRIVATE_KEY_PASSPHRASE == null ?
                    FilePasswordProvider.EMPTY :
                    FilePasswordProvider.of(CLIENT_PRIVATE_KEY_PASSPHRASE);

            // 使用 SecurityUtils.loadKeyPairs 更为通用
            Collection<KeyPair> keyPairs = SecurityUtils.getKeyPairResourceParser().loadKeyPairs(
                    null, // session, not available yet
                    CLIENT_PRIVATE_KEY_PATH,
                    passwordProvider);

            if (keyPairs == null || !keyPairs.iterator().hasNext()) {
                log.error("Client: Could not load key pair from: {}", CLIENT_PRIVATE_KEY_PATH);
                log.error("Client: Please ensure the private key file exists and is accessible.");
                if (CLIENT_PRIVATE_KEY_PASSPHRASE != null) {
                    log.error("Client: If the key is passphrase protected, ensure CLIENT_PRIVATE_KEY_PASSPHRASE is correct.");
                }
                return;
            }
            // 将加载的密钥对添加到客户端，以便在认证时使用
            client.setKeyIdentityProvider(KeyIdentityProvider.wrapKeyPairs(keyPairs));


            client.start();
            log.info("SSH Client started. Connecting to server...");

            ClientSession session = null;
            try {
                session = client.connect("hedeoer", "vm100", 2222) // 用户名，主机，端口
                        .verify(10, TimeUnit.SECONDS).getSession();
                log.info("Client: Connection future verified. Session ID: {}", session.getIoSession().getId());

                // (可选) 明确指定首选认证方法为公钥
                PropertyResolverUtils.updateProperty(session, CoreModuleProperties.PREFERRED_AUTHS.getName(), "publickey");
                log.info("Client: Preferred authentication methods set to 'publickey'.");

                // 触发认证过程
                session.auth().verify(15, TimeUnit.SECONDS);
                log.info("Client: Authentication successful!");

                // 执行命令并捕获输出到日志
                try (ClientChannel channel = session.createExecChannel("get_ssh_server_status");
                     ByteArrayOutputStream responseStream = new ByteArrayOutputStream(); // 用于捕获标准输出
                     ByteArrayOutputStream errorStream = new ByteArrayOutputStream()) { // 用于捕获标准错误

                    channel.setOut(responseStream);
                    channel.setErr(errorStream);

                    channel.open().verify(5, TimeUnit.SECONDS);
                    // 等待通道关闭事件，0表示无限等待直到事件发生或通道异常关闭
                    channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 0);

                    String stdout = responseStream.toString(StandardCharsets.UTF_8.name());
                    String stderr = errorStream.toString(StandardCharsets.UTF_8.name());
                    Integer exitStatus = channel.getExitStatus();

                    log.info("Client: Command executed. Exit Status: {}", (exitStatus == null ? "N/A" : exitStatus));
                    if (!stdout.isEmpty()) {
                        log.info("Client: Command STDOUT:\n{}", stdout);
                    }
                    if (!stderr.isEmpty()) {
                        log.warn("Client: Command STDERR:\n{}", stderr); // 使用 warn 级别记录错误流输出
                    }
                }

            } catch (Exception e) { // 捕获会话和命令执行过程中的所有异常
                log.error("Client: An error occurred during SSH session or command execution: {}", e.getMessage(), e);
                if (session != null && session.isOpen()) {
                    session.close(true); // 尝试立即关闭会话
                }
            } finally {
                if (session != null && session.isOpen()) { // 确保会话在正常流程结束时关闭
                    log.info("Client: Closing session...");
                    session.close(false); // false 表示优雅关闭
                }
            }
        } catch (GeneralSecurityException | IOException e) { // 捕获密钥加载和客户端启动的异常
            log.error("Client: Initialization or key loading error: {}", e.getMessage(), e);
        } finally {
            log.info("Client: Stopping client...");
            if (client.isStarted()) { // 只有在启动后才需要停止
                client.stop();
            }
            log.info("Client: Client stopped.");
        }
    }
}