package com.dss.favorite.model.dto;

import com.dss.common.result.PageQuery;
import com.dss.favorite.model.enums.TargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 收藏列表查询参数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FavoriteQuery extends PageQuery {

    @NotNull(message = "targetType 不能为空")
    @Schema(description = "SHOP 店铺 / PRODUCT 商品", example = "PRODUCT")
    private TargetType targetType;
}
