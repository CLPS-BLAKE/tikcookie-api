package com.dss.product.model.dto;

import com.dss.product.model.entity.ContentGroup;
import com.dss.product.model.enums.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 新建 / 修改商品（修改是整体覆盖）。
 * "FLASH 时 stock、flashStartTime、flashEndTime 必填"属于条件必填，在 Service 里校验（103005 / 103006）。
 */
@Data
@Schema(description = "新建 / 修改商品")
public class ProductSaveDTO {

    @NotBlank(message = "shopId 不能为空")
    @Pattern(regexp = "^\\d{1,19}$", message = "shopId 必须是数字")
    @Schema(description = "所属店铺 ID（字符串），创建后不能改", example = "101486601357656065")
    private String shopId;

    @NotBlank(message = "商品名不能为空")
    @Size(max = 60, message = "商品名最多 60 字")
    @Schema(description = "商品名", example = "双人牛肉面套餐")
    private String name;

    @NotNull(message = "套餐内容不能为 null，没有就传空数组")
    @Valid
    @Schema(description = "套餐内容，可以是空数组")
    private List<ContentGroup> contents;

    @NotNull(message = "金额不能为空")
    @Min(value = 1, message = "金额至少 1 分")
    @Schema(description = "金额（分）", example = "3990")
    private Long price;

    @NotNull(message = "商品类型不能为空")
    @Schema(description = "商品类型，创建后不能改", example = "NORMAL")
    private ProductType type;

    @NotBlank(message = "展示图不能为空")
    @Size(max = 200, message = "展示图 fileId 太长")
    @Schema(description = "展示图 fileId", example = "group1/M00/00/00/product.jpg")
    private String image;

    @NotNull(message = "有效期不能为空")
    @Min(value = 1, message = "有效期 1–365 天")
    @Max(value = 365, message = "有效期 1–365 天")
    @Schema(description = "支付后几天内有效", example = "30")
    private Integer validDays;

    @NotNull(message = "使用规则不能为 null，没有就传空数组")
    @Schema(description = "使用规则，可以是空数组", example = "[\"周末节假日通用\", \"无需预约\"]")
    private List<@NotBlank(message = "使用规则不能为空字符串") @Size(max = 50, message = "每条使用规则最多 50 字") String> useRules;

    // ---------- 以下仅 FLASH ----------

    @Min(value = 1, message = "库存至少为 1")
    @Schema(description = "库存，FLASH 必填", example = "100")
    private Integer stock;

    @Schema(description = "开抢时间，FLASH 必填", example = "2026-10-02 12:00:00")
    private LocalDateTime flashStartTime;

    @Schema(description = "结束时间，FLASH 必填，必须晚于开抢时间", example = "2026-10-02 12:30:00")
    private LocalDateTime flashEndTime;

    @Min(value = 1, message = "每人限购至少为 1")
    @Schema(description = "每人限购，FLASH 不传默认 1", example = "1")
    private Integer limitPerUser;
}
