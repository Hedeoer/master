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
    `id`             bigint(20)   NOT NULL AUTO_INCREMENT COMMENT 'id',
    `create_time`    datetime     NULL DEFAULT NULL COMMENT '创建时间',
    `created_by`     bigint(20)   NULL DEFAULT NULL COMMENT '创建人',
    `update_time`    datetime     NULL DEFAULT NULL COMMENT '修改时间',
    `updated_by`     bigint(20)   NULL DEFAULT NULL COMMENT '修改人',

    `rule_id` bigint(20) NOT NULL COMMENT 'firewall_port_rule主键ID',
    `info_id` bigint(20) NOT NULL COMMENT 'firewall_port_info主键ID',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_rule_info` (`rule_id`, `info_id`)
) ENGINE = InnoDB
  default charset = utf8mb4 COMMENT ='端口规则与端口信息映射表';


