package com.zeta.firewall.controller;

import com.zeta.firewall.model.dto.AgentNodeInfoDTO;
import com.zeta.firewall.model.entity.AgentNodeInfo;
import com.zeta.firewall.service.AgentNodeInfoService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zetaframework.base.result.ApiResult;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Api(tags = "防火墙管理")
//@PreAuth(replace = "agents:node") // 权限注解，可根据需要启用
@RestController
@RequestMapping("/api/agents/firewall")
public class FirewallController {
    private final AgentNodeInfoService agentNodeInfoService;


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


}
