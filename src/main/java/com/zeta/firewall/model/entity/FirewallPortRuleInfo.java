package com.zeta.firewall.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.zetaframework.base.entity.Entity;


@EqualsAndHashCode(callSuper = false)
@Data
@Builder
@TableName("firewall_port_rule_info")
public class FirewallPortRuleInfo extends Entity<Long> {

    private Long ruleId; // firewall_port_rule主键ID

    private Long infoId; // firewall_port_info主键ID
}