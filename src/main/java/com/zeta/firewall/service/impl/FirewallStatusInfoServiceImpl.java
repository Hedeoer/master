package com.zeta.firewall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.zeta.firewall.dao.FirewallStatusInfoMapper;
import com.zeta.firewall.model.dto.RedisCommandMessage;
import com.zeta.firewall.model.entity.FirewallStatusInfo;
import com.zeta.firewall.model.enums.FireWallStatus;
import com.zeta.firewall.model.enums.FirewallOperationType;
import com.zeta.firewall.model.enums.PingStatus;
import com.zeta.firewall.service.FirewallStatusInfoService;
import com.zeta.firewall.service.StreamResponseService;
import com.zeta.firewall.subscirbe.StreamProducer;
import com.zeta.firewall.util.JsonMessageConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.zetaframework.core.utils.JSONUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public  class FirewallStatusInfoServiceImpl extends ServiceImpl<FirewallStatusInfoMapper, FirewallStatusInfo> implements FirewallStatusInfoService {

    private final StreamProducer streamProducer;
    private final StreamResponseService streamResponseService;

    public FirewallStatusInfoServiceImpl(StreamProducer streamProducer, StreamResponseService streamResponseService) {
        this.streamProducer = streamProducer;
        this.streamResponseService = streamResponseService;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public FirewallStatusInfo getFirewallStatusInfo(String agentId) {

        // 存储更新后的最新端口使用信息，默认为空列表
        FirewallStatusInfo result = new FirewallStatusInfo();

        HashMap<String, String> map = new HashMap<>();

        List<String> primaryKeyColumns = List.of("agentId", "firewallType");

        RedisCommandMessage<FirewallStatusInfo> build = RedisCommandMessage.<FirewallStatusInfo>builder()
                .agentId(agentId)
                .ts(System.currentTimeMillis() / 1000)
                .agentComponentType(RedisCommandMessage.ComponentType.FIREWALL)
                .dataOpType(RedisCommandMessage.OperationType.QUERY)
                .requestParams(map)
                .primaryKeyColumns(primaryKeyColumns)
                .build();

        List<FirewallStatusInfo> firewallStatusInfoFromAgent  = sendRedisQueryCommandAndGetResponse(agentId,build);

        if (!firewallStatusInfoFromAgent.isEmpty() && insertOrUpdateBatchFireWallStatusInfo(firewallStatusInfoFromAgent)) {
            result = firewallStatusInfoFromAgent.get(0);
        }

        return result;

    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean insertOrUpdateBatchFireWallStatusInfo(List<FirewallStatusInfo> firewallStatusInfoList) {
        if (firewallStatusInfoList == null || firewallStatusInfoList.isEmpty()) {
            return true;
        }

        for (FirewallStatusInfo entity : firewallStatusInfoList) {
            // 根据唯一索引查询
            QueryWrapper<FirewallStatusInfo> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("agent_id", entity.getAgentId())
                    .eq("firewall_type", entity.getFirewallType().getFirewallType());
            FirewallStatusInfo existing = getOne(queryWrapper);
            if (existing != null) {
                // 如果存在，设置主键 ID，触发更新
                entity.setId(existing.getId());
            }else{
                entity.setId(null);
            }
        }
        return  this.saveOrUpdateBatch(firewallStatusInfoList);

    }


    private boolean isResponseSuccess(Map<Object, Object> value) {
        return value.containsKey("status") && value.get("status").equals("200");
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean operateFirewall(String nodeId, String operation) {
        if (!StringUtils.hasText(nodeId) || !StringUtils.hasText(operation)) {
            log.error("Invalid parameters for firewall operation: nodeId={}, operation={}", nodeId, operation);
            return false;
        }

        // 验证操作类型
        FirewallOperationType operationType = FirewallOperationType.fromValue(operation);
        if (operationType == null) {
            log.error("Invalid operation type: {}", operation);
            return false;
        }

        try {
            // 构建请求参数
            HashMap<String, String> requestParams = new HashMap<>();
            requestParams.put("operation", operation);

            // 构建Redis命令消息
            RedisCommandMessage<FirewallStatusInfo> commandMessage = RedisCommandMessage.<FirewallStatusInfo>builder()
                    .agentId(nodeId)
                    .ts(System.currentTimeMillis() / 1000)
                    .agentComponentType(RedisCommandMessage.ComponentType.FIREWALL)
                    .dataOpType(RedisCommandMessage.OperationType.UPDATE)
                    .requestParams(requestParams)
                    .build();

            // 发送命令并获取响应
            boolean exec = sendRedisOperationCommandAndGetResponse(nodeId, commandMessage);
            if (!exec) {
                return false;
            }

            // 先从数据库中查询
            FirewallStatusInfo firewallStatusInfoDB = this.getOne(new QueryWrapper<FirewallStatusInfo>().eq("agent_id", nodeId));
            if (FirewallOperationType.RESTART.equals(operationType) || FirewallOperationType.START.equals(operationType)) {
                firewallStatusInfoDB.setStatus(FireWallStatus.ACTIVE);
            }else {
                firewallStatusInfoDB.setStatus(FireWallStatus.INACTIVE);
            }
            // 如果操作成功，则更新数据库
            return this.updateById(firewallStatusInfoDB);

        } catch (Exception e) {
            log.error("Failed to execute firewall operation: {} for node: {}", operation, nodeId, e);
            return false;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean operatePing(String nodeId, String pingStatusOp) {

        if (!StringUtils.hasText(nodeId) || !StringUtils.hasText(pingStatusOp)) {
            log.error("Invalid parameters for firewall operation: nodeId={}, operation={}", nodeId, pingStatusOp);
            return false;
        }

        // 验证操作类型
        if (!("Enable".equals(pingStatusOp) || "Disable".equals(pingStatusOp))) {
            log.error("Invalid operation type: {}", pingStatusOp);
            return false;
        }

        try {
            // 构建请求参数
            HashMap<String, String> requestParams = new HashMap<>();
            requestParams.put("operation", "Enable".equals(pingStatusOp) ? "unblockping" : "blockping");

            // 构建Redis命令消息
            RedisCommandMessage<FirewallStatusInfo> commandMessage = RedisCommandMessage.<FirewallStatusInfo>builder()
                    .agentId(nodeId)
                    .ts(System.currentTimeMillis() / 1000)
                    .agentComponentType(RedisCommandMessage.ComponentType.FIREWALL)
                    .dataOpType(RedisCommandMessage.OperationType.UPDATE)
                    .requestParams(requestParams)
                    .build();

            // 发送命令并获取响应
            boolean exec = sendRedisOperationCommandAndGetResponse(nodeId, commandMessage);
            if (!exec) {
                return false;
            }

            // 先从数据库中查询
            FirewallStatusInfo firewallStatusInfoDB = this.getOne(new QueryWrapper<FirewallStatusInfo>().eq("agent_id", nodeId));
            firewallStatusInfoDB.setPingDisabled("Enable".equals(pingStatusOp) ?  PingStatus.STATUS_DISABLE : PingStatus.STATUS_ENABLE );
            // 如果操作成功，则更新数据库
            return this.updateById(firewallStatusInfoDB);

        } catch (Exception e) {
            log.error("Failed to execute firewall operation: {} for node: {}", pingStatusOp, nodeId, e);
            return false;
        }
    }

    /**
     * 发送Redis命令并获取防火墙操作的响应
     * @param nodeId 节点ID
     * @param commandMessage Redis命令消息
     * @return 操作结果 true:成功 false:失败
     */
    private boolean sendRedisOperationCommandAndGetResponse(String nodeId, RedisCommandMessage<FirewallStatusInfo> commandMessage) {
        String pubStreamkey = "pub:" + nodeId + ":firewallStatusInfo";
        String subStreamkey = "sub:" + nodeId + ":firewallStatusInfo";

        try {
            Map<String, String> messageMap = JsonMessageConverter.beanToMap(commandMessage);
            RecordId recordId = streamProducer.publishMessage(pubStreamkey, messageMap);
            Map<Object, Object> value = streamResponseService.getResponseEntry(nodeId, subStreamkey, pubStreamkey, recordId);

            // 检查响应状态
            return isResponseSuccess(value);
        } catch (Exception e) {
            log.error("Failed to execute firewall operation for node: {}", nodeId, e);
            return false;
        }
    }

    /**
     * 发送 redis 查询节点防火墙状态命令并获取响应
     * @param nodeId 节点id
     * @param build redis 查询防火墙状态命令封装对象
     * @return  List<FirewallStatusInfo>
     */
    private List<FirewallStatusInfo> sendRedisQueryCommandAndGetResponse(String nodeId, RedisCommandMessage<FirewallStatusInfo> build) {
        String pubStreamkey = "pub:" + nodeId + ":firewallStatusInfo";
        String subStreamkey = "sub:" + nodeId + ":firewallStatusInfo";

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

            return JSONUtil.parseObject(dataStr, new TypeReference<List<FirewallStatusInfo>>() {});

        } catch (Exception e) {
            log.error("Failed to get firewall status for node: {}", nodeId, e);
            return Collections.emptyList();
        }
    }

}
