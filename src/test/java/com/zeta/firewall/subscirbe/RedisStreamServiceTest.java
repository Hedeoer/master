package com.zeta.firewall.subscirbe;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RedisStreamServiceTest {

    @Autowired
    private RedisStreamService redisStreamService;
    @Test
    void getAllStreamKeys() {

        redisStreamService.getAllStreamKeys().forEach(System.out::println);
    }

    @Test
    void getPrefixedStreamKeys() {
        System.out.println(redisStreamService.getStreamInfo("pub:1w6CEh6BHvPB8WNs3gNpZo:portRule"));
        System.out.println(redisStreamService.getStreamGroupInfo("pub:1w6CEh6BHvPB8WNs3gNpZo:portRule"));
        System.out.println(redisStreamService.getStreamConsumerInfo("pub:1w6CEh6BHvPB8WNs3gNpZo:portRule", "firewall_sub:1w6CEh6BHvPB8WNs3gNpZo:portRule_group"));
    }
}