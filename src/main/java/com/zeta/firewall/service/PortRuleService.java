package com.zeta.firewall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zeta.firewall.model.entity.PortRule;

import java.util.List;

public interface PortRuleService extends IService<PortRule> {
    /**
     * 获取指定节点的端口规则列表
     *
     * @param nodeId 节点ID
     * @return 端口规则列表
     */
    List<PortRule> getPortRulesByNodeId(String nodeId);

}
