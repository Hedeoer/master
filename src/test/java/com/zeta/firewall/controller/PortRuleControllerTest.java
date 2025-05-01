package com.zeta.firewall.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeta.firewall.model.dto.PortRuleDTO;
import com.zeta.firewall.model.entity.PortRule;
import com.zeta.firewall.model.enums.RuleType;
import com.zeta.firewall.service.PortRuleService;
import com.zeta.firewall.service.StreamResponseService;
import com.zeta.firewall.subscirbe.StreamProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PortRuleController.class, excludeAutoConfiguration = {RedisAutoConfiguration.class})
public class PortRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortRuleService portRuleService;

    @MockBean
    private StreamProducer streamProducer;

    @MockBean
    private StreamResponseService streamResponseService;

    @Captor
    private ArgumentCaptor<Map<String, String>> messageCaptor;

    @Captor
    private ArgumentCaptor<List<PortRule>> portRulesCaptor;

    private ObjectMapper objectMapper = new ObjectMapper();
    private PortRuleDTO portRuleDTO;
    private RecordId recordId;
    private Map<Object, Object> successResponse;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        portRuleDTO = PortRuleDTO.builder()
                .protocol("tcp")
                .port("8080")
                .strategy("accept")
                .sourceType("specific")
                .sourceAddress("192.168.1.100")
                .description("Web应用服务端口")
                .zone("public")
                .family("ipv4")
                .permanent(true)
                .build();

        // 模拟Redis Stream的响应
        recordId = RecordId.of("0-1");

        successResponse = new HashMap<>();
        successResponse.put("status", "200");
        successResponse.put("message", "Success");
        successResponse.put("data", "{\"id\":123}");

        // 配置Mock行为
        when(streamProducer.publishMessage(anyString(), any())).thenReturn(recordId);
        when(streamResponseService.getResponseEntry(anyString(), anyString(), "",any())).thenReturn(successResponse);
    }

    @Test
    void addPortRule_shouldReturnSuccess() throws Exception {
        // 执行请求
        mockMvc.perform(post("/api/agents/firewall/port-rules/node1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(portRuleDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("端口规则添加成功"));

        // 验证服务调用
        verify(streamProducer).publishMessage(eq("pub:node1"), messageCaptor.capture());
        verify(streamResponseService).getResponseEntry(eq("node1"), eq("sub:node1"),"", eq(recordId));
        verify(portRuleService).saveOrUpdatePortRules(portRulesCaptor.capture());

        // 验证消息内容
        Map<String, String> capturedMessage = messageCaptor.getValue();
        assertEquals("FIREWALL", capturedMessage.get("agent_component_type"));
        assertEquals("INSERT", capturedMessage.get("data_op_type"));

        // 验证保存的端口规则
        List<PortRule> capturedRules = portRulesCaptor.getValue();
        assertEquals(1, capturedRules.size());
        PortRule rule = capturedRules.get(0);
        assertEquals("node1", rule.getAgentId());
        assertEquals(RuleType.PORT, rule.getType());
        assertEquals("8080", rule.getPort());
        assertEquals("tcp", rule.getProtocol());
        assertEquals(true, rule.getPolicy());
    }

    @Test
    void addPortRule_shouldReturnFailWhenAgentReturnsError() throws Exception {
        // 模拟失败响应
        Map<Object, Object> failureResponse = new HashMap<>();
        failureResponse.put("status", "400");
        failureResponse.put("message", "Invalid port rule");

        when(streamResponseService.getResponseEntry(anyString(), anyString(),"", any())).thenReturn(failureResponse);

        // 执行请求
        mockMvc.perform(post("/api/agents/firewall/port-rules/node1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(portRuleDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("添加端口规则失败: Invalid port rule"));

        // 验证不会保存到数据库
        verify(portRuleService, never()).saveOrUpdatePortRules(any());
    }
}
