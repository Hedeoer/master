package com.zeta.firewall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.zeta.firewall.dao.PortRuleMapper;
import com.zeta.firewall.model.dto.RedisCommandMessage;
import com.zeta.firewall.model.entity.PortRule;
import com.zeta.firewall.service.PortRuleService;
import com.zeta.firewall.service.StreamResponseService;
import com.zeta.firewall.subscirbe.StreamProducer;
import com.zeta.firewall.util.JsonMessageConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zetaframework.core.utils.JSONUtil;

import java.util.*;

/**
 * 端口规则服务实现类
 */
@Slf4j
@Service
public class PortRuleServiceImpl extends ServiceImpl<PortRuleMapper, PortRule> implements PortRuleService {

    private final StreamResponseService streamResponseService;
    private final StreamProducer streamProducer;

    public PortRuleServiceImpl(StreamResponseService streamResponseService, StreamProducer streamProducer) {
        this.streamResponseService = streamResponseService;
        this.streamProducer = streamProducer;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)  // 添加事务注解，并指定回滚异常
    public List<PortRule> getPortRulesByNodeId(String nodeId) {
       // 1. 查询数据库
        List<PortRule> portRules = this.lambdaQuery()
                .eq(PortRule::getAgentId, nodeId)
                .list();

        if (!portRules.isEmpty()) {
            return portRules;
        }

        // 2. 通过Redis Stream查询
        // 构建消息体
        HashMap<String, String> map = new HashMap<>();
        // todo 默认使用public区域
        map.put("zoneName", "public");

        List<String> primaryKeyColumns = List.of("port", "protocol");

        RedisCommandMessage<PortRule> build = RedisCommandMessage.<PortRule>builder()
                .agentId(nodeId)
                .ts(System.currentTimeMillis() / 1000)
                .agentComponentType(RedisCommandMessage.ComponentType.FIREWALL)
                .dataOpType(RedisCommandMessage.OperationType.QUERY)
                .requestParams(map)
                .primaryKeyColumns(primaryKeyColumns)
                .data(Collections.<PortRule>emptyList())
                .old(PortRule.builder().port("2323").build())
                .build();

        portRules = sendRedisQueryCommandAndGetResponse(nodeId,build);
        // 设置agentId
        portRules.forEach(portRule -> portRule.setAgentId(nodeId));

        // 3. 保存到数据库
        if (!portRules.isEmpty()) {
            this.saveBatch(portRules);
        }

        return portRules;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortRule> queryPortRulesByNodeId(String nodeId) {
        return this.lambdaQuery()
                .eq(PortRule::getAgentId, nodeId)
                .list();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdatePortRules(List<PortRule> portRules) {
        if (!portRules.isEmpty()) {
            this.saveOrUpdateBatch(portRules);
        }
    }

    /**
     * 添加端口规则
     * @param portRule 端口规则对象
     * @return 添加的端口规则ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean addPortRule(PortRule portRule) {

        boolean res = false;

        // 保存端口规则到agent
        // 获取agent节点的添加添加的结果，成功还是失败
        // 构建消息体
        HashMap<String, String> map = new HashMap<>();
        map.put("zoneName", portRule.getZone());
        map.put("policy", String.valueOf(portRule.getPolicy()));
//        map.put("isUsing", portRule.getUsing());

        List<String> primaryKeyColumns = List.of("port", "protocol");

        ArrayList<PortRule> data = new ArrayList<>();
        data.add(portRule);

        String agentId = portRule.getAgentId();
        RedisCommandMessage<PortRule> build = RedisCommandMessage.<PortRule>builder()
                .agentId(agentId)
                .ts(System.currentTimeMillis() / 1000)
                .agentComponentType(RedisCommandMessage.ComponentType.FIREWALL)
                .dataOpType(RedisCommandMessage.OperationType.INSERT)
                .requestParams(map)
                .primaryKeyColumns(primaryKeyColumns)
                .data(data)
                .build();


        // 新增端口规则命令如何判断是否成功？
        boolean success  = sendRedisInsertCommandAndGetResponse(agentId,build);

        // 如果成功，则保存到数据库
        // 失败则返回失败信息
        if (success && this.save(portRule)) {
            res = true;
        }

        return res;
    }

    /**
     * 发送Redis命令并获取新增端口规则的响应
     */
    private boolean sendRedisInsertCommandAndGetResponse(String nodeId, RedisCommandMessage<PortRule> build) {
        String pubStreamkey = "pub:" + nodeId;
        String subStreamkey = "sub:" + nodeId;
        boolean success = false;

        try {

            Map<String, String> messageMap = JsonMessageConverter.beanToMap(build);
            RecordId recordId = streamProducer.publishMessage(pubStreamkey, messageMap);
            Map<Object, Object> value = streamResponseService.getResponseEntry(nodeId, subStreamkey, recordId);

            return isResponseSuccess(value);

        } catch (Exception e) {
            log.error("Failed to insert port rules for node: {}", nodeId, e);
            return success;
        }
    }

    /**
     * 从Redis Stream查询端口规则
     */
    private List<PortRule> sendRedisQueryCommandAndGetResponse(String nodeId, RedisCommandMessage<PortRule> commandMap) {
        String pubStreamkey = "pub:" + nodeId;
        String subStreamkey = "sub:" + nodeId;

        try {

            Map<String, String> messageMap = JsonMessageConverter.beanToMap(commandMap);
            RecordId recordId = streamProducer.publishMessage(pubStreamkey, messageMap);
            Map<Object, Object> value = streamResponseService.getResponseEntry(nodeId, subStreamkey, recordId);

            // 检查响应状态
            if (!isResponseSuccess(value)) {
                return Collections.emptyList();
            }

            // 解析响应数据
            String dataStr = String.valueOf(value.get("data"));
            if (dataStr == null || dataStr.equals("null")) {
                return Collections.emptyList();
            }

            return JSONUtil.parseObject(dataStr, new TypeReference<List<PortRule>>() {});

        } catch (Exception e) {
            log.error("Failed to get port rules for node: {}", nodeId, e);
            return Collections.emptyList();
        }
    }

    private boolean isResponseSuccess(Map<Object, Object> value) {
        return value.containsKey("status") && value.get("status").equals("200");
    }

    /**
     * 删除端口规则
     * @param nodeId 节点ID
     * @param ruleIds 规则ID列表
     * @return 删除结果 true:成功 false:失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deletePortRules(String nodeId, List<Long> ruleIds) {
        if (nodeId == null || ruleIds == null || ruleIds.isEmpty()) {
            return false;
        }

        try {
            // 1. 查询要删除的规则
            List<PortRule> portRules = this.lambdaQuery()
                    .eq(PortRule::getAgentId, nodeId)
                    .in(PortRule::getId, ruleIds)
                    .list();

            if (portRules.isEmpty()) {
                log.warn("No port rules found for deletion with nodeId: {} and ruleIds: {}", nodeId, ruleIds);
                return false;
            }

            // 2. 构建删除命令消息
            HashMap<String, String> map = new HashMap<>();
            map.put("zoneName", "public"); // 默认使用public区域

            List<String> primaryKeyColumns = List.of("port", "protocol");

            RedisCommandMessage<PortRule> build = RedisCommandMessage.<PortRule>builder()
                    .agentId(nodeId)
                    .ts(System.currentTimeMillis() / 1000)
                    .agentComponentType(RedisCommandMessage.ComponentType.FIREWALL)
                    .dataOpType(RedisCommandMessage.OperationType.DELETE)
                    .requestParams(map)
                    .primaryKeyColumns(primaryKeyColumns)
                    .data(portRules)
                    .build();

            // 3. 发送删除命令并获取响应
            boolean success = sendRedisDeleteCommandAndGetResponse(nodeId, build);

            // 4. 如果成功，则从数据库中删除
            if (success) {
                return this.removeByIds(ruleIds);
            }

            return false;
        } catch (Exception e) {
            log.error("Failed to delete port rules for node: {} with ruleIds: {}", nodeId, ruleIds, e);
            return false;
        }
    }

    /**
     * 发送Redis命令并获取删除端口规则的响应
     */
    private boolean sendRedisDeleteCommandAndGetResponse(String nodeId, RedisCommandMessage<PortRule> build) {
        String pubStreamkey = "pub:" + nodeId;
        String subStreamkey = "sub:" + nodeId;
        boolean success = false;

        try {
            Map<String, String> messageMap = JsonMessageConverter.beanToMap(build);
            RecordId recordId = streamProducer.publishMessage(pubStreamkey, messageMap);
            Map<Object, Object> value = streamResponseService.getResponseEntry(nodeId, subStreamkey, recordId);

            return isResponseSuccess(value);

        } catch (Exception e) {
            log.error("Failed to delete port rules for node: {}", nodeId, e);
            return success;
        }
    }

    /**
     * 更新端口规则
     * @param ruleId 规则ID
     * @param portRule 更新后的端口规则对象
     * @return 更新结果 true:成功 false:失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updatePortRule(Long ruleId, PortRule portRule) {
        if (ruleId == null || portRule == null) {
            return false;
        }

        try {
            // 1. 查询要更新的规则
            PortRule oldRule = this.getById(ruleId);
            if (oldRule == null) {
                log.warn("No port rule found for update with ruleId: {}", ruleId);
                return false;
            }

            // 2. 设置规则ID和节点ID
            portRule.setId(ruleId);
            String nodeId = oldRule.getAgentId();
            portRule.setAgentId(nodeId);

            // 3. 构建更新命令消息
            HashMap<String, String> map = new HashMap<>();
            map.put("zoneName", portRule.getZone());
            map.put("policy", String.valueOf(portRule.getPolicy()));

            List<String> primaryKeyColumns = List.of("port", "protocol");

            ArrayList<PortRule> data = new ArrayList<>();
            data.add(portRule);

            RedisCommandMessage<PortRule> build = RedisCommandMessage.<PortRule>builder()
                    .agentId(nodeId)
                    .ts(System.currentTimeMillis() / 1000)
                    .agentComponentType(RedisCommandMessage.ComponentType.FIREWALL)
                    .dataOpType(RedisCommandMessage.OperationType.UPDATE)
                    .requestParams(map)
                    .primaryKeyColumns(primaryKeyColumns)
                    .data(data)
                    .old(oldRule)
                    .build();

            // 4. 发送更新命令并获取响应
            boolean success = sendRedisUpdateCommandAndGetResponse(nodeId, build);

            // 5. 如果成功，则更新数据库
            if (success) {
                return this.updateById(portRule);
            }

            return false;
        } catch (Exception e) {
            log.error("Failed to update port rule with ruleId: {}", ruleId, e);
            return false;
        }
    }

    /**
     * 发送Redis命令并获取更新端口规则的响应
     */
    private boolean sendRedisUpdateCommandAndGetResponse(String nodeId, RedisCommandMessage<PortRule> build) {
        String pubStreamkey = "pub:" + nodeId;
        String subStreamkey = "sub:" + nodeId;
        boolean success = false;

        try {
            Map<String, String> messageMap = JsonMessageConverter.beanToMap(build);
            RecordId recordId = streamProducer.publishMessage(pubStreamkey, messageMap);
            Map<Object, Object> value = streamResponseService.getResponseEntry(nodeId, subStreamkey, recordId);

            return isResponseSuccess(value);

        } catch (Exception e) {
            log.error("Failed to update port rule for node: {}", nodeId, e);
            return success;
        }
    }
}
