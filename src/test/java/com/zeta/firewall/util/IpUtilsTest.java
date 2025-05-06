package com.zeta.firewall.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class IpUtilsTest {

    @DisplayName("Test isMappingIpType with various IP addresses and types")
    @ParameterizedTest
    @CsvSource({
            // Valid IPv4 addresses with ipv4 type
            "192.168.1.1, ipv4, true",
            "10.0.0.1, ipv4, true",
            "172.16.0.1, ipv4, true",
            "127.0.0.1, ipv4, true",
            "0.0.0.0, ipv4, true",
            "255.255.255.255, ipv4, true",

            // Valid IPv4 CIDR with ipv4 type
            "192.168.1.0/24, ipv4, true",
            "10.0.0.0/8, ipv4, true",

            // Valid IPv6 addresses with ipv6 type
            "2001:db8::1, ipv6, true",
            "::1, ipv6, true",
            "fe80::1, ipv6, true",
            "2001:0db8:85a3:0000:0000:8a2e:0370:7334, ipv6, true",

            // Valid IPv6 CIDR with ipv6 type
            "2001:db8::/32, ipv6, true",
            "fe80::/64, ipv6, true",

            // IPv4 addresses with ipv6 type (should return false)
            "192.168.1.1, ipv6, false",
            "10.0.0.1, ipv6, false",

            // IPv6 addresses with ipv4 type (should return false)
            "2001:db8::1, ipv4, false",
            "::1, ipv4, false",

            // Invalid IP addresses
            ", ipv4, false",
            "null, ipv4, false",
            "invalid-ip, ipv4, false",
            "999.999.999.999, ipv4, false",
            "192.168.1, ipv4, true",

            // Invalid IP type
            "192.168.1.1, invalid, false",
            "2001:db8::1, invalid, false"
    })
    void isMappingIpType(String ip, String ipType, boolean expected) {
        // When ip is null, the test framework passes "null" as a string
        String actualIp = "null".equals(ip) ? null : ip;

        // Act
        boolean result = IpUtils.isMappingIpType(actualIp, ipType);

        // Assert
        assertEquals(expected, result,
                String.format("IP: %s with type: %s should return: %s", actualIp, ipType, expected));
    }

    @Test
    @DisplayName("Test isMappingIpType with null IP address")
    void isMappingIpType_withNullIp() {
        // Arrange
        String ip = null;
        String ipType = "ipv4";

        // Act
        boolean result = IpUtils.isMappingIpType(ip, ipType);

        // Assert
        assertFalse(result, "Null IP address should return false");
    }

    @Test
    @DisplayName("Test isMappingIpType with empty IP address")
    void isMappingIpType_withEmptyIp() {
        // Arrange
        String ip = "";
        String ipType = "ipv4";

        // Act
        boolean result = IpUtils.isMappingIpType(ip, ipType);

        // Assert
        assertFalse(result, "Empty IP address should return false");
    }

    @Test
    @DisplayName("Test isMappingIpType with null offerIpType")
    void isMappingIpType_withNullOfferIpType() {
        // Arrange
        String ip = "192.168.1.1";
        String ipType = null;

        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            IpUtils.isMappingIpType(ip, ipType);
        }, "Null offerIpType should throw NullPointerException");
    }
}