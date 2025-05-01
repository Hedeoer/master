package com.zeta.firewall.validation.annotation;

import com.zeta.firewall.validation.validator.FirewallOperationValidator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.*;

/**
 * 防火墙操作类型验证注解
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = FirewallOperationValidator.class)
public @interface ValidFirewallOperation {
    String message() default "操作类型必须为 'start', 'stop' 或 'restart'";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
