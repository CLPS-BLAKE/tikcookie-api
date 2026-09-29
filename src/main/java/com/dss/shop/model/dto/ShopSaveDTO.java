package com.dss.shop.model.dto;

import com.dss.shop.model.enums.ShopCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "新建 / 修改店铺（修改是整体覆盖）")
public class ShopSaveDTO {

    @NotBlank(message = "店名不能为空")
    @Size(max = 50, message = "店名最多 50 字")
    @Schema(description = "店名", example = "老王牛肉面（天河店）")
    private String name;

    @NotBlank(message = "地址不能为空")
    @Size(max = 200, message = "地址最多 200 字")
    @Schema(description = "地址", example = "广州市天河区体育西路 100 号")
    private String address;

    @NotNull(message = "分类不能为空")
    @Schema(description = "分类", example = "FOOD")
    private ShopCategory category;

    @NotEmpty(message = "至少一张展示图")
    @Size(max = 9, message = "最多 9 张展示图")
    @Schema(description = "展示图 fileId，1–9 张")
    private List<@NotBlank(message = "展示图 fileId 不能为空") String> images;

    @Size(max = 50, message = "营业时间最多 50 字")
    @Schema(description = "营业时间（选填）", example = "10:00-22:00")
    private String businessHours;

    @Pattern(regexp = "^[0-9+\\- ]{5,20}$", message = "电话格式不正确")
    @Schema(description = "电话（选填）", example = "020-12345678")
    private String phone;
}
