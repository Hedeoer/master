package com.zeta.firewall.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zeta.firewall.dao.FirewallPortRuleInfoMapper;
import com.zeta.firewall.model.entity.FirewallPortRuleInfo;
import com.zeta.firewall.service.FirewallPortRuleInfoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class FirewallPortRuleInfoServiceImpl extends ServiceImpl<FirewallPortRuleInfoMapper, FirewallPortRuleInfo> implements FirewallPortRuleInfoService {


    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean removeAll() {
        // 先查询是否有数据
        Long count = this.count();
        if (count == 0) {
            return true;
        }
        // 有数据才执行删除
        return this.remove(null);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean addOrUpdateAll(ArrayList<FirewallPortRuleInfo> mappingsList) {
        if (mappingsList == null || mappingsList.isEmpty()) {
            return true;
        }

        //按照ruleId和infoId去查询
        for (FirewallPortRuleInfo mapping : mappingsList) {
            LambdaQueryChainWrapper<FirewallPortRuleInfo> eq = this.lambdaQuery()
                    .eq(FirewallPortRuleInfo::getRuleId, mapping.getRuleId())
                    .eq(FirewallPortRuleInfo::getInfoId, mapping.getInfoId());

            FirewallPortRuleInfo one = eq.one();
            if (one != null) {
                mapping.setId(one.getId());
            }
        }

        return this.saveOrUpdateBatch(mappingsList);
    }

    @Override
    public List<FirewallPortRuleInfo> queryAll() {
        return this.getBaseMapper().selectList(null);
    }
}
