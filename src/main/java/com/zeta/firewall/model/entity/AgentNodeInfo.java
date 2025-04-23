package com.zeta.firewall.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * agent节点心跳汇报的信息映射类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("agent_node_info")
public class AgentNodeInfo {
    // agent节点唯一标识， 22位字符串
    @TableId(value = "agent_id")
    private String agentId;
    // agent节点心跳上报时的秒时间戳
    private String heartbeatTimestamp;
    // 是否首次上报
    private Boolean isFirstHeartbeat;
    // 是否存活
    private Boolean isActive;
    // agent节点的操作系统类型
    private String osName;
    // 节点主机名
    private String hostName;
    // 节点ip
    private String ip;
}