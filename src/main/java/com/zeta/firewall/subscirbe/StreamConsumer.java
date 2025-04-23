package com.zeta.firewall.subscirbe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 带有消费者组的Stream消费者，使用Spring Data Redis实现
 * 对应 XREADGROUP GROUP group consumer [COUNT count] [BLOCK milliseconds] [NOACK] STREAMS key [key ...] id [id ...]
 */
public class StreamConsumer {
    private final RedisTemplate<String, Object> redisTemplate;
    private final StreamOperations<String, Object, Object> streamOps;
    private final String streamKey;
    private final String groupName;
    private final String consumerName;
    private static final Logger log = LoggerFactory.getLogger(StreamConsumer.class);

    public StreamConsumer(RedisConnectionFactory connectionFactory, 
                          String streamKey, 
                          String groupName, 
                          String consumerName) {
        // 初始化RedisTemplate
        this.redisTemplate = new RedisTemplate<>();
        this.redisTemplate.setConnectionFactory(connectionFactory);
        this.redisTemplate.setKeySerializer(StringRedisSerializer.UTF_8);
        this.redisTemplate.setHashKeySerializer(StringRedisSerializer.UTF_8);
        this.redisTemplate.setHashValueSerializer(StringRedisSerializer.UTF_8);
        this.redisTemplate.afterPropertiesSet();
        
        this.streamOps = this.redisTemplate.opsForStream();
        this.streamKey = streamKey;
        this.groupName = groupName;
        this.consumerName = consumerName;

        createConsumerGroupIfNotExists();
    }

    private void createConsumerGroupIfNotExists() {
        // 1. 检查Stream是否存在
        boolean streamExists = redisTemplate.hasKey(streamKey);

        // 2. 如果Stream不存在，创建一个初始条目
        if (!streamExists) {
            Map<String, String> initialEntry = new HashMap<>();
            initialEntry.put("init", "initial");
            streamOps.add(streamKey, initialEntry);
            log.info("Stream created with initial entry: {}", streamKey);
        }

        // 3. 检查消费者组是否存在
        boolean groupExists = false;
        try {
            StreamInfo.XInfoGroups groups = streamOps.groups(streamKey);
            groupExists = groups.stream()
                    .anyMatch(group -> groupName.equals(group.groupName()));
        } catch (Exception e) {
            // 忽略NOGROUP错误，其他错误重新抛出
            if (e.getMessage() != null && !e.getMessage().contains("NOGROUP")) {
                throw e;
            }
        }

        // 4. 如果消费者组不存在，创建它
        if (!groupExists) {
            try {
                // 使用0-0从最开始消费
                streamOps.createGroup(streamKey, ReadOffset.from("0-0"), groupName);
                log.info("Consumer group created: {}", groupName);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                    log.info("Consumer group already exists: " + groupName);
                } else if (e.getMessage() != null && e.getMessage().contains("Invalid stream ID")) {
                    // 尝试用最新消息ID创建
                    streamOps.createGroup(streamKey, ReadOffset.latest(), groupName);
                    log.info("Consumer group created with LAST_ENTRY: {}", groupName);
                } else {
                    throw e;
                }
            }
        } else {
            log.info("Consumer group already exists: {}" ,groupName);
        }
    }

    /**
     * 以消费者组（Consumer Group）的方式阻塞消费 Redis Stream 中的新消息
     * <p>
     * 此方法使用 XREADGROUP 命令，从指定的 Stream 中读取尚未被任何消费者处理的新消息。
     * 拉取数不会超过 {@code count}，可阻塞等待新消息到来最长 {@code blockTimeMillis} 毫秒。
     *
     * @param count           最多消费的消息条数
     * @param blockTimeMillis 阻塞等待新消息的最大时间（毫秒），0 表示一直阻塞直到有消息
     * @return 读取的消息列表，若无新消息则返回空列表
     */
    public List<MapRecord<String, Object, Object>> consumeNewMessages(int count, int blockTimeMillis) {
        Consumer consumer = Consumer.from(groupName, consumerName);
        
        // 配置读取参数
        StreamReadOptions readOptions = StreamReadOptions.empty()
                .count(count);
        
        if (blockTimeMillis > 0) {
            readOptions = readOptions.block(Duration.ofMillis(blockTimeMillis));
        } else if (blockTimeMillis == 0) {
            readOptions = readOptions.block(Duration.ZERO); // 无限期阻塞
        }
        
        // 读取未投递的新消息 (> 对应原Jedis中的StreamEntryID.XREADGROUP_UNDELIVERED_ENTRY)
        List<MapRecord<String, Object, Object>> records = streamOps.read(
                consumer,
                readOptions,
                StreamOffset.create(streamKey, ReadOffset.from(">"))
        );
        
        return records != null ? records : Collections.emptyList();
    }
    
    /**
     * 确认消息已处理完成
     * 
     * @param recordId 要确认的消息ID
     * @return 确认成功的消息数量
     */
    public Long acknowledgeMessage(RecordId recordId) {
        return streamOps.acknowledge(streamKey, groupName, recordId);
    }
    
    /**
     * 批量确认多条消息
     * 
     * @param recordIds 要确认的消息ID列表
     * @return 确认成功的消息数量
     */
    public Long acknowledgeMessages(RecordId... recordIds) {
        return streamOps.acknowledge(streamKey, groupName, recordIds);
    }
}
