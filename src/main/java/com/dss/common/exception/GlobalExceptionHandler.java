package com.dss.common.exception;

import com.dss.common.error.CommonErrorCode;
import com.dss.common.error.ErrorCode;
import com.dss.common.result.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 统一异常处理：所有异常都转成 {code, msg, data}，HTTP 状态按错误码走（见接口文档 1.3）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常（含骨架期的 NotImplementedException → 501 / 50100）。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        return build(e.getErrorCode(), e.getMessage());
    }

    /** @RequestBody 和查询参数对象的字段校验失败；MethodArgumentNotValidException 是它的子类。 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("；"));
        return build(CommonErrorCode.BAD_REQUEST, msg.isEmpty() ? CommonErrorCode.BAD_REQUEST.getMsg() : msg);
    }

    /** 路径参数、单个请求参数上的约束校验失败（Spring 6.1 起的方法级校验）。 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleMethodValidation(HandlerMethodValidationException e) {
        String msg = e.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return build(CommonErrorCode.BAD_REQUEST, msg.isEmpty() ? CommonErrorCode.BAD_REQUEST.getMsg() : msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("；"));
        return build(CommonErrorCode.BAD_REQUEST, msg.isEmpty() ? CommonErrorCode.BAD_REQUEST.getMsg() : msg);
    }

    /** 参数缺失、类型不对、请求体不是合法 JSON、请求方法或 Content-Type 不支持。 */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<Result<Void>> handleBadRequest(Exception e) {
        return build(CommonErrorCode.BAD_REQUEST, CommonErrorCode.BAD_REQUEST.getMsg() + "：" + e.getMessage());
    }

    /** 请求整体超过 spring.servlet.multipart 的上限（10MB）。单张图片的业务上限另见 106003。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUpload(MaxUploadSizeExceededException e) {
        return build(CommonErrorCode.BAD_REQUEST, "上传的文件太大");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException e) {
        return build(CommonErrorCode.NOT_FOUND, CommonErrorCode.NOT_FOUND.getMsg());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        log.error("未处理的异常", e);
        return build(CommonErrorCode.SYSTEM_ERROR, CommonErrorCode.SYSTEM_ERROR.getMsg());
    }

    private ResponseEntity<Result<Void>> build(ErrorCode code, String msg) {
        return ResponseEntity.status(code.getHttpStatus()).body(Result.fail(code, msg));
    }
}
