package com.zeta.firewall.schedule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeta.firewall.dao.PortRuleMapper;
import com.zeta.firewall.event.PortRuleUpdateEvent;
import com.zeta.firewall.model.entity.FirewallPortRuleInfo;
import com.zeta.firewall.model.entity.PortInfo;
import com.zeta.firewall.model.entity.PortRule;
import com.zeta.firewall.service.FirewallPortRuleInfoService;
import com.zeta.firewall.service.PortInfoService;
import com.zeta.firewall.util.PortRuleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zetaframework.core.utils.JSONUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 端口使用信息定时拉取服务
 */
@Service
public class PortInfoPullService {

    private static final Logger logger = LoggerFactory.getLogger(PortInfoPullService.class);
    private static final long FIXED_DELAY = 60000;
    private static final String portInfoHashTableName = "firewall:portInfo";
    private final ObjectMapper objectMapper = JSONUtil.getObjectMapper();


    // 使用 CopyOnWriteArrayList 保存上一次最新端口信息
    private final List<PortInfo> lastAllAgentsLastedPortInfos = new CopyOnWriteArrayList<>();

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private final PortInfoService portInfoService;
    private final PortRuleMapper portRuleMapper;
    private final FirewallPortRuleInfoService firewallPortRuleInfoService;

    public PortInfoPullService(PortInfoService portInfoService, PortRuleMapper portRuleMapper, FirewallPortRuleInfoService firewallPortRuleInfoService) {
        this.portInfoService = portInfoService;
        this.portRuleMapper = portRuleMapper;
        this.firewallPortRuleInfoService = firewallPortRuleInfoService;
    }

    /**
     * 获取redis中的端口使用信息格式
     * {"ts":"1745900398","reportPortInfos":[
     *   {
     *     "agentId":"1RrFJTC3tXpli0epSySUPN",
     *     "protocol":"tcp",
     *     "portNumber":63342,
     *     "processName":"java",
     *     "processId":3218,
     *     "commandLine":"java Main",
     *     "listenAddress":"0000:0000:0000:0000:0000:ffff:7f00:0001"
     *   },
     *   {
     *     "agentId":"1RrFJTC3tXpli0epSySUPN",
     *     "protocol":"tcp",
     *     "portNumber":9999,
     *     "processName":"nc",
     *     "processId":65175,
     *     "commandLine":"nc 9999",
     *     "listenAddress":"0.0.0.0"
     *   }
     * ]}     */
    /**
     * 定时从 Redis 中拉取端口信息并更新数据库
     */

    /**
     * 定时拉取各节点（Agent）最新端口使用信息，并同步至数据库。
     *
     * 方法逻辑说明：
     * <ol>
     *     <li>从 Redis Hash（key: firewall:portInfo）中读取所有节点当前上报的端口数据。</li>
     *     <li>解析每个节点的数据，汇总成全量端口信息列表。</li>
     *     <li>将本次汇总结果与上一次同步的端口信息进行内容比对（忽略时间字段，仅关注关键端口特征字段）。</li>
     *     <li>如有变化（即任意节点端口状态发生变更），将全部最新端口信息批量写入数据库。</li>
     *     <li>删除数据库中与当前列表中记录不匹配的端口信息。</li>
     *     <li>更新端口规则和端口使用映射关系。</li>
     *     <li>同步（更新）本地缓存的最新端口集合，用于下次比对。</li>
     * </ol>
     *
     * 主要作用：
     * <ul>
     *     <li>高效检测分布式节点实际端口使用情况的变化，及时反映至中心库。</li>
     *     <li>避免每次无变化都批量写库，节省数据库压力与资源。</li>
     * </ul>
     *
     * 其他说明：
     * <ul>
     *     <li>端口信息比对仅涉及关键字段（如 agentId、protocol、portNumber、processName、processId、commandLine、listenAddress）。</li>
     *     <li>方法包含异常捕获和详细日志，异常节点数据不会影响整体流程。</li>
     * </ul>
     *
     * 注意事项：
     * <ul>
     *     <li>适合中等规模节点场景，如节点数极多建议分批或分节点维护 last 状态。</li>
     *     <li>如需按单个 agent 粒度判断变更，可在 parse 和比对环节拆分实现。</li>
     * </ul>
     */
    @Scheduled(fixedDelay = FIXED_DELAY)
    @Transactional(rollbackFor = Exception.class)
    public Boolean pullPortInfo() {

        Boolean success = Boolean.TRUE;

        Map<String, String> rawMap = stringRedisTemplate.<String, String>opsForHash().entries(portInfoHashTableName);
        List<PortInfo> allCurrentPortInfos = parseAllPortInfos(rawMap);

        boolean hasChange = !portInfosAreEqual(lastAllAgentsLastedPortInfos, allCurrentPortInfos);
        if (!hasChange) return success;

        // 数据库中全部端口信息
        List<PortInfo> currentAllPortInfoFromDB = portInfoService.queryAllPortInfosDB();

        try {
            logger.info("端口信息有变化，开始更新数据库");

            // 找出不再使用的端口信息（在数据库中存在，但在当前列表中不存在）
            List<PortInfo> notInUsePortInfos = findNotInUsePortInfos(currentAllPortInfoFromDB, allCurrentPortInfos);
            if (!notInUsePortInfos.isEmpty()) {
                logger.info("发现 {} 个端口不再使用，将从数据库中删除", notInUsePortInfos.size());
                Boolean removePortInfosNotInUse = portInfoService.removePortInfosNotInUse(notInUsePortInfos);
                if (!removePortInfosNotInUse) {
                    logger.info("删除端口信息失败，当前间隔：{} 毫秒", FIXED_DELAY);
                    success =  Boolean.FALSE;
                }
            }

            // 插入或更新当前端口信息
            Boolean insertOrUpdateBatch = portInfoService.insertOrUpdateBatchPortInfos(allCurrentPortInfos);

            // 全部的端口规则
            List<PortRule> currentAllPortRulesFromDB = portRuleMapper.selectList(null);

            // 寻找 端口规则和端口使用信息的映射关系
            Map<String, List<PortInfo>> mappings = PortRuleUtils.connectPortInfosWithPortRules(currentAllPortRulesFromDB, allCurrentPortInfos);
            ArrayList<FirewallPortRuleInfo> mappingsList = new ArrayList<>();
            for (Map.Entry<String, List<PortInfo>> mapping : mappings.entrySet()) {
                List<PortInfo> portInfoList = mapping.getValue();
                for (PortInfo portInfo : portInfoList) {
                    mappingsList.add(FirewallPortRuleInfo
                            .builder()
                            .ruleId(Long.parseLong(mapping.getKey()))
                            .infoId(portInfo.getId())
                            .build());
                }
            }


            // 删除端口规则和端口使用映射关系（先全部删除，再全量插入）
            Boolean allRemove = firewallPortRuleInfoService.removeAll();
            Boolean addAll = firewallPortRuleInfoService.addOrUpdateAll(mappingsList);

            if (insertOrUpdateBatch && allRemove && addAll) {
                synchronized (lastAllAgentsLastedPortInfos) {
                    lastAllAgentsLastedPortInfos.clear();
                    lastAllAgentsLastedPortInfos.addAll(allCurrentPortInfos);
                }
                logger.info("端口信息更新成功，当前间隔：{} 毫秒", FIXED_DELAY);
            } else {
                success = Boolean.FALSE;
                logger.error("定时拉取端口信息更新失败, 当前间隔：{} 毫秒", FIXED_DELAY);
            }
        } catch (Exception e) {
            success = Boolean.FALSE;
            logger.error("端口信息更新异常, 当前间隔：{} 毫秒", FIXED_DELAY, e);
        }
        return success;
    }

    /** 将redis中节点所有json解码，合成全端口info列表 */
    private List<PortInfo> parseAllPortInfos(Map<String, String> agentsLastedPortInfos) {
        List<PortInfo> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : agentsLastedPortInfos.entrySet()) {
            try {
                JsonNode node = objectMapper.readTree(entry.getValue());
                List<PortInfo> portInfos  = objectMapper.convertValue(node.get("reportPortInfos"), new TypeReference<List<PortInfo>>() {});
                result.addAll(portInfos);
            } catch (JsonProcessingException e) {
                logger.error("解析端口信息失败: {}", entry.getKey(), e);
            }
        }
        return result;
    }

    /** 比较两版端口列表是否一致，按“关键字段去重后内容一致”为准 */
    private boolean portInfosAreEqual(List<PortInfo> list1, List<PortInfo> list2) {
        if (list1.size() != list2.size()) return false;
        Set<String> set1 = list1.stream().map(this::portInfoKey).collect(Collectors.toSet());
        Set<String> set2 = list2.stream().map(this::portInfoKey).collect(Collectors.toSet());
        return set1.equals(set2);
    }

    /** 关键字段唯一key（可把字段组合成字符串，作为内容变化判断） */
    private String portInfoKey(PortInfo p) {
        return p.getAgentId() + ":" +
                p.getProtocol() + ":" +
                p.getPortNumber() + ":" +
                p.getProcessName() + ":" +
                p.getProcessId() + ":" +
                p.getCommandLine() + ":" +
                p.getListenAddress();
    }

    /**
     * 找出不再使用的端口信息（在上次列表中存在，但在当前列表中不存在）
     *
     * @param lastPortInfos 上次的端口信息列表
     * @param currentPortInfos 当前的端口信息列表
     * @return 不再使用的端口信息列表
     */
    private List<PortInfo> findNotInUsePortInfos(List<PortInfo> lastPortInfos, List<PortInfo> currentPortInfos) {
        // 将当前端口信息按照关键字段组合进行映射
        Set<String> currentPortKeys = currentPortInfos.stream()
                .map(p -> p.getAgentId() + ":" + p.getProtocol() + ":" + p.getPortNumber())
                .collect(Collectors.toSet());

        // 找出在上次列表中存在，但在当前列表中不存在的端口信息
        return lastPortInfos.stream()
                .filter(p -> !currentPortKeys.contains(p.getAgentId() + ":" + p.getProtocol() + ":" + p.getPortNumber()))
                .collect(Collectors.toList());
    }

    /**
     * 监听端口规则更新事件
     * 当端口规则发生变化时，立即触发一次端口信息拉取
     */
    @EventListener
    public void handlePortRuleUpdate(PortRuleUpdateEvent event) {
        logger.info("收到端口规则更新事件，触发端口信息拉取");
        this.pullPortInfo();
    }
}
