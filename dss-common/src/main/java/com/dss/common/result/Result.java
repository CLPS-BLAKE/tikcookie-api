package com.dss.common.result;

import com.dss.common.error.CommonErrorCode;
import com.dss.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应体：{code, msg, data}，code=0 表示成功。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一响应")
public class Result<T> {

    @Schema(description = "0 表示成功，其他见接口文档的通用码和业务码", example = "0")
    private int code;

    @Schema(description = "提示信息", example = "ok")
    private String msg;

    @Schema(description = "业务数据，没有时为 null")
    private T data;

    public static <T> Result<T> ok(T data) {
        return new Result<>(CommonErrorCode.SUCCESS.getCode(), CommonErrorCode.SUCCESS.getMsg(), data);
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMsg(), null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String msg) {
        return new Result<>(errorCode.getCode(), msg, null);
    }
}
