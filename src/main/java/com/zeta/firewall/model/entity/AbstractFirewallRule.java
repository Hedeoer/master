package com.zeta.firewall.model.entity;

import com.zeta.firewall.model.enums.RuleType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.zetaframework.base.entity.Entity;

/**
 * 抽象基类，实现共享属性和方法
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class AbstractFirewallRule extends Entity<Long> implements FirewallRule {
    protected String zone;        // 区域名称
    protected RuleType type;      // 规则类型
    protected boolean permanent;  // 是否永久规则
    protected String agentId;    // 所属节点ID
}
