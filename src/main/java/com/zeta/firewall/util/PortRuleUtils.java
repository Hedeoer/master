package com.zeta.firewall.util;

import com.zeta.firewall.model.entity.PortInfo;
import com.zeta.firewall.model.entity.PortRule;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
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

                // 使用contains处理端口规则中协议为tcp/udp的情况
                boolean isEqualProtocol = portRule.getProtocol().toUpperCase()
                        .contains(dbPortInfo.getProtocol().toUpperCase());

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

    /**
     * portRules中端口展开和去重
     * @param portRules 需要去重的端口规则列表
     * @return 去重后的端口列表
     */
    public static List<String> extractUniquePortsFromRules(List<PortRule> portRules) {
        if (portRules == null || portRules.isEmpty()) {
            return Collections.emptyList();
        }

        return portRules.stream()
            .map(PortRule::getPort)
            .flatMap(portStr -> {
                Set<String> ports = new HashSet<>();
                
                String[] portParts = portStr.split(",");
                for (String part : portParts) {
                    part = part.trim();
                    try {
                        if (part.contains("-")) {
                            String[] range = part.split("-");
                            int start = Integer.parseInt(range[0].trim());
                            int end = Integer.parseInt(range[1].trim());
                            
                            // 验证端口范围
                            if (start < 1 || end > 65535 || start > end) {
                                log.warn("Invalid port range: {}", part);
                                continue;
                            }
                            
                            for (int i = start; i <= end; i++) {
                                ports.add(String.valueOf(i));
                            }
                        } else {
                            int port = Integer.parseInt(part);
                            if (port >= 1 && port <= 65535) {
                                ports.add(part);
                            } else {
                                log.warn("Port number out of range: {}", port);
                            }
                        }
                    } catch (NumberFormatException e) {
                        log.warn("Invalid port format: {}", part);
                    }
                }
                
                return ports.stream();
            })
            .distinct()
            .collect(Collectors.toList());
    }

    /**
     * 通过 portInfos 匹配 对应的 portRules
     * @param portRules
     * @param needMatchPortInfos
     * @return 匹配上的 portRules，需要去重
     */
    public static List<PortRule> matchPortRulesByPortInfos(List<PortRule> portRules, List<PortInfo> needMatchPortInfos) {
        HashSet<PortRule> result = new HashSet<>();

        for (PortRule portRule : portRules) {
            for (PortInfo needMatchPortInfo : needMatchPortInfos) {
                boolean equalsAgentId = portRule.getAgentId().equals(needMatchPortInfo.getAgentId());
                boolean equalsProtocol = portRule.getProtocol().equalsIgnoreCase(needMatchPortInfo.getProtocol());

                boolean equalsPort;
                String port = portRule.getPort();
                Integer portNumberFromPortInfo = needMatchPortInfo.getPortNumber();
                if (port.contains(",")) {
                    equalsPort = portRule.getPort().contains(portNumberFromPortInfo + "");

                } else if (port.contains("-")) {
                    String[] startAndEndPort = port.split("-");
                    int start = Integer.parseInt(startAndEndPort[0].trim());
                    int end = Integer.parseInt(startAndEndPort[1].trim());
                    equalsPort = portNumberFromPortInfo >= start && portNumberFromPortInfo <= end;

                }else {
                    equalsPort = portRule.getPort().equals(portNumberFromPortInfo + "");
                }

                if (equalsAgentId && equalsProtocol && equalsPort) {
                    result.add(portRule);
                }
            }
        }
        return new ArrayList<>(result);
    }
}
