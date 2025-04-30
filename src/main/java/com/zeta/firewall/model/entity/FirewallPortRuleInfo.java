package com.zeta.firewall.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.zetaframework.base.entity.Entity;


@EqualsAndHashCode(callSuper = false)
@Data
@TableName("firewall_port_rule_info")
public class FirewallPortRuleInfo extends Entity<Long> {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ruleId; // firewall_port_rule主键ID

    private Long infoId; // firewall_port_info主键ID
}