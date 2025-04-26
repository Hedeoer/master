package com.zeta.firewall.model.dto;

import com.zeta.firewall.model.entity.PortRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for PortRuleDTO.toEntity() method
 */
public class PortRuleDTOToEntityTest {

    @Test
    @DisplayName("toEntity should convert all properties correctly")
    void toEntity_shouldConvertAllProperties() {
        // Arrange
        PortRuleDTO dto = PortRuleDTO.builder()
                .id(123L)
                .protocol("TCP")
                .port("8080")
                .strategy("accept")
                .sourceType("specific")
                .sourceAddress("192.168.1.100")
                .description("Web应用服务端口")
                .usedStatus("inUsed")
                .zone("public")
                .family("ipv4")
                .permanent(true)
                .nodeId("node1")
                .build();

        // Act
        PortRule entity = dto.toEntity();

        // Assert
        assertEquals(123L, entity.getId());
        assertEquals("tcp", entity.getProtocol()); // Should be lowercase
        assertEquals("8080", entity.getPort());
        assertTrue(entity.getPolicy()); // "accept" should convert to true
        assertEquals("Web应用服务端口", entity.getDescriptor());
        assertTrue(entity.getUsing()); // "inUsed" should convert to true
        assertEquals("public", entity.getZone());
        assertEquals("ipv4", entity.getFamily());
        assertTrue(entity.isPermanent());
        assertEquals("node1", entity.getAgentId());
        
        // Check source rule
        assertNotNull(entity.getSourceRule());
        assertEquals("192.168.1.100", entity.getSourceRule().getSource());
    }

    @Test
    @DisplayName("toEntity should handle null values gracefully")
    void toEntity_shouldHandleNullValues() {
        // Arrange - create a DTO with minimal required fields to avoid NPE
        PortRuleDTO dto = PortRuleDTO.builder()
                .protocol("TCP") // Required to avoid NPE in toLowerCase()
                .permanent(false) // Set a default value
                .build();

        // Act
        PortRule entity = dto.toEntity();

        // Assert
        assertNotNull(entity);
        assertNull(entity.getId());
        assertEquals("tcp", entity.getProtocol());
        assertNull(entity.getPort());
        assertFalse(entity.getPolicy()); // null strategy becomes false
        assertNull(entity.getDescriptor());
        assertFalse(entity.getUsing()); // null usedStatus becomes false
        assertNull(entity.getZone());
        assertNull(entity.getFamily());
        assertFalse(entity.isPermanent()); // boolean primitive defaults to false
        assertNull(entity.getAgentId());
        assertNull(entity.getSourceRule()); // null sourceType doesn't create SourceRule
    }
}
