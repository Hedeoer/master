package com.zeta.firewall.subscirbe;// 你可以称之为 StreamConsumerRunner 或 StreamListenerRunner

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 使用 @PostConstruct 启动新守护线程，不断消费指定的 redis stream
 */
@Service
public class StreamConsumeRunner {

    private static final Logger log = LoggerFactory.getLogger(StreamConsumeRunner.class);

    @Autowired
    private RedisConnectionFactory connectionFactory;

    private StreamConsumer streamConsumer;

    // todo 在controller调用
//    @PostConstruct
    public void startConsume() {
        // todo 推荐关键配置写成常量/配置文件
        String streamKey = "001:pub";
        String groupName = "spring-group";
        String consumerName = "spring-consumer";

        streamConsumer = new StreamConsumer(connectionFactory, streamKey, groupName, consumerName);

        // 建议新线程异步消费，避免阻塞主线程
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    // 非阻塞读取（0表示无限阻塞直到有消息）
                    List<MapRecord<String, Object, Object>> records = streamConsumer.consumeNewMessages(1, 2000);

                    if(records.isEmpty()) {continue;}

                    for (MapRecord<String, Object, Object> record : records) {
                        // 处理消息
                        log.info("收到:{}", record.getValue());
                        // ack
                        streamConsumer.acknowledgeMessage(record.getId());
                    }
                } catch (Exception ex) {
                    ex.printStackTrace(); // 日志记录
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                }
            }
        });
        t.setDaemon(true); // 后台线程，主程序停就自动退出
        t.start();
    }
}