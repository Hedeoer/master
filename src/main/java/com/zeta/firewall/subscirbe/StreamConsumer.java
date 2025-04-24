package com.zeta.firewall.subscirbe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

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
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    // Redis错误消息常量
    private static final String ERROR_BUSYGROUP = "BUSYGROUP";
    private static final String ERROR_INVALID_STREAM_ID = "Invalid stream ID";

    public StreamConsumer(RedisConnectionFactory connectionFactory,
                          String streamKey,
                          String groupName,
                          String consumerName) {
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

    /**
     * 确保消费者组存在，如果Stream不存在则自动创建
     * 使用XGROUP CREATE命令的MKSTREAM选项直接创建Stream，无需插入初始条目
     */
    private void createConsumerGroupIfNotExists() {
        try {
            // 使用RedisCallback直接执行XGROUP CREATE命令，带MKSTREAM选项
            redisTemplate.execute((RedisCallback<String>) connection -> {
                byte[] streamKeyBytes = redisTemplate.getStringSerializer().serialize(streamKey);

                try {
                    // 使用MKSTREAM选项创建Stream和消费者组
                    // 相当于执行: XGROUP CREATE streamKey groupName 0-0 MKSTREAM
                    connection.xGroupCreate(streamKeyBytes,
                            groupName,
                            ReadOffset.from("0-0"),
                            true);  // true表示使用MKSTREAM选项
                    log.info("Created consumer group and stream: {}", groupName);
                } catch (Exception e) {
                    // 处理组已存在的错误
                    if (e.getMessage() != null && e.getMessage().contains(ERROR_BUSYGROUP)) {
                        log.info("Consumer group already exists: {}", groupName);
                    }
                    // 处理无效Stream ID错误
                    else if (e.getMessage() != null && e.getMessage().contains(ERROR_INVALID_STREAM_ID)) {
                        try {
                            // 尝试用最新消息ID创建
                            connection.xGroupCreate(streamKeyBytes,
                                    groupName,
                                    ReadOffset.latest(),
                                    true);  // 仍然使用MKSTREAM选项
                            log.info("Created consumer group with latest offset: {}", groupName);
                        } catch (Exception ex) {
                            throw new StreamConsumerException("Failed to create consumer group with latest offset", ex);
                        }
                    } else {
                        throw new StreamConsumerException("Failed to create consumer group", e);
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to initialize consumer group for stream: {}", streamKey, e);
            throw new StreamConsumerException("Failed to initialize consumer group", e);
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
    public List<MapRecord<String, Object, Object>> consumeNewMessages(int count, Integer blockTimeMillis) {
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
     * 从指定的 Redis Stream 的起始 RecordId 开始，
     * 以当前 groupName 消费者组中的 consumerName 为消费者，
     * 读取一条新的消息（支持阻塞或非阻塞读取）并返回。
     *
     * 该方法通过 blockTimeMillis 参数控制读取行为：
     * - blockTimeMillis > 0 时，阻塞等待新消息的最大时间（单位：毫秒）；
     * - blockTimeMillis <= 0 时，永久阻塞直到有新消息到达（依赖底层实现，不同 Redis 客户端行为可能略有差异）。
     * 未读取到新消息时，返回 null。
     *
     * @param startId 消息读取的起始 RecordId（即 Stream 的消息 ID）
     * @param blockTimeMillis  阻塞等待新消息的最大时间（毫秒），<=0 表示无限期阻塞直到有新消息
     * @return 读取到的第一条消息（MapRecord 类型），如果没有消息则返回 null
     */
    public MapRecord<String, Object, Object> consumeNewMessages(RecordId startId, Integer blockTimeMillis) {
        Consumer consumer = Consumer.from(groupName, consumerName);

        // 读取参数：只读取一条新消息，从指定RecordId开始
        StreamReadOptions readOptions = StreamReadOptions.empty().count(1)
                .block(Duration.ofMillis(blockTimeMillis));

        List<MapRecord<String, Object, Object>> records = streamOps.read(
                consumer,
                readOptions,
                StreamOffset.create(streamKey, ReadOffset.from(startId.getValue()))
        );

        // 返回第一条消息（如果有），否则返回null
        return (records != null && !records.isEmpty()) ? records.get(0) : null;
    }

    /**
     * 使用XRANGE命令读取指定ID的消息
     * 
     * @param recordId 要读取的消息ID
     * @return 读取的消息，如果消息不存在返回null
     */
    public MapRecord<String, Object, Object> consumeNewMessagesWithRange(RecordId recordId) {
        // 使用XRANGE命令读取指定ID的消息
        // 设置start和end为相同的recordId，这样就只会返回这一条消息
        List<MapRecord<String, Object, Object>> records = streamOps.range(
                streamKey,
                Range.closed(recordId.getValue(), recordId.getValue())
        );

        return records != null && !records.isEmpty() ? records.get(0) : null;
    }

    /**
     * 使用XREAD命令读取指定ID的消息，支持阻塞和非阻塞模式
     *
     * @param recordId 要读取的消息ID
     * @param blockTimeMillis 阻塞等待时间（毫秒）。
     *                       大于0表示阻塞指定毫秒数，
     *                       等于0表示永久阻塞，
     *                       小于0表示非阻塞
     * @return 读取的消息，如果消息不存在返回null
     */
    public MapRecord<String, Object, Object> consumeNewMessagesWithXRead(RecordId recordId, Integer blockTimeMillis) {
        // 配置读取参数
        StreamReadOptions readOptions = StreamReadOptions.empty()
                .count(1);  // 只读取一条消息

        // 配置阻塞参数
        if (blockTimeMillis != null) {
            if (blockTimeMillis > 0) {
                readOptions = readOptions.block(Duration.ofMillis(blockTimeMillis));
            } else if (blockTimeMillis == 0) {
                readOptions = readOptions.block(Duration.ZERO); // 无限期阻塞
            }
            // blockTimeMillis < 0 时不设置block选项，即非阻塞模式
        }

        // 使用XREAD命令读取消息
        List<MapRecord<String, Object, Object>> records = streamOps.read(
                readOptions,
                StreamOffset.create(streamKey, ReadOffset.from(recordId.getValue()))
        );

        return records != null && !records.isEmpty() ? records.get(0) : null;
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

    /**
     * 检查Stream是否存在
     *
     * @return 如果Stream存在返回true，否则返回false
     */
    public boolean streamExists() {
        return redisTemplate.hasKey(streamKey);
    }

    /**
     * 创建空的Stream，不添加任何初始条目
     * 使用XGROUP CREATE命令的MKSTREAM选项创建空的Stream
     *
     * @return 如果创建成功返回true，否则返回false
     */
    public boolean createEmptyStream() {
        if (streamExists()) {
            return false; // Stream已存在
        }

        try {
            // 使用RedisCallback直接执行XGROUP CREATE命令，带MKSTREAM选项
            redisTemplate.execute((RedisCallback<Boolean>) connection -> {
                byte[] streamKeyBytes = redisTemplate.getStringSerializer().serialize(streamKey);
                try {
                    // 使用MKSTREAM选项创建Stream
                    connection.xGroupCreate(streamKeyBytes, "temp-group", ReadOffset.latest(), true);

                    // 删除临时消费者组
                    connection.del(redisTemplate.getStringSerializer().serialize(String.format("%s:%s", streamKey, "temp-group")));

                    return true;
                } catch (Exception e) {
                    log.error("Failed to create empty stream: {}", streamKey, e);
                    return false;
                }
            });
            return true;
        } catch (Exception e) {
            log.error("Failed to create empty stream: {}", streamKey, e);
            return false;
        }
    }
}
