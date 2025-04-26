package com.zeta.firewall.controller;

import com.zeta.firewall.model.dto.PortRuleDTO;
import com.zeta.firewall.model.entity.PortRule;
import com.zeta.firewall.service.PortRuleService;
import com.zeta.firewall.service.StreamResponseService;
import com.zeta.firewall.subscirbe.StreamProducer;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.zetaframework.base.result.ApiResult;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Api(tags = "防火墙端口规则管理")
//@PreAuth(replace = "agents:node") // 权限注解，可根据需要启用
@RestController
@RequestMapping("/api/agents/firewall")
public class PortRuleController {

    private final PortRuleService portRuleService;
    private final StreamProducer streamProducer;
    private final StreamResponseService streamResponseService;

    /**
     * 获取指定节点的端口规则列表
     *
     * @param nodeId 节点ID
     * @return 端口规则列表
     */
    @ApiOperation(value = "获取节点端口规则", notes = "获取指定节点的所有防火墙端口规则")
    @GetMapping("/port-rules/{nodeId}")
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

    /**
     * 添加端口规则
     *
     * @param portRuleDTO 端口规则DTO
     * @return 添加结果
     */
    @ApiOperation(value = "添加端口规则", notes = "为指定节点添加防火墙端口规则")
    @PostMapping("/port-rule")
    public ApiResult<?> addPortRule(
            @ApiParam(value = "节点ID", required = true, example = "node1")
            @RequestBody PortRuleDTO portRuleDTO) {


        // 1. 转换DTO为实体
        PortRule portRule = portRuleDTO.toEntity();
        String agentId = portRule.getAgentId();
        try {
            log.info("Adding port rule for node {}: {}", agentId, portRuleDTO);

            // 2. 发送Redis命令并持久化mysql并获取响应
            if (!portRuleService.addPortRule(portRule)) {
                return ApiResult.fail("添加端口规则失败");
            }

            // 响应
            return ApiResult.success("端口规则添加成功", Map.of("id", portRule.getId()));

        } catch (Exception e) {
            log.error("Failed to add port rule for node {}", agentId, e);
            return ApiResult.fail("添加端口规则失败: " + e.getMessage());
        }
    }
}