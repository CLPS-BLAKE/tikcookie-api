package com.dss.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页响应：{list, total, page, size}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页结果")
public class PageResult<T> {

    @Schema(description = "当前页数据")
    private List<T> list;

    @Schema(description = "总条数")
    private long total;

    @Schema(description = "当前页，从 1 开始")
    private int page;

    @Schema(description = "每页条数")
    private int size;

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        return new PageResult<>(list, total, page, size);
    }
}
