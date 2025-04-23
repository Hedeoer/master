package com.zeta.firewall.config;

import com.zeta.firewall.schedule.HeartBeatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
public class SchedulingConfig implements SchedulingConfigurer {
    private static final Logger logger = LoggerFactory.getLogger(HeartBeatService.class);

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);  // 线程池大小
        scheduler.setThreadNamePrefix("heartbeat-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);  // 优雅关闭
        scheduler.setAwaitTerminationSeconds(60);  // 关闭超时时间
        scheduler.setErrorHandler(throwable -> {
            // 自定义错误处理
            logger.error("Task execution failed", throwable);
        });
        scheduler.initialize();
        taskRegistrar.setTaskScheduler(scheduler);
    }
}