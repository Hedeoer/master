package com.zeta.firewall.controller;

import com.zeta.firewall.model.dto.AgentNodeInfoDTO;
import com.zeta.firewall.model.dto.FirewallOperationRequest;
import com.zeta.firewall.model.dto.FirewallStatusInfoDTO;
import com.zeta.firewall.model.entity.AgentNodeInfo;
import com.zeta.firewall.model.entity.FirewallStatusInfo;
import com.zeta.firewall.service.AgentNodeInfoService;
import com.zeta.firewall.service.FirewallStatusInfoService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.zetaframework.base.result.ApiResult;
import org.zetaframework.core.log.annotation.SysLog;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Api(tags = "防火墙管理")
//@PreAuth(replace = "agents:node") // 权限注解，可根据需要启用
@RestController
@RequestMapping("/api/agents/firewall")
public class FirewallController {
    private final AgentNodeInfoService agentNodeInfoService;
    private final FirewallStatusInfoService firewallStatusInfoService;


    /**
     * 获取所有节点信息
     *
     * @return 节点信息列表
     */
    @ApiOperation(value = "获取节点列表", notes = "获取所有防火墙节点的信息")
    @GetMapping("/nodes")
    public ApiResult<List<AgentNodeInfoDTO>> fetchNodeList() {
        // 获取所有节点信息
        List<AgentNodeInfo> nodeList = agentNodeInfoService.list();

        // 转换为DTO列表
        List<AgentNodeInfoDTO> dtoList = nodeList.stream()
                .map(AgentNodeInfoDTO::fromEntity)
                .collect(Collectors.toList());

        return ApiResult.success(dtoList);
    }


    @ApiOperation(value = "获取节点防火墙状态", notes = "获取指定节点防火墙状态的信息")
    @GetMapping("/status/{nodeId}")
    public ApiResult<FirewallStatusInfoDTO> getFirewallStatusInfo(
            @ApiParam(value = "节点ID", required = true, example = "node1")
            @PathVariable String nodeId) {

        FirewallStatusInfo firewallStatusInfo = firewallStatusInfoService.getFirewallStatusInfo(nodeId);

        // 转换为DTO
        FirewallStatusInfoDTO dto = FirewallStatusInfoDTO.fromEntity(firewallStatusInfo);

        return ApiResult.success(dto);
    }

    /**
     * 操作防火墙（启动、停止、重启）
     *
     * @param request 防火墙操作请求
     * @return 操作结果
     */
    @SysLog
    @ApiOperation(value = "操作防火墙", notes = "对指定节点的防火墙进行启动、停止或重启操作")
    @PostMapping("/operation")
    public ApiResult<Boolean> operateFirewall(@RequestBody @Valid FirewallOperationRequest request) {
        log.info("Executing firewall operation: {} on node: {}", request.getOperation(), request.getNodeId());

        // 执行防火墙操作
        Boolean result = firewallStatusInfoService.operateFirewall(request.getNodeId(), request.getOperation());

        if (result) {
            return ApiResult.success("操作成功", true);
        } else {
            return ApiResult.fail("操作失败", false);
        }
    }

    /**
     * 是否禁止外部系统ping
     *
     * @param request 防火墙操作请求
     * @return 操作结果
     */
    @SysLog
    @ApiOperation(value = "系统icmp ping控制", notes = "是否禁止外部系统ping")
    @PostMapping("/operation/ping")
    public ApiResult<Boolean> operatePing(@RequestBody Map<String, String> request) {

        String nodeId = request.get("nodeId");
        // Enable 或者 Disable
        String pingStatusOp = request.get("pingStatus");
        log.info("Executing ping operation: {} on node: {}", pingStatusOp, nodeId);

        Boolean result = firewallStatusInfoService.operatePing(nodeId, pingStatusOp);

        if (result) {
            return ApiResult.success("操作成功", true);
        } else {
            return ApiResult.fail("操作失败", false);
        }
    }
}
