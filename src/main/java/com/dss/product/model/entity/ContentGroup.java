package com.dss.product.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 套餐内容分组，如 {title: "主食", items: [{name: "招牌牛肉面", count: 2}]}。
 * 最初设计是嵌套数组 [[title,[dish...]]]，映射不成 Java 类，所以改成对象数组（存在 products.contents 这个 JSON 列里）；
 * 叫"套餐内容"而不是"菜品"，是为了让休闲娱乐等非餐饮套餐也能用。请求和响应共用这个类。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "套餐内容分组")
public class ContentGroup {

    @NotBlank(message = "分组标题不能为空")
    @Size(max = 30, message = "分组标题最多 30 字")
    @Schema(description = "分组标题", example = "主食")
    private String title;

    @NotEmpty(message = "分组里至少一项")
    @Valid
    @Schema(description = "分组里的项目")
    private List<ContentItem> items;
}
