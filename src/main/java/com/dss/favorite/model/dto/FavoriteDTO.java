package com.dss.favorite.model.dto;

import com.dss.favorite.model.enums.TargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "收藏")
public class FavoriteDTO {

    @NotNull(message = "targetType 不能为空")
    @Schema(description = "SHOP 店铺 / PRODUCT 商品", example = "SHOP")
    private TargetType targetType;

    @NotBlank(message = "targetId 不能为空")
    @Pattern(regexp = "^\\d{1,19}$", message = "targetId 必须是数字")
    @Schema(description = "店铺 ID 或商品 ID（字符串）", example = "101486601357656065")
    private String targetId;
}
