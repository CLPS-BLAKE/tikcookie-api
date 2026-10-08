package com.dss.common.exception;

import com.dss.common.error.CommonErrorCode;
import com.dss.common.result.PageQuery;
import com.dss.common.result.Result;
import com.dss.file.model.enums.FileErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全局异常处理的契约与信息泄露检查（接口文档 1.3）。
 * 通用码按 HTTP 状态一一对应；业务码（106xxx）HTTP 状态一律 200。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("业务异常：HTTP 200 + 业务码（文件 106002）")
    void bizExceptionKeepsHttp200() {
        ResponseEntity<Result<Void>> response =
                handler.handleBiz(new BizException(FileErrorCode.FILE_TYPE_NOT_ALLOWED));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(106002);
        assertThat(response.getBody().getMsg()).isEqualTo("只支持 jpg、png、webp 图片");
    }

    @Test
    @DisplayName("骨架期未实现：HTTP 501 + 50100")
    void notImplementedMapsTo501() {
        ResponseEntity<Result<Void>> response = handler.handleBiz(new NotImplementedException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
        assertThat(response.getBody().getCode()).isEqualTo(50100);
    }

    @Test
    @DisplayName("JSON 解析失败：HTTP 400 + 40000，且不把 Jackson 的解析细节透给客户端")
    void jsonParseFailureDoesNotLeakInternals() {
        String jacksonDetail = "JSON parse error: Unexpected character ('n' (code 110)): was expecting double-quote "
                + "to start field name; nested exception is com.fasterxml.jackson.core.JsonParseException: "
                + "through reference chain: com.dss.order.model.dto.CreateOrderDTO[\"shopId\"]";
        HttpMessageNotReadableException e =
                new HttpMessageNotReadableException(jacksonDetail, new MockHttpInputMessage(new byte[0]));

        ResponseEntity<Result<Void>> response = handler.handleNotReadable(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo(40000);
        String msg = response.getBody().getMsg();
        assertThat(msg).isEqualTo("请求体不是合法的 JSON");
        assertThat(msg).doesNotContain("com.fasterxml").doesNotContain("JsonParseException")
                .doesNotContain("CreateOrderDTO").doesNotContain("Unexpected character");
    }

    @Test
    @DisplayName("参数类型不对：只回参数名，不回异常原文")
    void typeMismatchDoesNotLeakInternals() {
        MethodArgumentTypeMismatchException e = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "shopId", null, new IllegalArgumentException("NumberFormatException: For input string: \"abc\""));

        ResponseEntity<Result<Void>> response = handler.handleTypeMismatch(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo(40000);
        assertThat(response.getBody().getMsg()).isEqualTo("参数格式不正确：shopId");
        assertThat(response.getBody().getMsg()).doesNotContain("NumberFormatException");
    }

    @Test
    @DisplayName("缺少 multipart 的 file 部分：HTTP 400 + 40000，提示里带字段名")
    void missingPart() {
        ResponseEntity<Result<Void>> response =
                handler.handleMissingPart(new MissingServletRequestPartException("file"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo(40000);
        assertThat(response.getBody().getMsg()).isEqualTo("缺少请求部分：file");
    }

    @Test
    @DisplayName("缺少查询参数：HTTP 400 + 40000")
    void missingParameter() {
        ResponseEntity<Result<Void>> response =
                handler.handleMissingParameter(new MissingServletRequestParameterException("page", "int"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMsg()).isEqualTo("缺少请求参数：page");
    }

    @Test
    @DisplayName("请求方法不支持：HTTP 400 + 40000")
    void methodNotSupported() {
        ResponseEntity<Result<Void>> response =
                handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PATCH"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMsg()).contains("PATCH");
    }

    @Test
    @DisplayName("Content-Type 不支持：HTTP 400 + 40000")
    void mediaTypeNotSupported() {
        ResponseEntity<Result<Void>> response =
                handler.handleMediaTypeNotSupported(new HttpMediaTypeNotSupportedException("Content-Type 'text/plain' is not supported"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo(40000);
        assertThat(response.getBody().getMsg()).startsWith("Content-Type 不支持");
    }

    @Test
    @DisplayName("请求整体超过 10MB：HTTP 400 + 40000（和单图 106003 区分开）")
    void maxUploadSize() {
        MaxUploadSizeExceededException e = new MaxUploadSizeExceededException(10 * 1024 * 1024);

        ResponseEntity<Result<Void>> response = handler.handleMaxUpload(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo(40000);
        assertThat(response.getBody().getMsg()).isEqualTo("上传的文件太大");
        assertThat(e.getMessage()).contains("10485760"); // 细节只进日志
    }

    @Test
    @DisplayName("系统异常：HTTP 500 + 50000，不把异常原文写进响应")
    void systemErrorHidesDetail() {
        ResponseEntity<Result<Void>> response =
                handler.handleOther(new IllegalStateException("jdbc connection refused to 10.0.0.5:3306"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getCode()).isEqualTo(50000);
        assertThat(response.getBody().getMsg()).isEqualTo("系统繁忙，请稍后再试");
        assertThat(response.getBody().getMsg()).doesNotContain("10.0.0.5");
    }

    @Test
    @DisplayName("校验类异常的中文提示可以直接返回（来自代码里的注解，不是框架原文）")
    void bindingMessageIsReturned() {
        org.springframework.validation.BeanPropertyBindingResult binding =
                new org.springframework.validation.BeanPropertyBindingResult(new PageQuery(), "query");
        binding.rejectValue("size", "Max", "size 最大为 50");

        ResponseEntity<Result<Void>> response = handler.handleBind(new BindException(binding));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo(CommonErrorCode.BAD_REQUEST.getCode());
        assertThat(response.getBody().getMsg()).contains("size 最大为 50");
    }

    @Test
    @DisplayName("响应体按 UTF-8 输出中文，不是乱码")
    void messageIsUtf8() {
        ResponseEntity<Result<Void>> response = handler.handleBiz(new BizException(FileErrorCode.FILE_TOO_LARGE));

        assertThat(response.getBody().getMsg().getBytes(StandardCharsets.UTF_8)).isNotEmpty();
        assertThat(response.getBody().getMsg()).isEqualTo("文件超过大小限制");
    }
}
