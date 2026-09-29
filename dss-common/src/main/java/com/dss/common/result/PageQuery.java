package com.dss.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页请求参数：page 从 1 开始，默认 1；size 默认 10，最大 50。各模块的查询参数都继承它。
 */
@Data
public class PageQuery {

    @Min(value = 1, message = "page 从 1 开始")
    @Schema(description = "页码，从 1 开始", example = "1")
    private int page = 1;

    @Min(value = 1, message = "size 至少为 1")
    @Max(value = 50, message = "size 最大为 50")
    @Schema(description = "每页条数，最大 50", example = "10")
    private int size = 10;
}
