package com.zeta.firewall.subscirbe;

import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

public class StreamProducer {

    private final StringRedisTemplate redisTemplate;
    private final String streamKey;
    public StreamProducer(StringRedisTemplate redisTemplate, String streamKey) {
        this.redisTemplate = redisTemplate;
        this.streamKey = streamKey;
    }
    /**
     * 向指定的 Redis Stream 发布一条消息，并设置流的最大长度约为 1000 条。
     * <p>
     * 消息以 Map 形式存储到 Redis Stream {@code streamKey} 中；
     * 当消息数超过 1000 条时，Stream 会自动进行近似裁剪（approximate trimming），以限制其最大长度，
     * 有利于节省内存并保持流的活跃状态。
     *
     * @param message 需发布到 Stream 的消息内容，键值对形式
     * @return 新添加消息的 RecordId (相当于Jedis中的StreamEntryID)
     */
    public RecordId publishMessage(Map<String, String> message) {
        StreamOperations<String, Object, Object> streamOps = redisTemplate.opsForStream();

        // 创建一个MapRecord
        MapRecord<String, String, String> record = StreamRecords.newRecord()
                .ofMap(message)
                .withStreamKey(streamKey);

        // 添加消息
        RecordId recordId = streamOps.add(record);

        // 裁剪流以保持最大长度约为1000
        streamOps.trim(streamKey, 1000, true);

        return recordId;
    }
}
