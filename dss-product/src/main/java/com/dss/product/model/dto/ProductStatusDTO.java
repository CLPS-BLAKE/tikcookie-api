package com.dss.product.model.dto;

import com.dss.product.model.enums.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "上下架")
public class ProductStatusDTO {

    @NotNull(message = "状态不能为空")
    @Schema(description = "ON_SHELF 上架 / OFF_SHELF 下架", example = "OFF_SHELF")
    private ProductStatus status;
}
