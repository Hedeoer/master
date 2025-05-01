package com.zeta.firewall.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zeta.firewall.model.enums.FireWallStatus;
import com.zeta.firewall.model.enums.FireWallType;
import com.zeta.firewall.model.enums.PingStatus;
import lombok.*;
import org.zetaframework.base.entity.Entity;

@Data
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("firewall_status_info")
public class FirewallStatusInfo extends Entity<Long> {
    /** 机器唯一标识 */
    private String agentId;

    /** 防火墙类型（FIREWALLD、UFW、NONE） */
    private FireWallType firewallType;

    /** 防火墙运行状态（UNKNOWN,NOT_INSTALLED,ACTIVE,INACTIVE） */
    private FireWallStatus status;

    /** 防火墙版本号 */
    private String version;

    /** 是否禁ping (STATUS_DISABLE,STATUS_ENABLE,STATUS_NONE) */
    private PingStatus pingDisabled;

    /** 获取秒级时间戳 */
    private Long timestamp;
}