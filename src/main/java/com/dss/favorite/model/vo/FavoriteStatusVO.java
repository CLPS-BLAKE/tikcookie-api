package com.dss.favorite.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "收藏状态")
public class FavoriteStatusVO {

    @Schema(description = "是否已收藏", example = "true")
    private boolean favorited;
}
