package com.zeta.firewall.validation.annotation;

import com.zeta.firewall.validation.validator.SourceTypeValidator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = SourceTypeValidator.class)
public @interface ValidSourceType {
    String message() default "源地址类型必须为 'any' 或 'specific'";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}