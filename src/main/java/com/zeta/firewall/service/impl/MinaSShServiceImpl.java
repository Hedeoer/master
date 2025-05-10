package com.zeta.firewall.service.impl;

import com.zeta.firewall.model.entity.SshServerInfo;
import com.zeta.firewall.service.MinaSShService;
import com.zeta.ssh.SimpleSshClient;
import org.zetaframework.core.utils.JSONUtil;

public class MinaSShServiceImpl implements MinaSShService {

    @Override
    public SshServerInfo checkAgentRunningStatus(String serverUserName, String sshServerIpOrHostName, Integer sshServerPort, boolean ifNeedKeepLife) {
        String s = SimpleSshClient.connectAndExecuteCommand(serverUserName, sshServerIpOrHostName, sshServerPort, ifNeedKeepLife, "get_ssh_server_status");
        return JSONUtil.parseObject(s, SshServerInfo.class);
    }
}
