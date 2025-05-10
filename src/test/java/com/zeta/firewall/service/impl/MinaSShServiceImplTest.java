package com.zeta.firewall.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
class MinaSShServiceImplTest {

    @Test
    void checkAgentRunningStatus() {
        MinaSShServiceImpl impl = new MinaSShServiceImpl();
        System.out.println(impl.checkAgentRunningStatus("hedeoer", "vm100", 2222, false));
    }
}