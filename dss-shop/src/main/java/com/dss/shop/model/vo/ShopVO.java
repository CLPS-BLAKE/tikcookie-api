package com.dss.shop.model.vo;

import com.dss.shop.model.enums.ShopCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "店铺详情")
public class ShopVO {

    @Schema(description = "店铺 ID（字符串）", example = "101486601357656065")
    private String id;

    @Schema(description = "店名")
    private String name;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "分类")
    private ShopCategory category;

    @Schema(description = "展示图 fileId")
    private List<String> images;

    @Schema(description = "展示图完整 URL，顺序和 images 一致")
    private List<String> imageUrls;

    @Schema(description = "营业时间，可为 null")
    private String businessHours;

    @Schema(description = "电话，可为 null")
    private String phone;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
