package com.courseqa.common;
public class FeatureNotImplementedException extends RuntimeException {
    public FeatureNotImplementedException(String owner,String feature) { super(feature+" 尚未实现，负责人 "+owner); }
}
