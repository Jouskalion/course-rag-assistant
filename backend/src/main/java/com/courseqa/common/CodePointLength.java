package com.courseqa.common;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;
@Target({ElementType.FIELD,ElementType.PARAMETER,ElementType.RECORD_COMPONENT,ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy=CodePointLengthValidator.class)
public @interface CodePointLength {
    int min() default 0;
    int max() default Integer.MAX_VALUE;
    String message() default "字符长度不符合约定";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
