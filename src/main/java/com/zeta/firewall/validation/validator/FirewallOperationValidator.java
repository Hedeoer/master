package com.zeta.firewall.validation.validator;

import com.zeta.firewall.model.enums.FirewallOperationType;
import com.zeta.firewall.validation.annotation.ValidFirewallOperation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * 防火墙操作类型验证器
 */
public class FirewallOperationValidator implements ConstraintValidator<ValidFirewallOperation, String> {

    @Override
    public void initialize(ValidFirewallOperation constraintAnnotation) {
        // 不需要初始化
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        
        // 使用枚举的fromValue方法验证值是否有效
        return FirewallOperationType.fromValue(value) != null;
    }
}
