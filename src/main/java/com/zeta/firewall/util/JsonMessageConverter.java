package com.zeta.firewall.util;

import cn.hutool.core.date.DatePattern;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.zeta.firewall.model.dto.RedisCommandMessage;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class JsonMessageConverter {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        // 配置ObjectMapper
        // 禁用将日期写为时间戳
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, false);
        objectMapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true);
        objectMapper.configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false);

        // 配置Java 8日期时间模块
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DatePattern.NORM_DATETIME_FORMATTER));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DatePattern.NORM_DATETIME_FORMATTER));

        // 注册模块
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(javaTimeModule);
    }

    /**
     * 将JSON字符串转换为AgentMessage对象
     *
     * @param json JSON字符串
     * @param dataClass data数组中对象的类型
     * @return AgentMessage对象
     */
    public static <T> RedisCommandMessage<T> parseMessage(
            String json,
            Class<T> dataClass
    ) {
        try {
            // 创建类型引用，用于处理泛型
            JavaType type = objectMapper.getTypeFactory().constructParametricType(
                    RedisCommandMessage.class,
                    dataClass
            );

            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.error("Failed to parse JSON message: {}", e.getMessage());
            throw new RuntimeException("JSON parsing failed", e);
        }
    }

    /**
     * 将AgentMessage对象转换为JSON字符串
     *
     * @param message AgentMessage对象
     * @return JSON字符串
     */
    public static <T> String toJsonString(RedisCommandMessage<T> message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Failed to convert message to JSON: {}", e.getMessage());
            throw new RuntimeException("JSON conversion failed", e);
        }
    }

    /**
     * 将RedisCommandMessage对象转化为map<String,String>
     * 将复杂对象序列化为JSON字符串
     *
     * 传递固定消息格式：当redis 发布更新命令时，需要的将如下的json按照各个属性转化为map然后发送
     * {
     *    "agentId": "test",
     *    "agentComponentType": "firewall",
     *    "dataOpType": "update",
     *    "requestParams": "",
     *    "ts": 1477053217,
     *    "primaryKeyColumns": ["port", "protocol"],
     *    "data": [
     *       {
     *          "zone": "public",
     *          "type": "PORT",
     *          "permanent": true,
     *          "family": "ipv4",
     *          "port": "6379",
     *          "protocol": "udp",
     *          "using": true,
     *          "policy": true,
     *          "sourceRule": {"source": "0.0.0.0"},
     *          "descriptor": "All IPs allowed"
     *       }
     *    ],
     *    "old": {
     *       "zone": "public",
     *       "type": "PORT",
     *       "permanent": true,
     *       "family": "ipv4",
     *       "port": "6379",
     *       "protocol": "tcp",
     *       "using": true,
     *       "policy": false,
     *       "sourceRule": {"source": "0.0.0.0"},
     *       "descriptor": "All IPs allowed"
     *    }
     * }
     *
     * @param message RedisCommandMessage对象
     * @return 字符串键值对的Map
     */
    public static <T> Map<String, String> beanToMap(RedisCommandMessage<T> message) {
        if (message == null) {
            return Collections.emptyMap();
        }

        HashMap<String, String> map = new HashMap<>();

        // 添加基本字段
        if (message.getAgentId() != null) {
            map.put("agentId", message.getAgentId());
        }

        if (message.getAgentComponentType() != null) {
            map.put("agentComponentType", message.getAgentComponentType().name());
        }

        if (message.getDataOpType() != null) {
            // 将枚举类型转换为字符串 QUERY ==》 query
            map.put("dataOpType", message.getDataOpType().name());
        }

        if (message.getTs() != null) {
            map.put("ts", message.getTs().toString());
        }

        // 复杂对象转为JSON字符串
        try {
            if (message.getRequestParams() != null) {
                map.put("requestParams", objectMapper.writeValueAsString(message.getRequestParams()));
            }

            if (message.getPrimaryKeyColumns() != null) {
                map.put("primaryKeyColumns", objectMapper.writeValueAsString(message.getPrimaryKeyColumns()));
            }

            if (message.getData() != null) {
                map.put("data", objectMapper.writeValueAsString(message.getData()));
            }

            if (message.getOld() != null) {
                map.put("old", objectMapper.writeValueAsString(message.getOld()));
            }
        } catch (Exception e) {
            log.error("Failed to convert complex object to JSON string: {}", e.getMessage());
            throw new RuntimeException("Bean to map conversion failed", e);
        }

        return map;
    }
}