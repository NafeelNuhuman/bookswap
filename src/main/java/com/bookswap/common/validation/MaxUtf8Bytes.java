package com.bookswap.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** The string's UTF-8 encoding must not exceed {@code value} bytes (e.g. BCrypt's 72-byte input limit). */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface MaxUtf8Bytes {
    int value();

    String message() default "must be at most {value} bytes when UTF-8 encoded";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
