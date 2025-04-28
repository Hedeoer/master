package com.zeta.firewall.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import org.zetaframework.base.entity.Entity;

/**
 * 端口信息实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@TableName(value = "firewall_port_info")
public class PortInfo extends Entity<Long> {
    private String agentId;          // agent节点的唯一标识
    private String protocol;         // 协议
    private Integer portNumber;          // 端口号
    private String processName;      // 进程名
    private Integer processId;           // 进程ID
    private String commandLine;      // 完整命令行
    private String listenAddress;    // 监听地址
}