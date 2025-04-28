package com.zeta.firewall.service;

import com.zeta.firewall.model.entity.PortInfo;
import com.zeta.firewall.model.entity.PortRule;

import java.util.List;
import java.util.Map;

public interface PortInfoService {

    /**
     * 支持通过 批量端口规则 来查询 对应的每个端口使用详细信息
     *
     * 首先查询mysql数据库，如果数据库中没有，则通过redis stream 发起获取节点的端口使用信息
     * 首次查询后的结果需要持久化到mysql，之后从mysql查询即可；
     *
     * 关于mysql中portInfo信息的更新时机：
     * 1. portInfo有固定的更新频率 见updatePortInfoPeriod（）实现
     * 1. 有新增端口规则时，直接发起redis 命令获取最新该条端口规则对应的端口使用信息
     * 2. 有更新端口规则时，直接发起redis 命令获取最新该条端口规则对应的端口使用信息
     *
     * 返回值：Map<String,List<PortInfo>> key:端口规则的id ; value:端口信息列表
     *
     *  当端口规则中 端口为 单个端口，比如 34343 ，返回值列表中元素个数为1或者0，0表示端口没有被使用
     *  当端口规则中 端口为 端口范围，比如 34343-34345，或者 34343, 23234,6432; 只返回端口正在被使用的端口信息
     *
     * @param portRules 端口规则列表
     * @param nodeId 节点机器的唯一标识
     * @return 端口规则列表中的端口信息详细的信息（PortInfo对象）列表
     */
    Map<String,List<PortInfo>> getPortInfosByPortRules(List<PortRule> portRules,String nodeId);

    /**
     * 周期性的更新端口使用信息
     * @param portInfos 需要更新的端口信息列表
     * @param  period 更新频率
     * @return 全部更新成功返回 true； 其他情况返回 false
     */
    Boolean updatePortInfoPeriod(List<PortInfo> portInfos,Integer period);

    /**
     * 批量保存或更新端口信息
     * @param portInfos 端口信息列表
     * @return 保存或更新结果 true:成功表示全部成功； 其他情况返回 false
     */
    Boolean insertOrUpdateBatchPortInfos(List<PortInfo> portInfos);

}
