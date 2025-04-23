package com.zeta.firewall.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * agent节点向master节点汇报心跳时，需要汇报的信息
 * 上报心跳时间戳，是否首次上报， 是否存活，操作系统，主机名，节点ip
 *
 */
//   /** CPU利用率 */
//  cpuUsage?: number
//  /** 内存利用率 */
//  memoryUsage?: number
//  /** 磁盘利用率 */
//  diskUsage?: number
//  /** 客户端版本 */
//  clientVersion?: string
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("agent_node_info")
public  class AgentNodeInfo {
    @TableId(value = "agent_id")
    private String agentId;
    private String heartbeatTimestamp;
    private Boolean isFirstHeartbeat;
    private Boolean isActive;
    private String osName;
    private String hostName;
    private String ip;
    private String cpuUsage;
    private String memoryUsage;
    private String diskUsage;
    private String clientVersion;
}