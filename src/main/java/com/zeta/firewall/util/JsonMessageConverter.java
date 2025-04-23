package com.zeta.firewall.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.zeta.firewall.model.dto.RedisCommandMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonMessageConverter {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        // Configure ObjectMapper
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true);
        // Add case insensitive enum handling
        objectMapper.configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, true);
//        objectMapper.configure(DeserializationFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true);
    }

    /**
     * 将JSON字符串转换为AgentMessage对象
     *
     * @param json JSON字符串
     * @param dataClass data数组中对象的类型
     * @param oldClass old对象的类型
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
}