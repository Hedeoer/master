package com.zeta.firewall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zeta.firewall.model.entity.PortRule;

import java.util.List;

public interface PortRuleService extends IService<PortRule> {

    /**
     * 根据nodeId查询端口规则
     * 如果数据库中不存在，则从远程获取并保存
     * @param nodeId 节点ID
     * @return 端口规则列表
     */
    List<PortRule> getPortRulesByNodeId(String nodeId);

    /**
     * 从数据库查询端口规则
     * @param nodeId 节点ID
     * @return 端口规则列表
     */
    List<PortRule> queryPortRulesByNodeId(String nodeId);

    /**
     * 批量保存或更新端口规则
     * @param portRules 端口规则列表
     */
    void saveOrUpdatePortRules(List<PortRule> portRules);

    /**
     * 添加端口规则
     * @param portRule 端口规则对象
     * @return 添加结果 true:成功 false:失败
     */
    Boolean addPortRule(PortRule portRule);

    /**
     * 删除端口规则
     * @param nodeId 节点ID
     * @param ruleIds 规则ID列表
     * @return 删除结果 true:成功 false:失败
     */
    Boolean deletePortRules(String nodeId, List<Long> ruleIds);

    /**
     * 更新端口规则
     * @param ruleId 规则ID
     * @param portRule 更新后的端口规则对象
     * @return 更新结果 true:成功 false:失败
     */
    Boolean updatePortRule(Long ruleId, PortRule portRule);

    /**
     * 更新端口规则的Using字段为false
     * @param portRules 需要更新的端口规则对象
     * @return 全部更新成功返回true
     */
    Boolean updatePortRuleUsingToFalse(List<PortRule> portRules);

    /**
     * 更新端口规则的Using字段为true
     * @param portRules 需要更新的端口规则对象
     * @return 全部更新成功返回true
     */
    Boolean updatePortRuleUsingToTrue(List<PortRule> portRules);


    /**
     * 获取全部的端口规则
     * @return 列表
     */
    List<PortRule> queryAllPortRules();
}
