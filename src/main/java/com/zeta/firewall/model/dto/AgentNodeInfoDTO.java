package com.zeta.firewall.model.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

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
}
