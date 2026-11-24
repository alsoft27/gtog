package com.gtog.event.infrastructure.in.web;

import java.lang.annotation.*;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FixedDateFieldsValidator.class)
public @interface ValidFixedDateFields {
	String message() default "startsAt and endsAt are required for FIXED_DATE";
	Class<?>[] groups() default {};
	Class<? extends Payload>[] payload() default {};
}
