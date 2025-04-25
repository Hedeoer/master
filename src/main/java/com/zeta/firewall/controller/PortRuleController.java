package com.zeta.firewall.controller;

import com.zeta.firewall.model.dto.PortRuleDTO;
import com.zeta.firewall.model.entity.PortRule;
import com.zeta.firewall.service.PortRuleService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zetaframework.base.result.ApiResult;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Api(tags = "防火墙端口规则管理")
//@PreAuth(replace = "agents:node") // 权限注解，可根据需要启用
@RestController
@RequestMapping("/api/agents/firewall/port-rules")
public class PortRuleController {

    private final PortRuleService portRuleService;

    /**
     * 获取指定节点的端口规则列表
     *
     * @param nodeId 节点ID
     * @return 端口规则列表
     */
    @ApiOperation(value = "获取节点端口规则", notes = "获取指定节点的所有防火墙端口规则")
    @GetMapping("/{nodeId}")
    public ApiResult<List<PortRuleDTO>> fetchPortRulesByNodeId(
            @ApiParam(value = "节点ID", required = true, example = "node1")
            @PathVariable String nodeId) {

        // 调用服务层获取规则列表
        List<PortRule> rules = portRuleService.getPortRulesByNodeId(nodeId);

        // 转换为DTO列表
        List<PortRuleDTO> dtoList = rules.stream()
                .map(PortRuleDTO::fromEntity)
                .collect(Collectors.toList());

        // 返回成功结果
        return ApiResult.success("获取端口规则成功", dtoList);
    }

}
