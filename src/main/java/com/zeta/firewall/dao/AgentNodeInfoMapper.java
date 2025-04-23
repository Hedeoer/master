package com.zeta.firewall.dao;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zeta.firewall.model.entity.AgentNodeInfo;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentNodeInfoMapper extends BaseMapper<AgentNodeInfo> {
    // 继承 BaseMapper 自动拥有常用的CRUD方法
}