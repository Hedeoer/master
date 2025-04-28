package com.zeta.firewall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zeta.firewall.dao.PortInfoMapper;
import com.zeta.firewall.model.entity.PortInfo;
import com.zeta.firewall.service.PortInfoService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@SpringBootTest
@ActiveProfiles("dev")  // 使用开发环境配置
@Transactional  // 测试完自动回滚，不污染数据库
class PortInfoServiceImplTest {

    @Autowired
    private PortInfoService portInfoService;
    
    @Autowired
    private PortInfoMapper portInfoMapper;

    private List<PortInfo> portInfoList;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        portInfoList = new ArrayList<>();
        portInfoList.add(PortInfo.builder()
                .agentId("node1")
                .protocol("tcp")
                .portNumber(8080)
                .processName("java")
                .processId(1234)
                .commandLine("java -jar app.jar")
                .listenAddress("0.0.0.0")
                .build());

        portInfoList.add(PortInfo.builder()
                .agentId("node1")
                .protocol("tcp")
                .portNumber(3306)
                .processName("mysqld")
                .processId(5678)
                .commandLine("/usr/sbin/mysqld")
                .listenAddress("127.0.0.1")
                .build());
    }

    @Test
    @DisplayName("测试批量插入或更新端口信息")
    void insertOrUpdateBatchPortInfos() {
        // 执行批量插入
        Boolean result = portInfoService.insertOrUpdateBatchPortInfos(portInfoList);
        
        // 验证插入结果
        assertTrue(result);
        
        // 从数据库查询验证
        for (PortInfo portInfo : portInfoList) {
            PortInfo dbPortInfo = portInfoMapper.selectOne(
                new QueryWrapper<PortInfo>()
                    .eq("agent_id", portInfo.getAgentId())
                    .eq("protocol", portInfo.getProtocol())
                    .eq("port_number", portInfo.getPortNumber())
            );
            log.info("数据库查询结果: {}", dbPortInfo);
            assertTrue(dbPortInfo != null);
            assertEquals(portInfo.getProcessName(), dbPortInfo.getProcessName());
            assertEquals(portInfo.getProcessId(), dbPortInfo.getProcessId());
            assertEquals(portInfo.getCommandLine(), dbPortInfo.getCommandLine());
            assertEquals(portInfo.getListenAddress(), dbPortInfo.getListenAddress());
        }
    }
}
