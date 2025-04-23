package com.zeta.firewall.util;

import com.zeta.firewall.model.dto.RedisCommandMessage;
import com.zeta.firewall.model.entity.PortRule;
import com.zeta.firewall.model.entity.SourceRule;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonMessageConverterTest {

    @Test
    void parseMessage_shouldParseValidJson() {
        // given
        String json = String.join("\n",
                "{",
                "   \"agent_id\": \"test-agent\",",
                "   \"agent_component_type\": \"firewall\",",
                "   \"data_op_type\": \"insert\",",
                "   \"request_params\": {",
                "       \"isUsing\": \"false\",",
                "       \"policy\": \"true\"",
                "   },",
                "   \"ts\": 1477053217,",
                "   \"primary_key_columns\": [\"port\", \"protocol\"],",
                "   \"data\": [",
                "       {",
                "           \"zone\": \"public\",",
                "           \"type\": \"PORT\",",
                "           \"permanent\": true,",
                "           \"family\": \"ipv4\",",
                "           \"port\": \"6379\",",
                "           \"protocol\": \"tcp\",",
                "           \"using\": true,",
                "           \"policy\": true,",
                "           \"sourceRule\": {",
                "               \"source\": \"All IPs allowed\"",
                "           },",
                "           \"descriptor\": \"Redis port\"",
                "       }",
                "   ],",
                "   \"old\": {",
                "       \"policy\": false",
                "   }",
                "}"
        );

        // when
        RedisCommandMessage<PortRule> message = JsonMessageConverter.parseMessage(
                json,
                PortRule.class
        );

        // then
        assertNotNull(message);
        assertEquals("test-agent", message.getAgentId());
        assertEquals(RedisCommandMessage.ComponentType.FIREWALL, message.getAgentComponentType());
        assertEquals(RedisCommandMessage.OperationType.INSERT, message.getDataOpType());
        assertEquals(1477053217L, message.getTs());

        // verify request_params
        Map<String, String> requestParams = message.getRequestParams();
        assertNotNull(requestParams);
        assertEquals("false", requestParams.get("isUsing"));
        assertEquals("true", requestParams.get("policy"));

        // verify primary_key_columns
        assertEquals(2, message.getPrimaryKeyColumns().size());
        assertTrue(message.getPrimaryKeyColumns().contains("port"));
        assertTrue(message.getPrimaryKeyColumns().contains("protocol"));

        // verify data array
        assertNotNull(message.getData());
        assertEquals(1, message.getData().size());
        PortRule portRule = message.getData().get(0);
        assertEquals("public", portRule.getZone());
        assertEquals("PORT", portRule.getType().toString());
        assertTrue(portRule.isPermanent());
        assertEquals("ipv4", portRule.getFamily());
        assertEquals("6379", portRule.getPort());
        assertEquals("tcp", portRule.getProtocol());
        assertTrue(portRule.getUsing());
        assertTrue(portRule.getPolicy());
        assertEquals("Redis port", portRule.getDescriptor());

        // verify sourceRule
        SourceRule sourceRule = portRule.getSourceRule();
        assertNotNull(sourceRule);
        assertEquals("All IPs allowed", sourceRule.getSource());

        // verify old object
        assertNotNull(message.getOld());
        assertFalse(message.getOld().getPolicy());
    }

    @Test
    void parseMessage_shouldHandleInvalidJson() {
        // given
        String invalidJson = "{ invalid json }";

        // when & then
        assertThrows(RuntimeException.class, () ->
                JsonMessageConverter.parseMessage(invalidJson, PortRule.class)
        );
    }

    @Test
    void parseMessage_shouldHandleNullValues() {
        // given
        String jsonWithNulls = String.join("\n",
                "{",
                "   \"agent_id\": null,",
                "   \"agent_component_type\": \"firewall\",",
                "   \"data_op_type\": null,",
                "   \"request_params\": null,",
                "   \"ts\": null,",
                "   \"primary_key_columns\": null,",
                "   \"data\": null,",
                "   \"old\": null",
                "}"
        );

        // when
        RedisCommandMessage<PortRule> message = JsonMessageConverter.parseMessage(
                jsonWithNulls,
                PortRule.class
        );

        // then
        assertNotNull(message);
        assertNull(message.getAgentId());
        assertEquals(RedisCommandMessage.ComponentType.FIREWALL, message.getAgentComponentType());
        assertEquals(RedisCommandMessage.OperationType.UNKNOWN, message.getDataOpType());
        assertNull(message.getRequestParams());
        assertNull(message.getTs());
        assertNull(message.getPrimaryKeyColumns());
        assertNull(message.getData());
        assertNull(message.getOld());
    }

    @Test
    void parseMessage_shouldHandleUnknownEnumValues() {
        // given
        String jsonWithUnknownEnum = String.join("\n",
                "{",
                "   \"agent_id\": \"test\",",
                "   \"agent_component_type\": \"UNKNOWN_TYPE\",",
                "   \"data_op_type\": \"UNKNOWN_OP\",",
                "   \"data\": [],",
                "   \"old\": null",
                "}"
        );

        // when
        RedisCommandMessage<PortRule> message = JsonMessageConverter.parseMessage(
                jsonWithUnknownEnum,
                PortRule.class
        );

        // then
        assertNotNull(message);
        assertEquals(RedisCommandMessage.ComponentType.UNKNOWN, message.getAgentComponentType());
        assertEquals(RedisCommandMessage.OperationType.UNKNOWN, message.getDataOpType());
    }
}