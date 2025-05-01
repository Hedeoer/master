package com.zeta.firewall.service;

import com.zeta.firewall.model.entity.FirewallStatusInfo;

import java.util.List;

public interface FirewallStatusInfoService {

    /**
     * 获取防火墙状态信息
     * @param agentId 节点机器id
     * @return 防火墙状态信息
     */
    FirewallStatusInfo getFirewallStatusInfo(String agentId);

    /**
     * 批量保存或更新防火墙状态信息
     * @param firewallStatusInfoList 防火墙状态信息列表
     * @return 保存或更新结果 true:成功表示全部成功； 其他情况返回 false
     */
    Boolean insertOrUpdateBatchFireWallStatusInfo(List<FirewallStatusInfo> firewallStatusInfoList);

    /**
     * 执行防火墙操作（启动、停止、重启）
     * @param nodeId 节点ID
     * @param operation 操作类型（start, stop, restart）
     * @return 操作结果 true:成功 false:失败
     */
    Boolean operateFirewall(String nodeId, String operation);

    /**
     * 执行ping禁用或者启用操作
     * @param nodeId 节点id
     * @param pingStatusOp Disable 或者 Enable
     * @return 操作结果 true:成功 false:失败
     */
    Boolean operatePing(String nodeId, String pingStatusOp);
}
