package com.dss.common.result;

import com.dss.common.error.CommonErrorCode;
import com.dss.common.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一响应契约（接口文档 1.3）：永远是 {code, msg, data} 三个字段，成功码为 0、msg 为 ok。
 */
class ResultContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("成功响应：code=0、msg=ok、data 是业务数据")
    void successShape() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(Result.ok("payload")));

        assertThat(fieldNames(json)).containsExactlyInAnyOrder("code", "msg", "data");
        assertThat(json.get("code").asInt()).isZero();
        assertThat(json.get("msg").asText()).isEqualTo("ok");
        assertThat(json.get("data").asText()).isEqualTo("payload");
    }

    @Test
    @DisplayName("成功但没有数据时 data 仍出现在响应里，值为 null")
    void successWithoutDataKeepsField() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(Result.ok()));

        assertThat(json.has("data")).isTrue();
        assertThat(json.get("data").isNull()).isTrue();
    }

    @Test
    @DisplayName("失败响应：code/msg 取自错误码，data 为 null")
    void failureShape() throws Exception {
        JsonNode json = objectMapper.readTree(
                objectMapper.writeValueAsString(Result.fail(CommonErrorCode.UNAUTHORIZED)));

        assertThat(json.get("code").asInt()).isEqualTo(40100);
        assertThat(json.get("msg").asText()).isEqualTo("未登录或登录已过期");
        assertThat(json.get("data").isNull()).isTrue();
    }

    @Test
    @DisplayName("通用码与 HTTP 状态一一对应")
    void commonErrorCodeMapping() {
        assertMapping(CommonErrorCode.SUCCESS, 0, 200);
        assertMapping(CommonErrorCode.BAD_REQUEST, 40000, 400);
        assertMapping(CommonErrorCode.UNAUTHORIZED, 40100, 401);
        assertMapping(CommonErrorCode.INTERNAL_KEY_INVALID, 40300, 403);
        assertMapping(CommonErrorCode.NOT_FOUND, 40400, 404);
        assertMapping(CommonErrorCode.SYSTEM_ERROR, 50000, 500);
        assertMapping(CommonErrorCode.NOT_IMPLEMENTED, 50100, 501);
    }

    @Test
    @DisplayName("业务码的 HTTP 状态一律 200（文件模块 106xxx 走 200，不是 4xx/5xx）")
    void businessErrorCodeReturnsHttp200() {
        ErrorCode fileError = new ErrorCode() {
            @Override
            public int getCode() {
                return 106002;
            }

            @Override
            public String getMsg() {
                return "只支持 jpg、png、webp 图片";
            }
        };

        assertThat(fileError.getHttpStatus()).isEqualTo(200);
    }

    private void assertMapping(ErrorCode code, int expectedCode, int expectedHttpStatus) {
        assertThat(code.getCode()).isEqualTo(expectedCode);
        assertThat(code.getHttpStatus()).isEqualTo(expectedHttpStatus);
    }

    private java.util.List<String> fieldNames(JsonNode json) {
        java.util.List<String> names = new java.util.ArrayList<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
