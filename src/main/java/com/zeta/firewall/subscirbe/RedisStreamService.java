package com.zeta.firewall.subscirbe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis Stream 服务类，提供对 Stream 的各种操作，包括信息查询和管理。
 */
@Service
public class RedisStreamService {

    // 日志记录器
    private static final Logger log = LoggerFactory.getLogger(RedisStreamService.class);

    // Spring Data Redis 模板，用于操作字符串类型的键和值
    private final StringRedisTemplate stringRedisTemplate;
    // Stream 操作 spécifiques，针对 String key, String field, String value
    private final StreamOperations<String, String, String> streamOps;

    @Autowired
    public RedisStreamService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.streamOps = stringRedisTemplate.opsForStream();
    }

    /**
     * 获取 Redis 中所有类型为 Stream 的 key。
     * 使用 SCAN 命令，对生产环境友好。
     *
     * @param matchPattern 匹配模式，例如 "*" 匹配所有， "mystream:*" 匹配特定前缀
     * @param scanCount    每次 SCAN 命令迭代的元素数量建议值
     * @return Stream key 的列表
     */
    public List<String> getAllStreamKeys(String matchPattern, long scanCount) {
        List<String> streamKeys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(matchPattern).count(scanCount).build();
        // 使用 try-with-resources 确保 Cursor 被关闭
        try (Cursor<byte[]> cursor = Objects.requireNonNull(stringRedisTemplate.getConnectionFactory())
                .getConnection()
                .scan(options)) {
            while (cursor.hasNext()) {
                String key = new String(cursor.next()); // 将 byte[] 键转换为 String
                // 检查 key 的类型是否为 Stream
                if (stringRedisTemplate.type(key) == DataType.STREAM) {
                    streamKeys.add(key);
                }
            }
        }
        return streamKeys;
    }

    /**
     * 获取 Redis 中所有类型为 Stream 的 key (使用默认扫描参数)。
     *
     * @return Stream key 的列表
     */
    public List<String> getAllStreamKeys() {
        return getAllStreamKeys("*", 100L); // 默认扫描所有，每次100个
    }

    /**
     * 1. 获取某个 Redis Stream 的整体状况 (对应 XINFO STREAM 命令)。
     *
     * @param streamKey Stream 的键名
     * @return Stream 的信息对象 {@link StreamInfo.XInfoStream}，如果 Stream 不存在则返回 null。
     */
    public StreamInfo.XInfoStream getStreamInfo(String streamKey) {
        try {
            log.debug("正在获取 Stream '{}' 的信息...", streamKey);
            return streamOps.info(streamKey);
        } catch (RedisSystemException e) {
            // 当 Stream 不存在时，Redis 通常返回 "NOENT No such key" 错误
            if (e.getMessage() != null && e.getMessage().contains("NOENT")) {
                log.warn("获取 Stream 信息失败：Stream '{}' 不存在。", streamKey);
                return null; // 返回 null 表示 Stream 不存在
            }
            // 对于其他 Redis 系统异常，记录错误并重新抛出
            log.error("获取 Stream '{}' 信息时发生 Redis 系统异常", streamKey, e);
            throw e;
        }
    }

    /**
     * 2. 获取某个 Stream 所有消费者组的整体状况 (对应 XINFO GROUPS 命令)。
     *
     * @param streamKey Stream 的键名
     * @return 消费者组信息列表 (List of {@link StreamInfo.XInfoGroup})。
     *         如果 Stream 不存在或没有消费者组，则返回空列表。
     */
    public List<StreamInfo.XInfoGroup> getStreamGroupInfo(String streamKey) {
        try {
            log.debug("正在获取 Stream '{}' 的消费者组信息...", streamKey);
            StreamInfo.XInfoGroups groups = streamOps.groups(streamKey);
            if (groups == null) {
                // Stream 可能不存在，或者存在但没有消费者组
                log.debug("Stream '{}' 不存在或没有找到消费者组。", streamKey);
                return Collections.emptyList();
            }
            // 将 StreamInfo.XInfoGroups 转换为 List<StreamInfo.XInfoGroup>
            return groups.stream().collect(Collectors.toList());
        } catch (RedisSystemException e) {
            if (e.getMessage() != null && e.getMessage().contains("NOENT")) {
                log.warn("获取消费者组信息失败：Stream '{}' 不存在。", streamKey);
                return Collections.emptyList();
            }
            log.error("获取 Stream '{}' 的消费者组信息时发生 Redis 系统异常", streamKey, e);
            throw e;
        }
    }

    /**
     * 3. 获取某个消费者组中所有消费者的状况 (对应 XINFO CONSUMERS 命令)。
     *
     * @param streamKey Stream 的键名
     * @param groupName 消费者组名称
     * @return 消费者信息列表 (List of {@link StreamInfo.XInfoConsumer})。
     *         如果 Stream 或组不存在，或组内无消费者，则返回空列表。
     */
    public List<StreamInfo.XInfoConsumer> getStreamConsumerInfo(String streamKey, String groupName) {
        try {
            log.debug("正在获取 Stream '{}' 中消费者组 '{}' 的消费者信息...", streamKey, groupName);
            StreamInfo.XInfoConsumers consumers = streamOps.consumers(streamKey, groupName);
            if (consumers == null) {
                // Stream/Group 可能不存在，或者 Group 中没有已声明的消费者
                log.debug("Stream '{}' 的消费者组 '{}' 不存在或没有找到消费者。", streamKey, groupName);
                return Collections.emptyList();
            }
            return consumers.stream().collect(Collectors.toList());
        } catch (RedisSystemException e) {
            // Redis 可能返回 "NOGROUP" 或 "NOENT"
            if (e.getMessage() != null && (e.getMessage().contains("NOENT") || e.getMessage().contains("NOGROUP"))) {
                log.warn("获取消费者信息失败：Stream '{}' 或消费者组 '{}' 不存在。", streamKey, groupName);
                return Collections.emptyList();
            }
            log.error("获取 Stream '{}' 中消费者组 '{}' 的消费者信息时发生 Redis 系统异常", streamKey, groupName, e);
            throw e;
        }
    }

    /**
     * 4. 提供删除某个 Stream 的功能，在删除某个 Stream 的同时删除相关联的消费者组。
     *
     * @param streamKey Stream 的键名
     * @return 如果 Stream 及其关联的消费者组被成功删除则返回 true，否则 false。
     */
    public boolean deleteStreamAndAssociatedGroups(String streamKey) {
        log.info("准备删除 Stream '{}' 及其关联的消费者组...", streamKey);

        // 首先检查 Stream 是否存在
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(streamKey))) {
            log.warn("Stream '{}' 不存在，无需删除。", streamKey);
            return false;
        }

        // 1. 获取并删除所有关联的消费者组
        List<StreamInfo.XInfoGroup> groups = getStreamGroupInfo(streamKey); // 此方法内部已有日志
        if (!CollectionUtils.isEmpty(groups)) {
            log.info("发现 Stream '{}' 关联了 {} 个消费者组，正在删除它们...", streamKey, groups.size());
            for (StreamInfo.XInfoGroup group : groups) {
                try {
                    log.debug("正在销毁 Stream '{}' 的消费者组 '{}'...", streamKey, group.groupName());
                    Boolean destroyed = streamOps.destroyGroup(streamKey, group.groupName());
                    if (Boolean.TRUE.equals(destroyed)) {
                        log.info("成功销毁消费者组: {}", group.groupName());
                    } else {
                        // XGROUP DESTROY 通常在组不存在时也返回成功 (1 or 0 if already gone in some impls)
                        // 或者如果键不存在，它可能抛出错误，而不是返回 false。
                        // Spring Data Redis 的 destroyGroup 返回 Boolean，如果组不存在可能返回 false 或抛异常。
                        log.warn("未能销毁消费者组 '{}' (可能已被删除或发生错误)。", group.groupName());
                    }
                } catch (Exception e) {
                    // 捕获销毁单个组时可能发生的任何异常
                    log.error("销毁 Stream '{}' 的消费者组 '{}' 时发生错误", streamKey, group.groupName(), e);
                    // 决定是否继续删除其他组和 Stream，当前策略是记录错误并继续
                }
            }
        } else {
            log.info("Stream '{}' 没有发现关联的消费者组。", streamKey);
        }

        // 2. 删除 Stream 本身
        log.info("正在删除 Stream '{}' 本身...", streamKey);
        Boolean deleted = stringRedisTemplate.delete(streamKey);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("成功删除 Stream: {}", streamKey);
            return true;
        } else {
            // Stream 可能已被其他进程删除
            log.warn("删除 Stream '{}' 失败 (可能已被其他进程删除)。", streamKey);
            return false;
        }
    }

    /**
     * 向指定的 Stream 添加一条消息。
     *
     * @param streamKey Stream 的键名
     * @param message   消息内容 (Map<String, String>)
     * @return 成功添加的消息的 ID，如果失败则返回 null。
     */
    public String addMessageToStream(String streamKey, Map<String, String> message) {
        log.debug("正在向 Stream '{}' 添加消息: {}", streamKey, message);
        try {
            RecordId recordId = streamOps.add(streamKey, message);
            if (recordId != null) {
                log.info("消息已成功添加到 Stream '{}'，ID 为: {}", streamKey, recordId.getValue());
                return recordId.getValue();
            }
            log.warn("向 Stream '{}' 添加消息失败，返回的 RecordId 为 null。", streamKey);
            return null;
        } catch (Exception e) {
            log.error("向 Stream '{}' 添加消息时发生异常", streamKey, e);
            return null;
        }
    }

    /**
     * 为指定的 Stream 创建一个消费者组 (修正版)。
     *
     * @param streamKey    Stream 的键名
     * @param groupName    要创建的消费者组名称
     * @param offsetString 读取偏移量。"0" 表示从头开始，"$" 表示从最新的消息开始（不包括当前已有的）。
     * @return 如果消费者组成功创建或已存在，则返回 true，否则 false。
     */
    public boolean createConsumerGroupCorrected(String streamKey, String groupName, String offsetString) {
        try {
            log.debug("尝试为 Stream '{}' 创建消费者组 '{}'，偏移量为 '{}'", streamKey, groupName, offsetString);

            // 确保 Stream 存在，如果不存在，通过 XADD 添加一个占位消息来创建它
            // 这通常是在新 Stream 上创建组之前所必需的
            if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(streamKey))) {
                log.info("Stream '{}' 不存在。正在通过添加初始消息来创建它...", streamKey);
                streamOps.add(streamKey, Collections.singletonMap("init_stream_placeholder", "true"));
            }

            ReadOffset readOffset;
            if ("0".equals(offsetString)) {
                readOffset = ReadOffset.from("0-0"); // 从 Stream 的起始位置读取
            } else if ("$".equals(offsetString)) {
                readOffset = ReadOffset.latest();    // 只读取新到达的消息
            } else {
                // 可以根据需要支持特定的消息ID作为偏移量
                log.warn("不支持的偏移量字符串 '{}'，将默认使用 ReadOffset.latest() (只读新消息)", offsetString);
                readOffset = ReadOffset.latest();
            }

            // 调用 XGROUP CREATE 命令
            String result = streamOps.createGroup(streamKey, readOffset, groupName);

            if ("OK".equalsIgnoreCase(result)) {
                log.info("成功为 Stream '{}' 创建消费者组 '{}'", streamKey, groupName);
                return true;
            } else {
                // 这种情况通常不会发生，因为如果 Redis 返回错误，Spring Data Redis 会将其转换为异常
                log.warn("为 Stream '{}' 创建消费者组 '{}' 失败。Redis 返回: {}", streamKey, groupName, result);
                return false;
            }
        } catch (RedisSystemException e) {
            // Redis 返回 "BUSYGROUP Consumer Group name already exists"
            if (e.getMessage() != null && e.getMessage().toUpperCase().contains("BUSYGROUP")) {
                log.warn("消费者组 '{}' 已存在于 Stream '{}'。", groupName, streamKey);
                return true; // 组已存在，对于此操作的目的而言，也视为成功
            }
            log.error("为 Stream '{}' 创建消费者组 '{}' 时发生错误", streamKey, groupName, e);
            return false;
        }
    }


    /**
     * 消费者从指定的 Stream 的消费者组中读取消息。
     *
     * @param streamKey    Stream 的键名
     * @param groupName    消费者组名称
     * @param consumerName 消费者名称
     * @return 读取到的消息列表 (List of {@link MapRecord})，如果没有新消息则返回空列表。
     */
    public List<MapRecord<String, String, String>> readFromStreamGroup(String streamKey, String groupName, String consumerName) {
        log.debug("消费者 '{}' (属于组 '{}') 尝试从 Stream '{}' 读取消息...", consumerName, groupName, streamKey);
        // 创建或标识一个消费者
        Consumer consumer = Consumer.from(groupName, consumerName);
        try {
            // 使用 XREADGROUP 命令，从上次消费的位置 ('>') 读取
            // 注意：这里没有设置阻塞或 count，可以根据需要调整 StreamReadOptions
            List<MapRecord<String, String, String>> messages = streamOps.read(
                    consumer,
                    StreamOffset.create(streamKey, ReadOffset.lastConsumed()) // '>'
            );

            if (messages != null && !messages.isEmpty()) {
                log.info("消费者 '{}' 从 Stream '{}' (组 '{}') 读取到 {} 条消息。第一条消息 ID: {}",
                        consumerName, streamKey, groupName, messages.size(), messages.get(0).getId());
                // **重要**: 在实际应用中，处理完消息后需要调用 XACK 确认消息
                // 例如: messages.forEach(msg -> streamOps.acknowledge(streamKey, groupName, msg.getId()));
                return messages;
            }
            log.debug("消费者 '{}' 在 Stream '{}' (组 '{}') 中没有发现新消息。", consumerName, streamKey, groupName);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("消费者 '{}' (组 '{}') 从 Stream '{}' 读取消息时发生错误", consumerName, groupName, streamKey, e);
            return Collections.emptyList();
        }
    }
}