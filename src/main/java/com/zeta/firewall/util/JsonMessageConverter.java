package com.zeta.firewall.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.zeta.firewall.model.dto.RedisCommandMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.formula.functions.T;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class JsonMessageConverter {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        // Configure ObjectMapper
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 修改枚举处理配置
        objectMapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, false);
        objectMapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true);
        objectMapper.configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, false);
        // 添加对象处理配置
        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false);
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
            map.put("agent_id", message.getAgentId());
        }

        if (message.getAgentComponentType() != null) {
            map.put("agent_component_type", message.getAgentComponentType().name());
        }

        if (message.getDataOpType() != null) {
            map.put("data_op_type", message.getDataOpType().name());
        }

        if (message.getTs() != null) {
            map.put("ts", message.getTs().toString());
        }

        // 复杂对象转为JSON字符串
        try {
            if (message.getRequestParams() != null) {
                map.put("request_params", objectMapper.writeValueAsString(message.getRequestParams()));
            }

            if (message.getPrimaryKeyColumns() != null) {
                map.put("primary_key_columns", objectMapper.writeValueAsString(message.getPrimaryKeyColumns()));
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