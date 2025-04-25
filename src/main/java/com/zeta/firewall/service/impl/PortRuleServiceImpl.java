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
import org.zetaframework.core.utils.JSONUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public List<PortRule> getPortRulesByNodeId(String nodeId) {
        String pubStreamkey = "pub:" + nodeId;
        String subStreamkey = "sub:" + nodeId;

        try {
            // 构建消息体
            HashMap<String, String> map = new HashMap<>();
            map.put("zoneName","public");

            List<String> primaryKeyColumns = List.of("port", "protocol");

            PortRule portRule = PortRule.builder()
                    .port("2323")
                    .build();

            RedisCommandMessage<PortRule> build = RedisCommandMessage.<PortRule>builder()
                    .agentId(nodeId)
                    .ts(System.currentTimeMillis() / 1000)
                    .agentComponentType(RedisCommandMessage.ComponentType.FIREWALL)
                    .dataOpType(RedisCommandMessage.OperationType.QUERY)
                    .requestParams(map)
                    .primaryKeyColumns(primaryKeyColumns)
                    .data(Collections.<PortRule>emptyList())
                    .old(portRule)
                    .build();

            Map<String, String> messageMap = JsonMessageConverter.beanToMap(build);

            // 发布消息
            RecordId recordId = streamProducer.publishMessage(pubStreamkey, messageMap);

            // 获取响应
            Map<Object, Object> value = streamResponseService.getResponseEntry(nodeId, subStreamkey, recordId);

            // 判断响应状态
            boolean respSuccess = value.containsKey("status") && value.get("status").equals("200");
            if (!respSuccess) {
                log.warn("Failed to get response from node: {}", nodeId);
                return Collections.emptyList();
            }

            // 解析响应数据
            String dataStr = String.valueOf(value.get("data"));
            if (dataStr == null || dataStr.equals("null")) {
                return Collections.emptyList();
            }

            // 将响应数据转换为PortRule列表
            List<PortRule> portRules = JSONUtil.parseObject(dataStr, new TypeReference<List<PortRule>>() {
            });
            // 数据持久化更新到 数据库，如何 portRule的主键id为null，会自动填充
            // todo 不能保证事务的问题要解决
            this.saveOrUpdateBatch(portRules);

            return portRules;

        } catch (Exception e) {
            log.error("Failed to get port rules for node: {}", nodeId, e);
            return Collections.emptyList();
        }
    }
}