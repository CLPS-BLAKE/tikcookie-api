package com.dss.shop.model.vo;

import com.dss.shop.model.enums.ShopCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分类")
public class CategoryVO {

    @Schema(description = "分类编码", example = "FOOD")
    private ShopCategory code;

    @Schema(description = "中文名", example = "餐饮美食")
    private String name;
}
