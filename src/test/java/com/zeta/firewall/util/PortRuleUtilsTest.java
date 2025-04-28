package com.zeta.firewall.util;


import com.zeta.firewall.model.entity.PortInfo;
import com.zeta.firewall.model.entity.PortRule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class PortRuleUtilsTest {

    @Test
    public void expandAndDeduplicatePortRules() {

        PortRule portRule1 = PortRule
                .builder()
                .port("1000-1004")
                .protocol("tcp/udp")
                .build();

        PortRule portRule2 = PortRule
                .builder()
                .port("1003-1005")
                .protocol("tcp/udp")
                .build();

        ArrayList<PortRule> portRules = new ArrayList<>();
        portRules.add(portRule1);
        portRules.add(portRule2);

        List<PortInfo> expandedPortRules = PortRuleUtils.expandAndDeduplicatePortRules(portRules);
        expandedPortRules.forEach(System.out::println);

    }
}