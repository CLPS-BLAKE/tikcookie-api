package com.dss.order.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "下单（普通和抢购共用）")
public class CreateOrderDTO {

    @NotBlank(message = "productId 不能为空")
    @Pattern(regexp = "^\\d{1,19}$", message = "productId 必须是数字")
    @Schema(description = "商品 ID（字符串）", example = "101486725911707649")
    private String productId;
}
