package com.dss.product.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 套餐内容里的一项。不带单价：商品只用一个金额。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "套餐内容项")
public class ContentItem {

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 50, message = "项目名称最多 50 字")
    @Schema(description = "名称", example = "招牌牛肉面")
    private String name;

    @NotNull(message = "份数不能为空")
    @Min(value = 1, message = "份数至少为 1")
    @Schema(description = "份数", example = "2")
    private Integer count;
}
