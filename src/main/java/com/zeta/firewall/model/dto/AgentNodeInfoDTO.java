package com.zeta.firewall.model.dto;

import com.zeta.firewall.model.entity.AgentNodeInfo;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Agent节点信息 DTO
 *
 * @author AutoGenerator
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "Agent节点信息")
public class AgentNodeInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 节点ID */
    @ApiModelProperty(value = "节点ID")
    private String nodeId;

    /** 节点名称 */
    @ApiModelProperty(value = "节点名称")
    private String nodeName;

    /** 节点IP */
    @ApiModelProperty(value = "节点IP")
    private String nodeIp;

    /** 节点状态 */
    @ApiModelProperty(value = "节点状态")
    private String nodeStatus;

    /** CPU使用率 */
    @ApiModelProperty(value = "CPU使用率")
    private Double cpuUsage;

    /** 内存使用率 */
    @ApiModelProperty(value = "内存使用率")
    private Double memoryUsage;

    /** 磁盘使用率 */
    @ApiModelProperty(value = "磁盘使用率")
    private Double diskUsage;

    /** 客户端版本 */
    @ApiModelProperty(value = "客户端版本")
    private String clientVersion;

    /** 最后心跳时间 */
    @ApiModelProperty(value = "最后心跳时间")
    private String lastHeartbeat;

    /**
     * 将 AgentNodeInfo 实体转换为 DTO
     *
     * @param entity AgentNodeInfo实体
     * @return AgentNodeInfoDTO
     */
    public static AgentNodeInfoDTO fromEntity(AgentNodeInfo entity) {
        if (entity == null) {
            return null;
        }

        return AgentNodeInfoDTO.builder()
                .nodeId(entity.getAgentId())
                .nodeName(entity.getHostName())
                .nodeIp(entity.getIp())
                .nodeStatus(entity.getIsActive() != null && entity.getIsActive() ? "online" : "offline")
                .cpuUsage(parseUsageValue(entity.getCpuUsage()))
                .memoryUsage(parseUsageValue(entity.getMemoryUsage()))
                .diskUsage(parseUsageValue(entity.getDiskUsage()))
                .clientVersion(entity.getClientVersion())
                .lastHeartbeat(formatTimestamp(entity.getHeartbeatTimestamp()))
                .build();
    }

    /**
     * 解析使用率字符串为数值
     * 
     * @param usageStr 使用率字符串
     * @return 使用率数值
     */
    private static Double parseUsageValue(String usageStr) {
        if (usageStr == null || usageStr.trim().isEmpty()) {
            return 0.0;
        }
        try {
            // 移除百分号并解析为double
            String cleanStr = usageStr.replace("%", "").trim();
            return Double.parseDouble(cleanStr);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * 格式化时间戳为ISO 8601格式
     * 
     * @param timestamp 时间戳字符串
     * @return 格式化后的时间字符串
     */
    private static String formatTimestamp(String timestamp) {
        if (timestamp == null || timestamp.trim().isEmpty()) {
            return null;
        }
        try {
            // 假设输入是yyyy-MM-dd HH:mm:ss格式
            LocalDateTime dateTime = LocalDateTime.parse(timestamp, 
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            // 转换为ISO 8601格式
            return dateTime.format(DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            return timestamp; // 如果解析失败，返回原始值
        }
    }
}
