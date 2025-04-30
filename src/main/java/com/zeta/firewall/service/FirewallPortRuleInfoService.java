package com.zeta.firewall.service;

import com.zeta.firewall.model.entity.FirewallPortRuleInfo;

import java.util.ArrayList;
import java.util.List;

public interface FirewallPortRuleInfoService {
    /**
     * 删除全部数据
     * @return 删除结果 true:成功 false:失败
     */
    Boolean removeAll();

    /**
     * 批量插入
     * @param mappingsList mappingsList
     * @return 插入结果 true:成功 false:失败
     */
    Boolean addOrUpdateAll(ArrayList<FirewallPortRuleInfo> mappingsList);

    /**
     * 查询全部
     * @return list
     */
    List<FirewallPortRuleInfo> queryAll();
}
