package com.courseqa.common;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
public class CodePointLengthValidator implements ConstraintValidator<CodePointLength,String> {
    private int min,max;
    public void initialize(CodePointLength rule) { min=rule.min();max=rule.max(); }
    public boolean isValid(String value,ConstraintValidatorContext context) {
        if(value==null)return true;
        int length=value.codePointCount(0,value.length());return length>=min&&length<=max;
    }
}
