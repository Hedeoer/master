package com.zeta.firewall.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 端口规则 - 允许特定端口的流量
 */
@Getter
@Setter
@ToString(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
// 有一个使用了自定义 TypeHandler 的字段 private SourceRule sourceRule
//如果 autoResultMap = false（默认值），MyBatis-Plus 不会自动构建 ResultMap，可能导致类型转换失败
@TableName(value = "firewall_port_rule", autoResultMap = true)
public class PortRule extends AbstractFirewallRule {

    // 使用数据表的自增id
    @TableId(value = "id", type = IdType.AUTO)
    protected Long id;

    private String family;    // ip type (ipv4 ,ipv6)
    private String port;      // 端口号或范围 (如 "80" 或 "1024-2048")
    private String protocol;  // 协议 (tcp 或 udp 或者 tcp/udp)
    @TableField("`using`")
    private Boolean using;    // 端口使用状态 （已使用，未使用）
    private Boolean policy;   // 端口策略（允许，拒绝）
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private SourceRule sourceRule; // 源IP地址或CIDR
    private String descriptor; //端口描述信息

    /**
     * 对象比较只包含 family, port、protocol sourceRule，policy 和父类属性（agentId，permanent，type，zone）
     * @param o
     * @return
     */
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        PortRule portRule = (PortRule) o;
        return Objects.equals(family, portRule.family)
                &&  Objects.equals(port, portRule.port)
                && Objects.equals(protocol, portRule.protocol)
                && Objects.equals(sourceRule,portRule.sourceRule)
                && Objects.equals(policy,portRule.policy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), family,port, protocol,sourceRule,policy);
    }

    @Override
    public Map<String, Object> toDBusParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("zone", zone);
        params.put("port", port);
        params.put("protocol", protocol);
        params.put("agentId", agentId);
        return params;
    }
}