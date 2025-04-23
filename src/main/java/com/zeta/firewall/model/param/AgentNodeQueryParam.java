package com.zeta.firewall.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Agent节点查询参数
 *
 * @author AutoGenerator
 */
@Data
@ApiModel(description = "Agent节点查询参数")
public class AgentNodeQueryParam {

    /** 节点ID */
    @ApiModelProperty(value = "节点ID")
    private String agentId;

    /** 主机名 */
    @ApiModelProperty(value = "主机名")
    private String hostName;

    /** 节点IP */
    @ApiModelProperty(value = "节点IP")
    private String ip;

    /** 节点状态 */
    @ApiModelProperty(value = "节点状态")
    private String nodeStatus;

    /** 操作系统 */
    @ApiModelProperty(value = "操作系统")
    private String osName;

    /** 客户端版本 */
    @ApiModelProperty(value = "客户端版本")
    private String clientVersion;
}
