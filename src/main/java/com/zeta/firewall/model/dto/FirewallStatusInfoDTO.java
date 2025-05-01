package com.zeta.firewall.model.dto;

import com.zeta.firewall.model.entity.FirewallStatusInfo;
import com.zeta.firewall.model.enums.FireWallStatus;
import com.zeta.firewall.model.enums.PingStatus;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 防火墙状态数据传输对象
 * 用于前端展示的端口规则数据格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "防火墙状态DTO")
public class FirewallStatusInfoDTO {
    /**
     * {
     *   "success": true,
     *   "code": 200,
     *   "message": "操作成功",
     *   "data": {
     *     "status": "running",      // 可选值: "running" 或 "not running"
     *     "name": "UFW",            // 防火墙名称，如"UFW", "Firewalld", "IPTables"等
     *     "version": "0.36.2",      // 防火墙版本号
     *     "pingStatus": "Disable"   // Ping响应状态，可选值: "Enable" 或 "Disable"
     *   }
     * }
     */

    @ApiModelProperty(value = "防火墙运行状态(running, not running)")
    private String status;

    @ApiModelProperty(value = "防火墙工具类型(UFW,Firewalld)")
    private String name;

    @ApiModelProperty(value = "防火墙版本号(0.36.2)")
    private String version;

    @ApiModelProperty(value = "是否禁用ping(Enable,Disable)")
    private String pingStatus;

    public static FirewallStatusInfoDTO fromEntity(FirewallStatusInfo entity) {
        if (entity == null) {
            return null;
        }


        String status = FireWallStatus.ACTIVE.equals(entity.getStatus()) ? "running" : "not running";
        String fireWallType = entity.getFirewallType().name();
        String pingStatus = PingStatus.STATUS_ENABLE.equals(entity.getPingDisabled()) ? "Disable" : "Enable";

        return FirewallStatusInfoDTO.builder()
                .status(status)
                .name(fireWallType)
                .version(entity.getVersion())
                .pingStatus(pingStatus)
                .build();
    }


}
