package com.zeta.firewall.service;

import com.zeta.firewall.exception.EmptyResultException;
import com.zeta.firewall.subscirbe.StreamConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * StreamResponseService
 *
 * 该服务用于通过 Redis Stream 消息队列，按需消费并获取结果数据。
 * 内置重试机制，若未获取到结果会自动重试指定次数。
 * 如所有重试均失败，则进入恢复方法进行异常兜底处理。
 *
 * 主要职责：
 * - 从指定 Redis Stream 获取数据
 * - 支持失败重试及最终失败恢复处理
 *
 * 为什么要重试：
 * 主节点向从节点发布命令，也许从节点响应不够及时（网络延迟，执行效率等），使得主节点的命令没有响应
 */
@Service
@Slf4j
public class StreamResponseService {

    /**
     * Redis 连接工厂，用于创建和管理 Redis 连接。
     */
    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    /**
     * 根据指定的 nodeId、Stream key 及 recordId 获取对应的消息内容。
     * 如果结果为空，则会自动重试（共4次），每次间隔2秒。
     *
     * @param nodeId       节点ID，用于拼接stream的主key
     * @param subStreamkey Stream的实际二级Key
     * @param recordId     要读取的消息的唯一ID
     * @return 消息内容，key-value 形式
     * @throws EmptyResultException 如果未获取到合法结果，将触发异常并进行重试
     */
    @Retryable(
            value = EmptyResultException.class,
            maxAttempts = 4,
            backoff = @Backoff(delay = 2000)
    )
    public Map<Object, Object> getResponseEntry(String nodeId, String subStreamkey, RecordId recordId) {
        log.info("Attempting to get response for recordId: {}, attempt: {}",
                recordId, RetrySynchronizationManager.getContext().getRetryCount());

        String pubStreamKey = "pub:" + nodeId;
        String groupName = "firewall_" + pubStreamKey + "_group";
        String consumerName = groupName + "_consumer";
        StreamConsumer streamConsumer = new StreamConsumer(redisConnectionFactory, subStreamkey, groupName, consumerName);

        MapRecord<String, Object, Object> response = streamConsumer.consumeNewMessagesWithRange(recordId);

        if (response == null || response.getValue() == null || response.getValue().isEmpty()) {
            log.info("Response is empty, will retry...");
            throw new EmptyResultException("Response is empty for recordId: " + recordId);
        }else{
            // 确认消息已处理完成，避免重复消费
            streamConsumer.acknowledgeMessage(recordId);
        }

        return response.getValue();
    }

    /**
     * 当所有重试都失败后会被调用的恢复方法。
     * 可自定义异常处理策略，比如返回默认内容或抛出自定义异常。
     *
     * @param e           最后一次重试失败的异常
     * @param nodeId      节点ID
     * @param subStreamkey Stream的实际二级Key
     * @param recordId    要读取的消息的唯一ID
     * @return 默认值（当前实现为直接抛出运行时异常）
     */
    @Recover
    public Map<Object, Object> recover(EmptyResultException e, String nodeId, String subStreamkey, RecordId recordId) {
        log.error("All retry attempts failed for recordId: {}", recordId, e);
        // 返回默认值或抛出异常
        throw new RuntimeException("Failed to get response after all retry attempts", e);
    }
}