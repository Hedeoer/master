DROP TABLE IF EXISTS `firewall_port_rule`;

CREATE TABLE `firewall_port_rule`
(
    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT 'id',
    `create_time` datetime     NULL DEFAULT NULL COMMENT '创建时间',
    `created_by`  bigint(20)   NULL DEFAULT NULL COMMENT '创建人',
    `update_time` datetime     NULL DEFAULT NULL COMMENT '修改时间',
    `updated_by`  bigint(20)   NULL DEFAULT NULL COMMENT '修改人',

    -- AbstractFirewallRule fields
    `permanent`   bit(1)       NULL DEFAULT NULL COMMENT '是否永久生效',
    `type`        varchar(32)  NULL DEFAULT NULL COMMENT '规则类型',
    `zone`        varchar(32)  NULL DEFAULT NULL COMMENT '作用域',
    `agent_id`    varchar(64)  NULL DEFAULT NULL COMMENT '所属节点ID',

    -- PortRule specific fields
    `family`      varchar(10)  NULL DEFAULT NULL COMMENT 'ip类型(ipv4,ipv6)',
    `port`        varchar(32)  NOT NULL COMMENT '端口号或范围(如 "80" 或 "1024-2048")',
    `protocol`    varchar(10)  NOT NULL COMMENT '协议(tcp或udp)',
    `using`       bit(1)       NULL DEFAULT NULL COMMENT '端口使用状态(已使用，未使用)',
    `policy`      bit(1)       NULL DEFAULT NULL COMMENT '端口策略(允许，拒绝)',
    `source_rule` varchar(255) NULL DEFAULT NULL COMMENT '源IP地址或CIDR',
    `descriptor`  varchar(255) NULL DEFAULT NULL COMMENT '端口描述信息',

    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_agent_port_protocol`(`agent_id`, `port`, `protocol`) COMMENT '节点下端口和协议唯一索引'
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '防火墙端口规则表'
  ROW_FORMAT = DYNAMIC;