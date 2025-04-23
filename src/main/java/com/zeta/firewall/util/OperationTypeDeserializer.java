package com.zeta.firewall.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.zeta.firewall.model.dto.RedisCommandMessage;

import java.io.IOException;

/**
 * Custom deserializer for OperationType enum to handle case-insensitive values
 */
public class OperationTypeDeserializer extends JsonDeserializer<RedisCommandMessage.OperationType> {
    @Override
    public RedisCommandMessage.OperationType deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();
        if (value == null || value.isEmpty()) {
            return RedisCommandMessage.OperationType.UNKNOWN;
        }
        
        // Case-insensitive comparison
        for (RedisCommandMessage.OperationType type : RedisCommandMessage.OperationType.values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        
        return RedisCommandMessage.OperationType.UNKNOWN;
    }
}
