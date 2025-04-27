package com.zeta.firewall.validation.annotation;

import com.zeta.firewall.validation.validator.SourceAddressValidator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = SourceAddressValidator.class)
public @interface ValidSourceAddress {
    String message() default "请输入有效的IPv4、IPv6或CIDR格式地址";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
