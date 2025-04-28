package com.zeta.firewall.model.dto;

import com.zeta.firewall.model.entity.PortInfo;
import com.zeta.firewall.model.entity.PortRule;
import com.zeta.firewall.validation.annotation.ValidSourceAddress;
import com.zeta.firewall.validation.annotation.ValidSourceType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

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

    @ApiModelProperty(value = "agent节点唯一标识id")
    private String nodeId;

    @ApiModelProperty(value = "协议(TCP/UDP)")
    private String protocol;

    @ApiModelProperty(value = "端口号或范围")
    private String port;

    @ApiModelProperty(value = "策略(accept/drop)")
    private String strategy;

    @ApiModelProperty(value = "源地址类型，any 或者 specific")
    @ValidSourceType
    private String sourceType;

    @ApiModelProperty(value = "源地址")
    @ValidSourceAddress
    private String sourceAddress;

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

    @ApiModelProperty(value = "端口规则中已经被使用的端口列表([\"8080\", \"8082\", \"8085\"])")
    private List<String> usedPorts;

   @ApiModelProperty(value = "各个正在使用的端口使用详细信息")
    private List<PortInfo> portUsageDetails;

    /**
     * 从实体对象转换为DTO对象
     *
     * @param entity PortRule实体对象
     * @return PortRuleDTO对象
     */
    public static PortRuleDTO fromEntity(PortRule entity,List<PortInfo> portInfos) {
        if (entity == null) {
            return null;
        }

        ArrayList<String> usedPorts = new ArrayList<>();
        // portInfos不为空表示该条端口规则中有端口被正在使用
        if (!(portInfos == null || portInfos.isEmpty())) {
            portInfos.stream()
                    .map(PortInfo::getPortNumber)
                    .map(String::valueOf)
                    .forEach(usedPorts::add);
        }

        String sourceType = (entity.getSourceRule() != null
                && entity.getSourceRule().getSource() != null
                && !entity.getSourceRule().getSource().equals("0.0.0.0") ) ?  "specific" : "any";

        String sourceAddress = sourceType.equals("specific") ? entity.getSourceRule().getSource() : "";

        return PortRuleDTO.builder()
                .id(entity.getId())
                .nodeId(entity.getAgentId())
                .protocol(entity.getProtocol().toUpperCase())
                .port(entity.getPort())
                .strategy(entity.getPolicy() != null && entity.getPolicy() ? "accept" : "drop")
                .sourceType(sourceType)
                .sourceAddress(sourceAddress)
                .description(entity.getDescriptor())
                .usedStatus(entity.getUsing() != null && entity.getUsing() ? "inUsed" : "notUsed")
                .zone(entity.getZone())
                .family(entity.getFamily())
                .permanent(entity.isPermanent())
                .usedPorts(usedPorts)
                .portUsageDetails(portInfos)
                .build();
    }

    /**
     * 转换为实体对象
     *
     * @return PortRule实体对象
     */
    public com.zeta.firewall.model.entity.PortRule toEntity() {
        PortRule entity = new PortRule();
        entity.setId(this.id);

        // 处理可能为null的字符串字段
        if (this.protocol != null) {
            entity.setProtocol(this.protocol.toLowerCase());
        }
        entity.setPort(this.port);
        entity.setPolicy("accept".equalsIgnoreCase(this.strategy));

        // 设置源地址规则
        if (this.sourceType != null) {
            com.zeta.firewall.model.entity.SourceRule sourceRule = new com.zeta.firewall.model.entity.SourceRule();

            if (!"any".equals(this.sourceType)) {
                sourceRule.setSource(this.sourceAddress);
            }else {
                sourceRule.setSource("0.0.0.0");
            }
            entity.setSourceRule(sourceRule);
        }

        entity.setDescriptor(this.description);
        entity.setUsing("inUsed".equalsIgnoreCase(this.usedStatus));
        entity.setZone(this.zone);
        entity.setFamily(this.family);
        // Handle null permanent value
        entity.setPermanent(this.permanent != null ? this.permanent : false);
        entity.setAgentId(this.nodeId);

        return entity;
    }
}