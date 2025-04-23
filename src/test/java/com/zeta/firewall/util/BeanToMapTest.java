package com.zeta.firewall.util;

import com.zeta.firewall.model.dto.RedisCommandMessage;
import com.zeta.firewall.model.entity.PortRule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BeanToMapTest {

    @Test
    void beanToMap_shouldConvertMessageToMap() {
        // given
        RedisCommandMessage<PortRule> message = new RedisCommandMessage<>();
        message.setAgentId("test-agent");
        message.setAgentComponentType(RedisCommandMessage.ComponentType.FIREWALL);
        message.setDataOpType(RedisCommandMessage.OperationType.INSERT);
        
        Map<String, String> requestParams = new HashMap<>();
        requestParams.put("isUsing", "false");
        requestParams.put("policy", "true");
        message.setRequestParams(requestParams);
        
        message.setTs(1477053217L);
        
        List<String> primaryKeyColumns = new ArrayList<>();
        primaryKeyColumns.add("port");
        primaryKeyColumns.add("protocol");
        message.setPrimaryKeyColumns(primaryKeyColumns);

        // when
        Map<String, String> result = JsonMessageConverter.beanToMap(message);

        // then
        assertNotNull(result);
        assertEquals("test-agent", result.get("agent_id"));
        assertEquals("FIREWALL", result.get("agent_component_type"));
        assertEquals("INSERT", result.get("data_op_type"));
        assertEquals("1477053217", result.get("ts"));
        assertEquals("[\"port\",\"protocol\"]", result.get("primary_key_columns"));
        
        // Request params should be serialized as a JSON string
        assertTrue(result.get("request_params").contains("\"isUsing\":\"false\""));
        assertTrue(result.get("request_params").contains("\"policy\":\"true\""));
    }

    @Test
    void beanToMap_shouldHandleNullValues() {
        // given
        RedisCommandMessage<PortRule> message = new RedisCommandMessage<>();
        // All fields are null

        // when
        Map<String, String> result = JsonMessageConverter.beanToMap(message);

        // then
        assertNotNull(result);
        assertNull(result.get("agent_id"));
        assertNull(result.get("agent_component_type"));
        assertNull(result.get("data_op_type"));
        assertNull(result.get("ts"));
        assertNull(result.get("primary_key_columns"));
        assertNull(result.get("request_params"));
        assertNull(result.get("data"));
        assertNull(result.get("old"));
    }
}
