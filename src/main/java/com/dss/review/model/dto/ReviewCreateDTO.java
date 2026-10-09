package com.dss.review.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 提交评价。rating 必填；content、images、anonymous 可选。
 * images 是评价图片 fileId 数组（先调用上传接口取得 fileId），最多 3 张。
 */
@Data
@Schema(description = "提交评价")
public class ReviewCreateDTO {

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低 1 星")
    @Max(value = 5, message = "评分最高 5 星")
    @Schema(description = "评分 1-5 星", example = "5")
    private Integer rating;

    @Size(max = 500, message = "评价最多 500 字")
    @Schema(description = "文字评价，可为空", example = "好吃，分量足")
    private String content;

    @Size(max = 3, message = "最多 3 张图片")
    @Schema(description = "评价图片 fileId 数组，最多 3 张", example = "[\"group1/M00/00/00/xxx.jpg\"]")
    private List<@Size(max = 200, message = "图片 fileId 太长") String> images;

    @Schema(description = "是否匿名，不传默认 false", example = "false")
    private Boolean anonymous;
}
