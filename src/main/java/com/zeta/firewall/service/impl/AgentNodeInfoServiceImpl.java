package com.zeta.firewall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zeta.firewall.dao.AgentNodeInfoMapper;
import com.zeta.firewall.model.entity.AgentNodeInfo;
import com.zeta.firewall.service.AgentNodeInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Agent节点信息 服务实现类
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AgentNodeInfoServiceImpl extends ServiceImpl<AgentNodeInfoMapper, AgentNodeInfo> implements AgentNodeInfoService {
    // 继承 ServiceImpl 自动拥有常用的CRUD方法
}
