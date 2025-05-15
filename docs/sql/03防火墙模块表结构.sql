create table if not exists agent_node_info
(
    agent_id            varchar(22)  not null comment 'agent节点唯一标识，22位字符串'
        primary key,
    heartbeat_timestamp varchar(20)  not null comment 'agent节点心跳上报时的时间戳',
    is_first_heartbeat  tinyint(1)   not null comment '是否首次上报（0=否, 1=是）',
    is_active           tinyint(1)   not null comment '是否存活（0=否, 1=是）',
    os_name             varchar(64)  null comment 'agent节点的操作系统类型',
    host_name           varchar(128) null comment '节点主机名',
    ip                  varchar(45)  null comment '节点IP（支持IPv6）',
    cpu_usage           varchar(20)  null comment 'CPU使用率',
    memory_usage        varchar(20)  null comment '内存使用率',
    disk_usage          varchar(20)  null comment '磁盘使用率',
    client_version      varchar(32)  null comment '客户端版本'
)
    ENGINE = InnoDB
    CHARACTER SET = utf8mb4
    COLLATE = utf8mb4_general_ci COMMENT = 'Agent节点信息表'
    ROW_FORMAT = DYNAMIC;

create index idx_heartbeat_timestamp
    on agent_node_info (heartbeat_timestamp);



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
    UNIQUE INDEX `uk_agent_port_protocol` (`agent_id`, `permanent`, `type`, `zone`, `family`, `port`, `protocol`,
                                           `source_rule`, `policy`) COMMENT '组合唯一索引'
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '防火墙端口规则表'
  ROW_FORMAT = DYNAMIC;

DROP TABLE IF EXISTS `firewall_port_info`;
CREATE TABLE `firewall_port_info`
(
    `id`             bigint(20)   NOT NULL AUTO_INCREMENT COMMENT 'id',
    `create_time`    datetime     NULL DEFAULT NULL COMMENT '创建时间',
    `created_by`     bigint(20)   NULL DEFAULT NULL COMMENT '创建人',
    `update_time`    datetime     NULL DEFAULT NULL COMMENT '修改时间',
    `updated_by`     bigint(20)   NULL DEFAULT NULL COMMENT '修改人',

    `agent_id`       varchar(64)  NOT NULL COMMENT 'agent节点的唯一标识',
    `protocol`       varchar(10)  NOT NULL COMMENT '协议',
    `port_number`    int(11)      NOT NULL COMMENT '端口号',
    `process_name`   varchar(255) NULL DEFAULT NULL COMMENT '进程名',
    `process_id`     int(11)      NULL DEFAULT NULL COMMENT '进程ID',
    `command_line`   text         NULL DEFAULT NULL COMMENT '完整命令行',
    `listen_address` varchar(255) NULL DEFAULT NULL COMMENT '监听地址',

    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_agent_protocol_port` (`agent_id`, `protocol`, `port_number`) USING BTREE COMMENT 'agent节点协议端口唯一索引'
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '防火墙端口使用信息表'
  ROW_FORMAT = DYNAMIC;

drop table if exists firewall_port_rule_info;
CREATE TABLE `firewall_port_rule_info`
(
    `id`          bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
    `create_time` datetime   NULL DEFAULT NULL COMMENT '创建时间',
    `created_by`  bigint(20) NULL DEFAULT NULL COMMENT '创建人',
    `update_time` datetime   NULL DEFAULT NULL COMMENT '修改时间',
    `updated_by`  bigint(20) NULL DEFAULT NULL COMMENT '修改人',

    `rule_id`     bigint(20) NOT NULL COMMENT 'firewall_port_rule主键ID',
    `info_id`     bigint(20) NOT NULL COMMENT 'firewall_port_info主键ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_rule_info` (`rule_id`, `info_id`)
) ENGINE = InnoDB
  default charset = utf8mb4 COMMENT ='端口规则与端口信息映射表';


drop table if exists `firewall_status_info`;
CREATE TABLE `firewall_status_info`
(
    `id`            bigint(20)  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `agent_id`      varchar(64) NOT NULL COMMENT '机器唯一标识',
    `firewall_type` varchar(20) NOT NULL COMMENT '防火墙类型（FIREWALLD、UFW、NONE）',
    `status`        varchar(20) DEFAULT NULL COMMENT '防火墙运行状态（UNKNOWN,NOT_INSTALLED,ACTIVE,INACTIVE）',
    `version`       varchar(50) DEFAULT NULL COMMENT '防火墙版本号',
    `ping_disabled` varchar(20) DEFAULT NULL COMMENT '是否禁ping (STATUS_DISABLE,STATUS_ENABLE,STATUS_NONE)',
    `timestamp`     bigint(20)  DEFAULT NULL COMMENT '获取秒级时间戳',
    `create_time`   datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `created_by`    varchar(64) DEFAULT NULL COMMENT '创建人',
    `update_time`   datetime    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `updated_by`    varchar(64) DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_firewall` (`agent_id`, `firewall_type`) COMMENT 'agent_id和firewall_type组合唯一键',
    KEY `idx_create_time` (`create_time`) COMMENT '创建时间索引'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='防火墙状态信息表';
