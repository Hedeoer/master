package com.zeta.firewall.model.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 端口规则数据传输对象
 * 用于前端展示的端口规则数据格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "端口规则DTO")
public class PortRuleDTO {
    
    @ApiModelProperty(value = "规则ID")
    private Long id;

    @ApiModelProperty(value = "协议(TCP/UDP)")
    private String protocol;

    @ApiModelProperty(value = "端口号或范围")
    private String port;

    @ApiModelProperty(value = "策略(accept/reject)")
    private String strategy;

    @ApiModelProperty(value = "源地址")
    private String address;

    @ApiModelProperty(value = "描述信息")
    private String description;

    @ApiModelProperty(value = "使用状态(inUsed/notUsed)")
    private String usedStatus;

    @ApiModelProperty(value = "作用域")
    private String zone;

    @ApiModelProperty(value = "IP类型(ipv4/ipv6)")
    private String family;

    @ApiModelProperty(value = "是否永久生效")
    private Boolean permanent;

    /**
     * 从实体对象转换为DTO对象
     *
     * @param entity PortRule实体对象
     * @return PortRuleDTO对象
     */
    public static PortRuleDTO fromEntity(com.zeta.firewall.model.entity.PortRule entity) {
        if (entity == null) {
            return null;
        }

        return PortRuleDTO.builder()
                .id(entity.getId())
                .protocol(entity.getProtocol().toUpperCase())
                .port(entity.getPort())
                .strategy(entity.getPolicy() != null && entity.getPolicy() ? "accept" : "reject")
                .address(entity.getSourceRule() != null ? entity.getSourceRule().getSource() : "Anywhere")
                .description(entity.getDescriptor())
                .usedStatus(entity.getUsing() != null && entity.getUsing() ? "inUsed" : "notUsed")
                .zone(entity.getZone())
                .family(entity.getFamily())
                .permanent(entity.isPermanent())
                .build();
    }

    /**
     * 转换为实体对象
     *
     * @return PortRule实体对象
     */
    public com.zeta.firewall.model.entity.PortRule toEntity() {
        com.zeta.firewall.model.entity.PortRule entity = new com.zeta.firewall.model.entity.PortRule();
        entity.setId(this.id);
        entity.setProtocol(this.protocol.toLowerCase());
        entity.setPort(this.port);
        entity.setPolicy("accept".equalsIgnoreCase(this.strategy));
        
        // 设置源地址规则
        if (!"Anywhere".equals(this.address)) {
            com.zeta.firewall.model.entity.SourceRule sourceRule = new com.zeta.firewall.model.entity.SourceRule();
            sourceRule.setSource(this.address);
            entity.setSourceRule(sourceRule);
        }
        
        entity.setDescriptor(this.description);
        entity.setUsing("inUsed".equalsIgnoreCase(this.usedStatus));
        entity.setZone(this.zone);
        entity.setFamily(this.family);
        entity.setPermanent(this.permanent);
        
        return entity;
    }
}