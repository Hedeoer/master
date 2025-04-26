package com.zeta.firewall.model.entity;

import java.util.Map;

/**
 * 所有防火墙规则的基础接口
 */
public interface FirewallRule {
    String getZone();
    RuleType getType();
    boolean isPermanent();
    String getAgentId();  // 获取所属节点ID的方法
    Map<String, Object> toDBusParams();
}