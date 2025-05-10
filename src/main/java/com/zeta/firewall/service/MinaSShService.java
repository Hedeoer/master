package com.zeta.firewall.service;

import com.zeta.firewall.model.entity.SshServerInfo;

/**
 * 和 apache mina sshd相关的服务
 */
public interface MinaSShService {
    /**
     * 检查SSH服务器上agent的运行状态。
     * @param serverUserName        ssh服务端用户名
     * @param sshServerIpOrHostName ssh服务端ip或者主机名
     * @param sshServerPort         ssh服务端服务监听的端口号
     * @param ifNeedKeepLife        是否在无流量时保持会话有效
     * @return SshServerInfo
     */
    SshServerInfo checkAgentRunningStatus(String serverUserName, String sshServerIpOrHostName, Integer sshServerPort, boolean ifNeedKeepLife);
}
