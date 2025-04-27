package com.zeta.firewall.validation.validator;

import com.zeta.firewall.model.enums.SourceTypeEnum;
import com.zeta.firewall.validation.annotation.ValidSourceType;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class SourceTypeValidator implements ConstraintValidator<ValidSourceType, String> {

    @Override
    public void initialize(ValidSourceType constraintAnnotation) {
        // 不需要初始化
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        return SourceTypeEnum.isValid(value);
    }
}