package com.zeta.firewall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zeta.firewall.model.entity.AgentNodeInfo;

import java.util.List;


public interface AgentNodeInfoService extends IService<AgentNodeInfo> {
    /**
     * 删除agent节点
     *
     * 删除agent节点时，需要删除以下数据：
     * 1. 删除agent节点信息
     * 2. 删除agent节点的端口规则
     * 3. 删除agent节点的端口信息
     * 4. 删除agent节点的防火墙状态信息
     * 5. 删除agent节点的redis stream数据
     *
     * @param nodeIds 节点id列表
     * @return 删除成功返回true，否则返回false
     */
    Boolean batchDeleteNodes(List<String> nodeIds);
}
