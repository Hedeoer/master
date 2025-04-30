package com.zeta.firewall.event;

import org.springframework.context.ApplicationEvent;

public class PortRuleUpdateEvent extends ApplicationEvent {
    public PortRuleUpdateEvent(Object source) {
        super(source);
    }
}