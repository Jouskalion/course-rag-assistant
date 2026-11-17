package com.courseqa.common;
import com.courseqa.contracts.dto.ApiError;
import com.courseqa.contracts.dto.ErrorEnvelope;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ErrorEnvelope> business(ServiceException ex) {
        return ResponseEntity.status(ex.httpStatus()).body(new ErrorEnvelope(ex.asError(),UUID.randomUUID()));
    }
    private ResponseEntity<ErrorEnvelope> error(int status,String code,String message) {
        return ResponseEntity.status(status).body(new ErrorEnvelope(new ApiError(code,message,false,List.of()),UUID.randomUUID()));
    }
    @ExceptionHandler(FeatureNotImplementedException.class)
    public ResponseEntity<ErrorEnvelope> pending(FeatureNotImplementedException ex) {return error(501,"FEATURE_NOT_IMPLEMENTED",ex.getMessage());}
    @ExceptionHandler({MethodArgumentNotValidException.class,MissingServletRequestParameterException.class,MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class,MissingServletRequestPartException.class})
    public ResponseEntity<ErrorEnvelope> invalid(Exception ex) {return error(422,"INVALID_REQUEST","请求字段或格式不符合接口约定");}
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorEnvelope> large(Exception ex) {return error(413,"FILE_TOO_LARGE","单个 PDF 不能超过 20 MiB");}
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorEnvelope> missing(Exception ex) {return error(404,"NOT_FOUND","接口或资源不存在");}
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> method(Exception ex) {return error(405,"METHOD_NOT_ALLOWED","请求方法不支持");}
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> media(Exception ex) {return error(415,"UNSUPPORTED_MEDIA_TYPE","请求媒体类型不支持");}
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> failure(Exception ex) {return error(500,"INTERNAL_ERROR","处理失败，请查看本机服务日志");}
}
