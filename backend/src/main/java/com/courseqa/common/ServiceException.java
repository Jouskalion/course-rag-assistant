package com.courseqa.common;
import com.courseqa.contracts.dto.ApiError;
import java.util.List;
/** 后台任务保存 asError，HTTP 控制器使用 httpStatus。负责人 D。 */
public class ServiceException extends RuntimeException {
    private final String code;
    private final int httpStatus;
    private final boolean retryable;
    public ServiceException(String code, int httpStatus, String message, boolean retryable) {
        super(message); this.code=code; this.httpStatus=httpStatus; this.retryable=retryable;
    }
    public int httpStatus() { return httpStatus; }
    public ApiError asError() { return new ApiError(code,getMessage(),retryable,List.of()); }
}
