package com.zeta.firewall.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import org.zetaframework.base.entity.Entity;

import java.util.Objects;

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

    /**
     * 计算信息完整度评分
     * 用于在多个相同端口的连接中选择信息最完整的一条
     * @return 信息完整度评分，分数越高表示信息越完整
     */
    public int gainInfoCompletenessScore() {
        int score = 0;
        if (processName != null && !processName.isEmpty()) score += 1;
        if (processId != null && processId > 0) score += 1;
        if (commandLine != null && !commandLine.isEmpty()) score += 1;
        if (listenAddress != null && !listenAddress.isEmpty() && !"unknown".equals(listenAddress)) score += 1;
        return score;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        // 不比较父类字段，因为父类包含时间字段
        PortInfo portInfo = (PortInfo) o;
        return Objects.equals(portNumber, portInfo.portNumber) &&
                Objects.equals(processId, portInfo.processId) &&
                Objects.equals(agentId, portInfo.agentId) &&
                Objects.equals(protocol, portInfo.protocol) &&
                Objects.equals(processName, portInfo.processName) &&
                Objects.equals(commandLine, portInfo.commandLine) &&
                Objects.equals(listenAddress, portInfo.listenAddress);
    }

    @Override
    public int hashCode() {
        // 不包含父类的hashCode，因为父类包含时间字段
        return Objects.hash(agentId, protocol, portNumber, processName, processId, commandLine, listenAddress);
    }
}