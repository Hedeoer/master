package com.zeta.firewall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zeta.firewall.dao.AgentNodeInfoMapper;
import com.zeta.firewall.model.entity.AgentNodeInfo;
import com.zeta.firewall.service.AgentNodeInfoService;
import com.zeta.firewall.service.FirewallStatusInfoService;
import com.zeta.firewall.service.PortInfoService;
import com.zeta.firewall.service.PortRuleService;
import com.zeta.firewall.subscirbe.RedisStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent节点信息 服务实现类
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AgentNodeInfoServiceImpl extends ServiceImpl<AgentNodeInfoMapper, AgentNodeInfo> implements AgentNodeInfoService {

    private final PortRuleService portRuleService;
    private final PortInfoService portInfoService;
    private final FirewallStatusInfoService fireWallStatusInfoService;
    private final RedisStreamService redisStreamService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean batchDeleteNodes(List<String> nodeIds) {

        // 执行批量删除操作
        this.removeByIds(nodeIds);
        // 删除节点相关的端口规则
        portRuleService.deletePortRulesByNodeIds(nodeIds);
        // 删除节点相关的端口信息
        portInfoService.deletePortInfosByNodeIds(nodeIds);
        // 删除节点相关的防火墙状态信息
        fireWallStatusInfoService.deleteFirewallStatusInfoByNodeIds(nodeIds);

        // 删除节点相关的Redis Stream数据,如果不清理redis相关数据，会导致redis存储没有必要的数据，并且如果agent节点删除后，redis中还存在相关数据，会导致agent节点无法重新注册
        List<String> allStreamKeys = redisStreamService.getAllStreamKeys();
        allStreamKeys.stream()
                .filter(key -> nodeIds.stream().anyMatch(key::contains))
                .forEach(redisStreamService::deleteStreamAndAssociatedGroups);

        // 删除节点相关的Redis Hash数据（firewall:heartbeats） （firewall:portInfo）
        HashOperations<String, String, String> hashOps = stringRedisTemplate.<String, String>opsForHash();
        Long deleteCount = hashOps.delete("firewall:heartbeats", nodeIds.toArray());
        Long deleteCount2 = hashOps.delete("firewall:portInfo", nodeIds.toArray());

        // 检查redis中heartbeats hash表中的数据是否与mysql中agent_node_info表中的数据一致
        long totalAgentsFromMySQLSize = this.count();
        Map<String, String> heartBeatAgents = hashOps.entries("firewall:heartbeats");
        if (heartBeatAgents.size() != totalAgentsFromMySQLSize) {

            Set<String> agentIdsFromRedis = heartBeatAgents.keySet();
            // 获取mysql的全部数据
            Set<String> agentIdFromMySQL = this.list()
                    .stream()
                    .map(AgentNodeInfo::getAgentId)
                    .collect(Collectors.toSet());
            // 计算差集：找出那些在Redis中存在但在MySQL中不存在的节点ID
            agentIdsFromRedis.removeAll(agentIdFromMySQL);

            if (!agentIdsFromRedis.isEmpty()) {
                Boolean finalResult;
                List<String> currentAllStreamKeys = redisStreamService.getAllStreamKeys();
                currentAllStreamKeys.stream()
                        .filter(key -> agentIdsFromRedis.stream().anyMatch(key::contains))
                        .forEach(redisStreamService::deleteStreamAndAssociatedGroups);
                hashOps.delete("firewall:heartbeats", agentIdsFromRedis.toArray());
                hashOps.delete("firewall:portInfo", agentIdsFromRedis.toArray());
                ArrayList<String> arr = new ArrayList<>(agentIdsFromRedis);
                // 删除节点相关的端口规则
                portRuleService.deletePortRulesByNodeIds(arr);
                // 删除节点相关的端口信息
                portInfoService.deletePortInfosByNodeIds(arr);
                // 删除节点相关的防火墙状态信息
                finalResult = fireWallStatusInfoService.deleteFirewallStatusInfoByNodeIds(arr);
                if (Boolean.FALSE.equals(finalResult)) {
                    throw new RuntimeException("在对齐mysql数据和redis数据时，删除redis 哈希表【firewall:heartbeats】数据失败: " + agentIdsFromRedis);
                }
            }

        }

        if (deleteCount != nodeIds.size() || deleteCount2 != nodeIds.size()) {
            throw new RuntimeException("Failed to delete all nodes : " + nodeIds);
        }

        return Boolean.TRUE;
    }

}
