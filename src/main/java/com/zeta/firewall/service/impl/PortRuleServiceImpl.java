package com.zeta.firewall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.zeta.firewall.dao.PortRuleMapper;
import com.zeta.firewall.event.PortRuleUpdateEvent;
import com.zeta.firewall.model.dto.RedisCommandMessage;
import com.zeta.firewall.model.entity.FirewallPortRuleInfo;
import com.zeta.firewall.model.entity.PortInfo;
import com.zeta.firewall.model.entity.PortRule;
import com.zeta.firewall.service.FirewallPortRuleInfoService;
import com.zeta.firewall.service.PortInfoService;
import com.zeta.firewall.service.PortRuleService;
import com.zeta.firewall.service.StreamResponseService;
import com.zeta.firewall.subscirbe.StreamProducer;
import com.zeta.firewall.util.JsonMessageConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final PortInfoService portInfoService;
    private final ApplicationEventPublisher eventPublisher;
    private final FirewallPortRuleInfoService firewallPortRuleInfoService;

    private String pubStreamKeySuffix;
    private String subStreamKeySuffix;

    public PortRuleServiceImpl(StreamResponseService streamResponseService, StreamProducer streamProducer, PortInfoService portInfoService, ApplicationEventPublisher eventPublisher, FirewallPortRuleInfoService firewallPortRuleInfoService) {
        this.streamResponseService = streamResponseService;
        this.streamProducer = streamProducer;
        this.portInfoService = portInfoService;
        this.eventPublisher = eventPublisher;
        this.firewallPortRuleInfoService = firewallPortRuleInfoService;
        this.pubStreamKeySuffix = "portRule";
        this.subStreamKeySuffix = "portRule";
    }

    /**
     * 发布端口规则更新事件
     * 统一管理事件发布，避免重复代码
     */
    private void publishPortRuleUpdateEvent() {
        try {
            eventPublisher.publishEvent(new PortRuleUpdateEvent(this));
            log.debug("端口规则更新事件已发布");
        } catch (Exception e) {
            log.error("发布端口规则更新事件失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)  // 添加事务注解，并指定回滚异常
    public List<PortRule> getPortRulesByNodeId(String nodeId) {

        List<PortRule> portRules = new ArrayList<PortRule>();

        // 先从从数据库获取
        List<PortRule> portRulesFromDB = this.queryPortRulesByNodeId(nodeId);
        if (!portRulesFromDB.isEmpty()) {
            return portRulesFromDB;
        }


        // 通过Redis Stream查询
        // 构建消息体
        HashMap<String, String> map = new HashMap<>();
        // todo 默认使用public区域
        map.put("zoneName", "public");

        List<String> primaryKeyColumns = List.of("family", "port", "protocol", "sourceRule", "policy", "agentId", "permanent", "type", "zone");

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
            // 更新端口规则
            // 手动触发一次端口使用情况更新
            if (!(this.saveOrUpdatePortRules(portRules))) {
                log.error("无法通过节点id: {}, 获取对应的端口规则",nodeId);
            }else{
                // 1.发布事件 因为发布的事件中涉及数据库的更新操作，需要开启新的事务
                publishPortRuleUpdateEvent();
//                log.info("getPortRulesByNodeId 当前事务: {}", TransactionSynchronizationManager.getCurrentTransactionName());

                // 2. 查询最新数据
                List<PortRule> lastedPortRules = this.queryAllPortRules();
                List<FirewallPortRuleInfo> firewallPortRuleInfos = firewallPortRuleInfoService.queryAll();

                // 3. 更新使用状态
                for (PortRule lastedPortRule : lastedPortRules) {
                    lastedPortRule.setUsing(false);
                    for (FirewallPortRuleInfo info : firewallPortRuleInfos) {
                        if (info.getRuleId().equals(lastedPortRule.getId())) {
                            lastedPortRule.setUsing(true);
                            break;
                        }
                    }
                }

                if (this.updateBatchById(lastedPortRules)) {
                    portRules = lastedPortRules;
                }

            }
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
    public Boolean saveOrUpdatePortRules(List<PortRule> portRules) {
        if (portRules.isEmpty()) {
            return false;
        }

        try {
            // 1. 批量查询已存在的记录
            List<PortRule> existingRules = new ArrayList<>();
            for (PortRule rule : portRules) {
                PortRule existing = this.lambdaQuery()
                        .eq(PortRule::getAgentId, rule.getAgentId())
                        .eq(PortRule::isPermanent, rule.isPermanent())
                        .eq(PortRule::getType, rule.getType())
                        .eq(PortRule::getZone, rule.getZone())
                        .eq(PortRule::getFamily, rule.getFamily())
                        .eq(PortRule::getPort, rule.getPort())
                        .eq(PortRule::getProtocol, rule.getProtocol())
                        // 使用 apply 方法添加自定义 SQL 条件
                        .apply("JSON_EXTRACT(source_rule, '$.source') = {0}", rule.getSourceRule().getSource())
                        .eq(PortRule::getPolicy, rule.getPolicy())
                        .one();

                if (existing != null) {
                    // 如果存在记录，设置ID以触发更新
                    rule.setId(existing.getId());
                    existingRules.add(existing);
                }
            }

            // 2. 执行批量保存或更新
            boolean success = this.saveOrUpdateBatch(portRules);
            if (!success) {
                log.error("批量保存或更新端口规则失败");
                return false;
            }

            // 3. 发布更新事件
            publishPortRuleUpdateEvent();

            return true;
        } catch (Exception e) {
            log.error("保存或更新端口规则失败", e);
            throw e;
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

        List<String> primaryKeyColumns = List.of("family", "port", "protocol", "sourceRule", "policy", "agentId", "permanent", "type", "zone");

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
        if (success && this.saveOrUpdatePortRules(List.of(portRule))) {
            // 更新端口的使用情况
            List<PortInfo> portInfos = portInfoService.updatePortInfoByPortRules(List.of(portRule), agentId);
            if (portInfos != null && !portInfos.isEmpty()) {
                portRule.setUsing(true);
            }
            publishPortRuleUpdateEvent();
            res = true;
        }

        return res;
    }

    /**
     * 发送Redis命令并获取新增端口规则的响应
     */
    private boolean sendRedisInsertCommandAndGetResponse(String nodeId, RedisCommandMessage<PortRule> build) {
        String pubStreamkey = "pub:" + nodeId + ":" + pubStreamKeySuffix;
        String subStreamkey = "sub:" + nodeId + ":" + subStreamKeySuffix;
        boolean success = false;

        try {

            Map<String, String> messageMap = JsonMessageConverter.beanToMap(build);
            RecordId recordId = streamProducer.publishMessage(pubStreamkey, messageMap);
            Map<Object, Object> value = streamResponseService.getResponseEntry(nodeId, subStreamkey,pubStreamkey, recordId);

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
        String pubStreamkey = "pub:" + nodeId + ":" + pubStreamKeySuffix;
        String subStreamkey = "sub:" + nodeId + ":" + subStreamKeySuffix;

        try {

            Map<String, String> messageMap = JsonMessageConverter.beanToMap(commandMap);
            RecordId recordId = streamProducer.publishMessage(pubStreamkey, messageMap);
            Map<Object, Object> value = streamResponseService.getResponseEntry(nodeId, subStreamkey,pubStreamkey, recordId);

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

            List<String> primaryKeyColumns = List.of("family", "port", "protocol", "sourceRule", "policy", "agentId", "permanent", "type", "zone");

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
            if (this.removeByIds(ruleIds)) {
                publishPortRuleUpdateEvent();
                return true;
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
        String pubStreamkey = "pub:" + nodeId + ":" + pubStreamKeySuffix;
        String subStreamkey = "sub:" + nodeId + ":" + subStreamKeySuffix;
        boolean success = false;

        try {
            Map<String, String> messageMap = JsonMessageConverter.beanToMap(build);
            RecordId recordId = streamProducer.publishMessage(pubStreamkey, messageMap);
            Map<Object, Object> value = streamResponseService.getResponseEntry(nodeId, subStreamkey, pubStreamkey, recordId);

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

            List<String> primaryKeyColumns = List.of("family", "port", "protocol", "sourceRule", "policy", "agentId", "permanent", "type", "zone");

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
                // 需要更新端口规则对应的 端口使用情况
                // 获取端口最新的使用情况
                List<PortInfo> portInfos =  portInfoService.updatePortInfoByPortRules(List.of(portRule), nodeId);
                // 更新字段Using
                portRule.setUsing(portInfos != null && !portInfos.isEmpty());

                this.saveOrUpdatePortRules(List.of(portRule));
                return true;
            }

            return false;
        } catch (Exception e) {
            log.error("Failed to update port rule with ruleId: {}", ruleId, e);
            return false;
        }
    }

    @Override
    public List<PortRule> queryAllPortRules() {
        return this.getBaseMapper().selectList(null);
    }

    /**
     * 发送Redis命令并获取更新端口规则的响应
     */
    private boolean sendRedisUpdateCommandAndGetResponse(String nodeId, RedisCommandMessage<PortRule> build) {
        String pubStreamkey = "pub:" + nodeId + ":" + pubStreamKeySuffix;
        String subStreamkey = "sub:" + nodeId + ":" + subStreamKeySuffix;
        boolean success = false;

        try {
            Map<String, String> messageMap = JsonMessageConverter.beanToMap(build);
            RecordId recordId = streamProducer.publishMessage(pubStreamkey, messageMap);
            Map<Object, Object> value = streamResponseService.getResponseEntry(nodeId, subStreamkey, pubStreamkey, recordId);

            return isResponseSuccess(value);

        } catch (Exception e) {
            log.error("Failed to update port rule for node: {}", nodeId, e);
            return success;
        }
    }

}
