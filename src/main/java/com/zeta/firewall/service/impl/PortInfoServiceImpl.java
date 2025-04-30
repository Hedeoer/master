package com.zeta.firewall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeta.firewall.dao.PortInfoMapper;
import com.zeta.firewall.model.dto.RedisCommandMessage;
import com.zeta.firewall.model.entity.PortInfo;
import com.zeta.firewall.model.entity.PortRule;
import com.zeta.firewall.service.PortInfoService;
import com.zeta.firewall.service.StreamResponseService;
import com.zeta.firewall.subscirbe.StreamProducer;
import com.zeta.firewall.util.JsonMessageConverter;
import com.zeta.firewall.util.PortRuleUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zetaframework.core.utils.JSONUtil;

import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
public class PortInfoServiceImpl extends ServiceImpl<PortInfoMapper, PortInfo> implements PortInfoService {

    private final StreamProducer streamProducer;
    private final StreamResponseService streamResponseService;
    private final ObjectMapper mapper = new ObjectMapper();


    public PortInfoServiceImpl(StreamProducer streamProducer, StreamResponseService streamResponseService) {
        this.streamProducer = streamProducer;
        this.streamResponseService = streamResponseService;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, List<PortInfo>> getPortInfosByPortRules(List<PortRule> portRules, String nodeId) {
        // redis 命令
        // 获取响应
        // 写入mysql
        // 返回结果

        boolean res = false;

        // 从数据库查询

        // portRules 对象展开 和 去重
        List<PortInfo> portInfos = PortRuleUtils.expandAndDeduplicatePortRules(portRules);

        /**
         * SELECT *
         * FROM firewall_port_info
         * WHERE (agent_id, protocol, port_number) IN
         *     (('node1', 'tcp', 80),
         *      ('node2', 'udp', 443),
         *      ('node3', 'tcp', 8080));
         */
        List<PortInfo> dbPortInfos = this.list(new QueryWrapper<PortInfo>()
                .in("agent_id", portRules.stream().map(PortRule::getAgentId).collect(Collectors.toList()))
                .in("protocol", portRules.stream().map(PortRule::getProtocol).collect(Collectors.toList()))
                .in("port_number", portRules.stream().map(PortRule::getPort).collect(Collectors.toList())));

        // dbPortInfos 和 portInfos 数量不一致，则需要发送redis命令来获取最新的端口
        if (dbPortInfos.size() == portInfos.size()) {
            // 数据库中已经有需要查询的全部端口使用情况
            // 转化为Map<String, List<PortInfo>> 返回
            return PortRuleUtils.connectPortInfosWithPortRules(portRules, dbPortInfos);
        }

        // 保存端口规则到agent
        // 获取agent节点的添加添加的结果，成功还是失败
        // 构建消息体
        HashMap<String, String> map = new HashMap<>();
        map.put("portType", "RANGE_PORT_COMMA");
        // todo 端口信息封装
        // 去重端口号
        List<String> needPortUsageInfos = portInfos.stream()
                .map(PortInfo::getPortNumber)
                .map(String::valueOf)
                .distinct()
                .collect(Collectors.toList());
        try {
            map.put("port", mapper.writeValueAsString(needPortUsageInfos));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        List<String> primaryKeyColumns = List.of("agentId", "protocol","portNumber");

        RedisCommandMessage<PortRule> build = RedisCommandMessage.<PortRule>builder()
                .agentId(nodeId)
                .ts(System.currentTimeMillis() / 1000)
                .agentComponentType(RedisCommandMessage.ComponentType.PORT)
                .dataOpType(RedisCommandMessage.OperationType.QUERY)
                .requestParams(map)
                .primaryKeyColumns(primaryKeyColumns)
                .build();


        // 查询端口使用情况命令如何判断是否成功？
        List<PortInfo> portInfosFromAgent  = sendRedisQueryCommandAndGetResponse(nodeId,build);

        Boolean execResult = this.insertOrUpdateBatchPortInfos(portInfosFromAgent);

        // 如果保存到数据库成功，且 agent节点响应的端口使用情况列表不为空，则返回
        if (execResult && portInfosFromAgent != null && !portInfosFromAgent.isEmpty()) {
            Map<String, List<PortInfo>> result = PortRuleUtils.connectPortInfosWithPortRules(portRules, portInfosFromAgent);
            return result;
        }

        // 否则，返回空集合
        return Collections.emptyMap();
    }

    /**
     * 发送 redis 查询节点端口使用情况命令并获取响应
     * @param nodeId 节点id
     * @param build redis 查询节点端口使用情况命令封装对象
     * @return  List<PortInfo>
     */
    private List<PortInfo> sendRedisQueryCommandAndGetResponse(String nodeId, RedisCommandMessage<PortRule> build) {
        String pubStreamkey = "pub:" + nodeId + ":portInfo";
        String subStreamkey = "sub:" + nodeId + ":portInfo";

        try {

            Map<String, String> messageMap = JsonMessageConverter.beanToMap(build);
            RecordId recordId = streamProducer.publishMessage(pubStreamkey, messageMap);
            Map<Object, Object> value = streamResponseService.getResponseEntry(nodeId, subStreamkey, pubStreamkey,recordId);

            // 检查响应状态
            if (!isResponseSuccess(value)) {
                return Collections.emptyList();
            }

            // 解析响应数据
            String dataStr = String.valueOf(value.get("data"));
            if (dataStr == null || dataStr.equals("null")) {
                return Collections.emptyList();
            }

            return JSONUtil.parseObject(dataStr, new TypeReference<List<PortInfo>>() {});

        } catch (Exception e) {
            log.error("Failed to get port infos for node: {}", nodeId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 批量保存或更新端口信息
     * @param portInfos 端口信息列表
     * @return 保存或更新结果 true:成功表示全部成功； 其他情况返回 false
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean insertOrUpdateBatchPortInfos(List<PortInfo> portInfos) {

        // null，无需更新，返回false
        if (portInfos == null) {
            log.warn("Port info list is  null, nothing to save or update");
            return false;
        }
        // 空列表，无需更新，返回true
        if (portInfos.isEmpty()) {
            log.warn("Port info list is empty , nothing to save or update");
            return true;
        }

        // saveOrUpdateBatch 方法通过主键来判断是否为更新操作。如果表中定义了唯一键；将要执行saveOrUpdateBatch
        // 的数据中有唯一键重复，会报唯一索引冲突
        // 故在执行 saveOrUpdateBatch前通过唯一键查询来设置每条记录的主键，从而正确触发（插入或更新）
        for (PortInfo entity : portInfos) {
            // 根据唯一索引查询
            QueryWrapper<PortInfo> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("agent_id", entity.getAgentId())
                    .eq("protocol", entity.getProtocol())
                    .eq("port_number", entity.getPortNumber());
            PortInfo existing = getOne(queryWrapper);
            if (existing != null) {
                // 如果存在，设置主键 ID，触发更新
                entity.setId(existing.getId());
            }else{
                entity.setId(null);
            }
        }
        return  this.saveOrUpdateBatch(portInfos);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public List<PortInfo> updatePortInfoByPortRules(List<PortRule> portRules, String nodeId) {

        // 存储更新后的最新端口使用信息，默认为空列表
        List<PortInfo> result = new ArrayList<>();

        HashMap<String, String> map = new HashMap<>();
        map.put("portType", "RANGE_PORT_COMMA");

        // 去重端口号
        List<String> uniquePorts = PortRuleUtils.extractUniquePortsFromRules(portRules);

        try {
            map.put("port", mapper.writeValueAsString(uniquePorts));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        List<String> primaryKeyColumns = List.of("agentId", "protocol","portNumber");

        RedisCommandMessage<PortRule> build = RedisCommandMessage.<PortRule>builder()
                .agentId(nodeId)
                .ts(System.currentTimeMillis() / 1000)
                .agentComponentType(RedisCommandMessage.ComponentType.PORT)
                .dataOpType(RedisCommandMessage.OperationType.QUERY)
                .requestParams(map)
                .primaryKeyColumns(primaryKeyColumns)
                .build();


        // 查询端口使用情况命令如何判断是否成功？
        List<PortInfo> portInfosFromAgent  = sendRedisQueryCommandAndGetResponse(nodeId,build);

        if (insertOrUpdateBatchPortInfos(portInfosFromAgent)) {
            result = portInfosFromAgent;
        }

        return result;
    }

    private boolean isResponseSuccess(Map<Object, Object> value) {
        return value.containsKey("status") && value.get("status").equals("200");
    }

    /**
     * 删除数据库中与notInUsePortInfos列表中记录相匹配的端口信息
     * 匹配条件：agentId、protocol和portNumber相同
     *
     * @param notInUsePortInfos 不再使用的端口信息列表
     * @return 删除成功返回true，否则返回false
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean removePortInfosNotInUse(List<PortInfo> notInUsePortInfos) {
        if (notInUsePortInfos == null || notInUsePortInfos.isEmpty()) {
            return true; // 没有需要删除的记录，视为成功
        }

        try {
            // 构建条件删除
            QueryWrapper<PortInfo> queryWrapper = new QueryWrapper<>();

            // 构建OR条件组合：(agentId=? AND protocol=? AND portNumber=?) OR (agentId=? AND protocol=? AND portNumber=?) ...
            for (PortInfo portInfo : notInUsePortInfos) {
                queryWrapper.or(wrapper -> wrapper
                    .eq("agent_id", portInfo.getAgentId())
                    .eq("protocol", portInfo.getProtocol())
                    .eq("port_number", portInfo.getPortNumber()));
            }

            // 执行删除操作
            int deletedCount = baseMapper.delete(queryWrapper);
            log.info("Removed {} port infos that are no longer in use", deletedCount);

            return true;
        } catch (Exception e) {
            log.error("Failed to remove port infos", e);
            return false;
        }
    }

    @Override
    public List<PortInfo> queryAllPortInfosDB() {
        return this.getBaseMapper().selectList(null);

    }
}
