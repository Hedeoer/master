package com.zeta.firewall.model.dto;

import com.zeta.firewall.model.enums.FirewallOperationType;
import com.zeta.firewall.validation.annotation.ValidFirewallOperation;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 防火墙操作请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "防火墙操作请求")
public class FirewallOperationRequest {

    @NotBlank(message = "节点ID不能为空")
    @ApiModelProperty(value = "节点ID", required = true, example = "node123")
    private String nodeId;

    @NotNull(message = "操作类型不能为空")
    @ValidFirewallOperation
    @ApiModelProperty(value = "操作类型", required = true, example = "start", notes = "可选值: start, stop, restart")
    private String operation;

    /**
     * 获取操作类型枚举
     * @return 操作类型枚举
     */
    public FirewallOperationType getOperationType() {
        return FirewallOperationType.fromValue(operation);
    }
}
