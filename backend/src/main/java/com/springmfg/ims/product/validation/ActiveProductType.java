package com.springmfg.ims.product.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** The value must be the code of an active row in product_types (replaces the spring-only regex). */
@Documented
@Constraint(validatedBy = ActiveProductTypeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ActiveProductType {

    String message() default "must be an active product type";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
