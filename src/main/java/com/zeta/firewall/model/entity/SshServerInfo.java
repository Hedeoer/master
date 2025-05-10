package com.zeta.firewall.model.entity;

import java.util.Set;
/**
 * SSH服务器信息类，用于封装SSH服务器的基本状态信息。
 */
public class SshServerInfo {
    private String serverVersion;
    private String host;
    private Integer serverPort;
    private Integer currentActiveSessions;
    private Set<String> boundAddresses;

    // Jackson 需要一个无参构造函数进行反序列化，尽管这里主要用于序列化
    public SshServerInfo() {
    }

    public SshServerInfo(String serverVersion, String host, Integer serverPort,
                         Integer currentActiveSessions, Set<String> boundAddresses) {
        this.serverVersion = serverVersion;
        this.host = host;
        this.serverPort = serverPort;
        this.currentActiveSessions = currentActiveSessions;
        this.boundAddresses = boundAddresses;
    }

    // --- Getters and Setters for Jackson serialization/deserialization ---
    public String getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getServerPort() {
        return serverPort;
    }

    public void setServerPort(Integer serverPort) {
        this.serverPort = serverPort;
    }

    public Integer getCurrentActiveSessions() {
        return currentActiveSessions;
    }

    public void setCurrentActiveSessions(Integer currentActiveSessions) {
        this.currentActiveSessions = currentActiveSessions;
    }

    public Set<String> getBoundAddresses() {
        return boundAddresses;
    }

    public void setBoundAddresses(Set<String> boundAddresses) {
        this.boundAddresses = boundAddresses;
    }

    @Override
    public String toString() { // toString 主要用于调试，实际输出是JSON
        return "SshServerInfo{" +
                "serverVersion='" + serverVersion + '\'' +
                ", host='" + host + '\'' +
                ", serverPort=" + serverPort +
                ", currentActiveSessions=" + currentActiveSessions +
                ", boundAddresses=" + boundAddresses +
                '}';
    }
}