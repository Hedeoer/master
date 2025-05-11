package com.zeta.ssh;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.PropertyResolverUtils;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.common.session.SessionHeartbeatController;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.core.CoreModuleProperties;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Collection;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SimpleSshClient {

    //客户端私钥文件的路径
    private static final Path CLIENT_PRIVATE_KEY_PATH = Paths.get(System.getProperty("user.home"), ".ssh", "id_ed25519");
    // 已知主机公钥存放地址
    private static final Path CLIENT_KNOWN_HOSTS = Paths.get(System.getProperty("user.home"), ".ssh", "known_hosts");
    private static final String CLIENT_PRIVATE_KEY_PASSPHRASE = null;


    /**
     * 检查SSH服务器上agent的运行状态。
     *
     * @param serverUserName        ssh服务端用户名
     * @param sshServerIpOrHostName ssh服务端ip或者主机名
     * @param sshServerPort         ssh服务端服务监听的端口号
     * @param ifNeedKeepLife        是否在无流量时保持会话有效
     * @param commandToExecute      需要执行的命令字符串
     * @return ssh命令执行的标准输出结果，如果发生错误则返回空字符串。
     */
    public static String connectAndExecuteCommand(String serverUserName, String sshServerIpOrHostName, Integer sshServerPort, boolean ifNeedKeepLife, String commandToExecute) {
        String sshCommandExecuteResult = "";

        // 使用 try-with-resources 管理 SshClient 和 ClientSession 的生命周期
        try (SshClient sshClient = ClientBuilder.builder()
                .serverKeyVerifier(new KnownHostsServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE,CLIENT_KNOWN_HOSTS))
                .build()
             // ClientSession 的创建和认证也应在 SshClient 启动后进行
        ) {
            // 加载密钥对
            FilePasswordProvider passwordProvider = CLIENT_PRIVATE_KEY_PASSPHRASE == null ?
                    FilePasswordProvider.EMPTY :
                    FilePasswordProvider.of(CLIENT_PRIVATE_KEY_PASSPHRASE);
            Collection<KeyPair> keyPairs = SecurityUtils.getKeyPairResourceParser().loadKeyPairs(
                    null, CLIENT_PRIVATE_KEY_PATH, passwordProvider);

            if (keyPairs == null || !keyPairs.iterator().hasNext()) {
                log.error("Client: Could not load key pair from: {}", CLIENT_PRIVATE_KEY_PATH);
                if (CLIENT_PRIVATE_KEY_PASSPHRASE != null) {
                    log.error("Client: If the key is passphrase protected, ensure CLIENT_PRIVATE_KEY_PASSPHRASE is correct.");
                }
                return sshCommandExecuteResult;
            }
            sshClient.setKeyIdentityProvider(KeyIdentityProvider.wrapKeyPairs(keyPairs));

            sshClient.start(); // 启动客户端

            SessionHeartbeatController.HeartbeatType heartbeatType = ifNeedKeepLife ? SessionHeartbeatController.HeartbeatType.IGNORE : SessionHeartbeatController.HeartbeatType.NONE;
            sshClient.setSessionHeartbeat(heartbeatType, TimeUnit.SECONDS, 60);

            log.info("SSH Client started. Connecting to server {}@{}:{}", serverUserName, sshServerIpOrHostName, sshServerPort);

            // ClientSession 应该在 SshClient 的 try-with-resources 块内部创建和使用
            try (ClientSession session = sshClient.connect(serverUserName, sshServerIpOrHostName, sshServerPort)
                    .verify(10, TimeUnit.SECONDS).getSession()) {

                log.info("Client: Connection future verified. Session ID: {}", session.getIoSession().getId());
                PropertyResolverUtils.updateProperty(session, CoreModuleProperties.PREFERRED_AUTHS.getName(), "publickey");
                log.info("Client: Preferred authentication methods set to 'publickey'.");

                session.auth().verify(15, TimeUnit.SECONDS);
                log.info("Client: Authentication successful!");

                // 执行命令
                try (ClientChannel channel = session.createExecChannel(commandToExecute);
                     ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
                     ByteArrayOutputStream errorStream = new ByteArrayOutputStream()) {

                    channel.setOut(responseStream);
                    channel.setErr(errorStream);

                    channel.open().verify(5, TimeUnit.SECONDS);
                    channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 0);

                    String stdout = responseStream.toString(StandardCharsets.UTF_8);
                    String stderr = errorStream.toString(StandardCharsets.UTF_8);
                    Integer exitStatus = channel.getExitStatus();

                    log.info("Client: Command \"{}\" executed. Exit Status: {}", commandToExecute, (exitStatus == null ? "N/A" : exitStatus));
                    sshCommandExecuteResult = stdout;

                    if (!stderr.isEmpty()) {
                        log.error("Client: Command \"{}\" STDERR:\n{}", commandToExecute, stderr);
                    }
                } // ClientChannel 会在这里自动关闭
            } // ClientSession 会在这里自动关闭
        } catch (GeneralSecurityException e) {
            log.error("Client: Security error: {}", e.getMessage(), e);
        } catch (IOException e) {
            log.error("Client: IO error during SSH operation: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Client: An unexpected error occurred: {}", e.getMessage(), e);
        }
        // SshClient 会在最外层 try-with-resources 结束时自动调用 client.close() (即 client.stop())
        log.info("SSH Client operations completed. Client will be stopped if managed by try-with-resources.");
        return sshCommandExecuteResult;
    }
}