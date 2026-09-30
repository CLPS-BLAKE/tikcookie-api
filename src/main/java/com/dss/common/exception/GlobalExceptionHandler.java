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
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 统一异常处理：所有异常都转成 {code, msg, data}，HTTP 状态按错误码走（见接口文档 1.3）。
 * <p>
 * 两类消息的区别（接口文档 1.3 的约定）：
 * <ul>
 *     <li>校验类异常（{@link BindException} 等）的 msg 来自代码里的校验注解，是写给人看的中文提示，直接返回。</li>
 *     <li>框架异常（JSON 解析、类型不匹配、方法/Content-Type 不支持等）的 {@code getMessage()} 会带上
 *         Jackson 类名、字段名、目标类型等内部信息，只进服务端日志，返回给客户端的是固定提示。</li>
 *     <li>业务码（如 106xxx）的 HTTP 状态一律 200；通用码按 {@link ErrorCode#getHttpStatus()}。</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常（含骨架期的 NotImplementedException → 501 / 50100）。msg 由代码显式给定，可以安全返回。 */
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

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        return badRequest("缺少请求参数：" + e.getParameterName(), e);
    }

    /** 上传接口没带 file 部分时走这里。 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Result<Void>> handleMissingPart(MissingServletRequestPartException e) {
        return badRequest("缺少请求部分：" + e.getRequestPartName(), e);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<Void>> handleMissingHeader(MissingRequestHeaderException e) {
        return badRequest("缺少请求头：" + e.getHeaderName(), e);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return badRequest("参数格式不正确：" + e.getName(), e);
    }

    /** 请求体不是合法 JSON：不返回 Jackson 的解析细节。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return badRequest("请求体不是合法的 JSON", e);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return badRequest("请求方法不支持：" + e.getMethod(), e);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return badRequest("Content-Type 不支持：" + e.getContentType(), e);
    }

    /** 请求整体超过 spring.servlet.multipart 的上限（10MB）→ HTTP 400 / 40000。单张图片的业务上限另见 106003。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUpload(MaxUploadSizeExceededException e) {
        log.warn("上传请求超过框架上限：{}", e.getMessage());
        return build(CommonErrorCode.BAD_REQUEST, "上传的文件太大");
    }

    /** multipart 请求本身有问题（边界缺失、格式错乱等）；尺寸超限由上面更具体的处理器接。 */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Result<Void>> handleMultipart(MultipartException e) {
        return badRequest("上传请求格式不正确", e);
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

    /** 客户端错误：固定提示返回给客户端，异常原文只进日志（可能含类名、字段名等内部信息）。 */
    private ResponseEntity<Result<Void>> badRequest(String msg, Exception e) {
        log.warn("请求不合法：{}（{}）", msg, e.getMessage());
        return build(CommonErrorCode.BAD_REQUEST, msg);
    }

    private ResponseEntity<Result<Void>> build(ErrorCode code, String msg) {
        return ResponseEntity.status(code.getHttpStatus()).body(Result.fail(code, msg));
    }
}
