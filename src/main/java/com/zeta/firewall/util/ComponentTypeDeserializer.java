package com.zeta.firewall.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.zeta.firewall.model.dto.RedisCommandMessage;

import java.io.IOException;

/**
 * Custom deserializer for ComponentType enum to handle case-insensitive values
 */
public class ComponentTypeDeserializer extends JsonDeserializer<RedisCommandMessage.ComponentType> {
    @Override
    public RedisCommandMessage.ComponentType deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();
        if (value == null || value.isEmpty()) {
            return RedisCommandMessage.ComponentType.UNKNOWN;
        }
        
        // Case-insensitive comparison
        for (RedisCommandMessage.ComponentType type : RedisCommandMessage.ComponentType.values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        
        return RedisCommandMessage.ComponentType.UNKNOWN;
    }
}
