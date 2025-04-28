package com.zeta.firewall.util;

import com.zeta.firewall.model.entity.PortInfo;
import com.zeta.firewall.model.entity.PortRule;

import java.util.*;
import java.util.stream.Collectors;

public class PortRuleUtils {
    public static List<PortInfo> expandAndDeduplicatePortRules(List<PortRule> portRules) {
        // 展开所有PortRule
        List<PortInfo> expandedRules = portRules.stream()
            .flatMap(rule -> expandPortRule(rule).stream())
            .collect(Collectors.toList());
        
        // 按照agentId + port + protocol去重
        return expandedRules.stream()
            .collect(Collectors.collectingAndThen(
                Collectors.toMap(
                    rule -> rule.getAgentId() + "|" + rule.getPortNumber() + "|" + rule.getProtocol(),
                    rule -> rule,
                    (r1, r2) -> r1,
                    LinkedHashMap::new
                ),
                map -> new ArrayList<>(map.values())
            ));
    }

    private static List<PortInfo> expandPortRule(PortRule rule) {
        List<PortInfo> result = new ArrayList<>();
        
        // 处理协议
        String[] protocols = rule.getProtocol().contains("/") 
            ? new String[]{"tcp", "udp"} 
            : new String[]{rule.getProtocol()};
        
        // 处理端口
        String port = rule.getPort();
        
        // 处理逗号分隔的端口（包括可能包含范围的端口）
        if (port.contains(",")) {
            String[] ports = port.split(",");
            for (String singlePort : ports) {
                singlePort = singlePort.trim();
                // 检查是否为范围
                if (singlePort.contains("-")) {
                    String[] range = singlePort.split("-");
                    int start = Integer.parseInt(range[0].trim());
                    int end = Integer.parseInt(range[1].trim());
                    for (int i = start; i <= end; i++) {
                        for (String protocol : protocols) {
                            PortInfo newRule = new PortInfo();
                            newRule.setAgentId(rule.getAgentId());
                            newRule.setPortNumber(i);
                            newRule.setProtocol(protocol);
                            result.add(newRule);
                        }
                    }
                } else {
                    // 单个端口
                    for (String protocol : protocols) {
                        PortInfo newRule = new PortInfo();
                        newRule.setAgentId(rule.getAgentId());
                        newRule.setPortNumber(Integer.parseInt(singlePort));
                        newRule.setProtocol(protocol);
                        result.add(newRule);
                    }
                }
            }
        }
        // 处理端口范围
        else if (port.contains("-")) {
            String[] range = port.split("-");
            int start = Integer.parseInt(range[0].trim());
            int end = Integer.parseInt(range[1].trim());
            for (int i = start; i <= end; i++) {
                for (String protocol : protocols) {
                    PortInfo newRule = new PortInfo();
                    newRule.setAgentId(rule.getAgentId());
                    newRule.setPortNumber(i);
                    newRule.setProtocol(protocol);
                    result.add(newRule);
                }
            }
        }
        // 单个端口
        else {
            for (String protocol : protocols) {
                PortInfo newRule = new PortInfo();
                newRule.setAgentId(rule.getAgentId());
                newRule.setPortNumber(Integer.valueOf(port));
                newRule.setProtocol(protocol);
                result.add(newRule);
            }
        }
        
        return result;
    }

    /**
     *
     * @param portRules 端口规则列表 （节点id，端口【22，4567-5643，（43，576，789）】，协议【tcp,udp,tcp/udp】）
     * @param dbPortInfos 和portRules对应的端口使用情况信息列表 （节点id，端口【一定为单个端口】，协议）
     * @return Map<String, List<PortInfo>>
     */
    public static Map<String, List<PortInfo>> connectPortInfosWithPortRules(List<PortRule> portRules, List<PortInfo> dbPortInfos) {
        HashMap<String, List<PortInfo>> map = new HashMap<>();
        for (PortRule portRule : portRules) {
            // 一条端口规则唯一的标识id
            Long portRuleId = portRule.getId();
            // 存储端口规则对应的端口使用信息
            ArrayList<PortInfo> portInfos = new ArrayList<>();

            for (PortInfo dbPortInfo : dbPortInfos) {
                boolean isEqualProtocol = portRule.getProtocol().equals(dbPortInfo.getProtocol());
                boolean isEqualAgentId = portRule.getAgentId().equals(dbPortInfo.getAgentId());

                boolean isEqualPort;
                String portPortRule = portRule.getPort();
                Integer portPortInfo = dbPortInfo.getPortNumber();
                // 端口规则中 端口为逗号分隔的情况 ，比如 3434,343,6765
                // 端口规则中 端口为范围的情况 ，比如 3434-34345
                // 端口规则中 端口为单个端口的情况 ，比如 3434
                if(portPortRule.contains(",")){
                    isEqualPort = portPortRule.contains(portPortInfo + "");
                }else if (portPortRule.contains("-")){
                    String[] startAndEndPort = portPortRule.split("-");
                    int start = Integer.parseInt(startAndEndPort[0].trim());
                    int end = Integer.parseInt(startAndEndPort[1].trim());
                    isEqualPort = portPortInfo >= start && portPortInfo <= end;
                }else{
                    isEqualPort = portPortRule.equals(portPortInfo + "");
                }

                if (isEqualProtocol && isEqualAgentId && isEqualPort) {
                    portInfos.add(dbPortInfo);
                }
            }
            map.put(portRuleId + "", portInfos);
        }
        return map;
    }
}