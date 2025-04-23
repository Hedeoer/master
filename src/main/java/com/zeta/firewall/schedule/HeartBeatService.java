package com.zeta.firewall.schedule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeta.firewall.model.entity.AgentNodeInfo;
import com.zeta.firewall.service.AgentNodeInfoService;
import com.zeta.firewall.util.DateTimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 周期性， 异步的检查 集群节点的心跳
 *
 */
@Service
public class HeartBeatService {
    private static final Logger logger = LoggerFactory.getLogger(HeartBeatService.class);
    private static final long FIXED_DELAY = 25000;
    private final ObjectMapper objectMapper = new ObjectMapper();
    // 统计某个节点连续离线次数
    private final ConcurrentHashMap<String, Integer> offlineCountMap = new ConcurrentHashMap<>();
    // 记录最后一次打印的状态（true 在线，false 离线）
    private final ConcurrentHashMap<String, Boolean> lastStateMap = new ConcurrentHashMap<>();
    // 离线检测到 OFFLINE_MAX 次后打印离线日志
    private static final int OFFLINE_MAX = 3;
    // 节点上报时间 早于 心跳检查时间 OFFLINE_THRESHOLD 秒，则判定为离线
    private static final long OFFLINE_THRESHOLD = 30; // 30秒
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private AgentNodeInfoService agentNodeInfoService;

    /**
     * master节点周期性地检查来自agent节点的心跳
     * 每10秒执行一次，但“上次任务执行完毕后再等10秒”才开始下次（适合任务耗时不确定且不能重叠)
     */
    @Scheduled(fixedDelay = FIXED_DELAY)
    public void heartBeatCheckPeriod() throws JsonProcessingException {
        // 通过读取redis中名为 heartbeats 的hash表数据来检测agent节点的存活状态
        // 将所有汇报心跳的agentId数据持久化
        // 如何区分agent节点心跳是否是首次？如果是首次，需要持久化agent节点信息;不是首次，需要判断agent节点是否离线？
        //     通过对比redis服务器时间戳和心跳汇报的时间戳间隔，比如超过30秒，表示agent节点离线
        Map<Object, Object> heartBeats = stringRedisTemplate.opsForHash().entries("firewall:heartbeats");

        for (Map.Entry<Object, Object> heartBeat : heartBeats.entrySet()) {
            String agentId = (String) heartBeat.getKey();
            String value = (String) heartBeat.getValue();
            AgentNodeInfo agentNodeInfo = new AgentNodeInfo();
            try {
                // 尝试解析JSON，如果失败则跳过该条数据
                agentNodeInfo = objectMapper.readValue(value, AgentNodeInfo.class);
            } catch (Exception e) {
                logger.error("Invalid JSON data for agent {}, value: {}, skipping...", agentId, value);
                continue;
            }


            Long reportTimeStamp = Long.parseLong(agentNodeInfo.getHeartbeatTimestamp());
            Boolean firstReportFlag = agentNodeInfo.getIsFirstHeartbeat();
            Long currentRedisServerTime = getRedisServerTime();

            boolean isAlive = currentRedisServerTime >= reportTimeStamp &&
                    Math.abs(currentRedisServerTime - reportTimeStamp) <= OFFLINE_THRESHOLD;

            // 将秒级时间戳转化为 yyyy-MM-dd HH:mm:ss格式字符串
            String heartbeatTimestamp = DateTimeUtil.timestampSecToString(reportTimeStamp);
            agentNodeInfo.setHeartbeatTimestamp(heartbeatTimestamp);

            if (firstReportFlag) {
                logger.info("agent节点: {} 首次注册，对agent节点信息进行持久化", agentId);
                agentNodeInfoService.saveOrUpdate(agentNodeInfo);
                // 注册节点为在线
                offlineCountMap.put(agentId, 0);
                lastStateMap.put(agentId, true);
                // 更新Redis中的心跳数据，将isFirstHeartbeat设置为false
                agentNodeInfo.setIsFirstHeartbeat(false);
                String updatedValue = objectMapper.writeValueAsString(agentNodeInfo);
                stringRedisTemplate.opsForHash().put("heartbeats", agentId, updatedValue);
                continue;
            }

            if (isAlive) {
                // 节点在线
                offlineCountMap.put(agentId, 0); // 离线计数清零
                if (!Boolean.TRUE.equals(lastStateMap.get(agentId))) {
                    logger.info("节点:{} 在线，当前检测集群节点心跳时间间隔: {} 毫秒", agentId, FIXED_DELAY);
                    // 数据库状态更新为在线
                    agentNodeInfo.setIsActive(true);
                    agentNodeInfoService.updateById(agentNodeInfo);
                    lastStateMap.put(agentId, true);
                }
            } else {
                // 节点被判定为离线
                int count = offlineCountMap.getOrDefault(agentId, 0) + 1;
                offlineCountMap.put(agentId, count);
                // 只在统计到第3次时打印一次离线
                if (count == OFFLINE_MAX && !Boolean.FALSE.equals(lastStateMap.get(agentId))) {
                    logger.error("节点:{} 离线，当前检测集群节点心跳时间间隔: {} 毫秒, 当前已经检测 {} 次，节点均为离线状态！", agentId, FIXED_DELAY,OFFLINE_MAX);
                    // 数据库状态更新为离线
                    agentNodeInfo.setIsActive(false);
                    agentNodeInfoService.updateById(agentNodeInfo);
                    lastStateMap.put(agentId, false);
                }
            }
        }
    }

    /**
     * 获取 Redis 服务器当前 Unix 时间戳（秒级，等价于 TIME 命令返回的第一个元素）
     * @return long 当前时间戳（秒）
     */
    private Long getRedisServerTime() {
        // 执行TIME原生命令
        return  stringRedisTemplate.execute((RedisConnection connection) -> connection.time()) / 1000;
    }
}
