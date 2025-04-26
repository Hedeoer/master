package com.zeta.firewall.model.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 删除端口规则请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "删除端口规则请求")
public class DeletePortRulesRequest {

    @ApiModelProperty(value = "节点ID", required = true, example = "node1")
    private String nodeId;

    @ApiModelProperty(value = "要删除的规则ID列表", required = true)
    private List<Long> ruleIds;
}
