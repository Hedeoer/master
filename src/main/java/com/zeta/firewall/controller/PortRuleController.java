package com.zeta.firewall.controller;

import com.zeta.firewall.model.dto.DeletePortRulesRequest;
import com.zeta.firewall.model.dto.PortRuleDTO;
import com.zeta.firewall.model.entity.FirewallPortRuleInfo;
import com.zeta.firewall.model.entity.PortInfo;
import com.zeta.firewall.model.entity.PortRule;
import com.zeta.firewall.service.FirewallPortRuleInfoService;
import com.zeta.firewall.service.PortInfoService;
import com.zeta.firewall.service.PortRuleService;
import com.zeta.firewall.util.IpUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.zetaframework.base.result.ApiResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Api(tags = "防火墙端口规则管理")
//@PreAuth(replace = "agents:node") // 权限注解，可根据需要启用
@RestController
@RequestMapping("/api/agents/firewall")
public class PortRuleController {

    private final PortRuleService portRuleService;
    private final PortInfoService portInfoService;
    private final FirewallPortRuleInfoService firewallPortRuleInfoService;

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
        List<FirewallPortRuleInfo> firewallPortRuleInfos = firewallPortRuleInfoService.queryAll();
        List<PortInfo> portInfos = portInfoService.queryAllPortInfosDB();

        // 根据规则列表获取端口使用信息(usedStatus，usedPorts，portUsageDetails)
        // 思路：遍历rules，从firewallPortRuleInfos(ruleId 和 infoId关系映射表)查询是否有对应的ruleId，有则从portInfos中查询对应的portInfo，最后将portRule 转化为 PortRuleDTO

        // 建立 ruleId -> List<infoId>
        Map<Long, List<Long>> ruleIdToInfoIdList = firewallPortRuleInfos.stream()
                .collect(Collectors.groupingBy(
                        FirewallPortRuleInfo::getRuleId,
                        Collectors.mapping(FirewallPortRuleInfo::getInfoId, Collectors.toList())
                ));

        // 建立 infoId -> PortInfo
        Map<Long, PortInfo> infoIdToPortInfo = portInfos.stream()
                .collect(Collectors.toMap(PortInfo::getId, p -> p));

        // 转换为DTO列表
        List<PortRuleDTO> dtoList = rules.stream()
                .map(portRule -> {
                    List<Long> infoIds = ruleIdToInfoIdList.getOrDefault(portRule.getId(), Collections.emptyList());
                    List<PortInfo> relatedPortInfos = infoIds.stream()
                            .map(infoIdToPortInfo::get)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    return PortRuleDTO.fromEntity(portRule, relatedPortInfos);
                })
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

        // 0.前置检查
        String family = portRuleDTO.getFamily();
        String sourceAddress = portRuleDTO.getSourceAddress();
        if (!IpUtils.isMappingIpType(sourceAddress, family)) {
            return ApiResult.fail("添加端口规则失败: " + "源ip: "+sourceAddress+", 和 指定的ip协议族:"+family+" 类型不匹配");
        }

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

    /**
     * 删除端口规则
     *
     * @param request 删除请求对象
     * @return 删除结果
     */
    @ApiOperation(value = "删除端口规则", notes = "删除指定节点的防火墙端口规则")
    @PostMapping("/port-rules/delete")
    public ApiResult<Boolean> deletePortRules(@RequestBody DeletePortRulesRequest request) {
        try {
            log.info("Deleting port rules for node {}: rule IDs {}", request.getNodeId(), request.getRuleIds());

            // 调用服务层删除规则
            Boolean result = portRuleService.deletePortRules(request.getNodeId(), request.getRuleIds());

            if (result) {
                return ApiResult.success("删除端口规则成功", true);
            } else {
                return ApiResult.fail("删除端口规则失败");
            }

        } catch (Exception e) {
            log.error("Failed to delete port rules for node {}", request.getNodeId(), e);
            return ApiResult.fail("删除端口规则失败: " + e.getMessage());
        }
    }

    /**
     * 更新端口规则
     *
     * @param ruleId      规则ID
     * @param portRuleDTO 端口规则DTO
     * @return 更新结果
     */
    @ApiOperation(value = "更新端口规则", notes = "更新指定节点的防火墙端口规则")
    @PutMapping("/port-rule/{ruleId}")
    public ApiResult<Boolean> updatePortRuleApi(
            @ApiParam(value = "规则ID", required = true, example = "1")
            @PathVariable Long ruleId,
            @ApiParam(value = "端口规则信息", required = true)
            @RequestBody PortRuleDTO portRuleDTO) {
        try {
            log.info("Updating port rule with ID {}: {}", ruleId, portRuleDTO);

            // 1. 转换DTO为实体
            PortRule portRule = portRuleDTO.toEntity();

            // 2. 调用服务层更新规则
            // todo 更新时源地址未能更新
            Boolean result = portRuleService.updatePortRule(ruleId, portRule);

            if (result) {
                return ApiResult.success("更新成功", true);
            } else {
                return ApiResult.fail("更新端口规则失败");
            }

        } catch (Exception e) {
            log.error("Failed to update port rule with ID {}", ruleId, e);
            return ApiResult.fail("更新端口规则失败: " + e.getMessage());
        }
    }
}