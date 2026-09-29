package com.dss.file.model.enums;

import com.dss.common.error.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件模块业务码 106xxx。
 */
@Getter
@AllArgsConstructor
public enum FileErrorCode implements ErrorCode {

    FILE_EMPTY(106001, "文件为空"),
    FILE_TYPE_NOT_ALLOWED(106002, "只支持 jpg、png、webp 图片"),
    FILE_TOO_LARGE(106003, "文件超过大小限制"),
    FILE_UPLOAD_FAILED(106004, "文件上传失败");

    private final int code;
    private final String msg;
}
