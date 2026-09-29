package com.dss.order.model.vo;

import com.dss.order.model.enums.OrderStatus;
import com.dss.product.model.enums.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "订单列表项")
public class OrderListItemVO {

    @Schema(description = "订单 ID（字符串）")
    private String id;

    @Schema(description = "商品类型")
    private ProductType productType;

    @Schema(description = "快照：商品名")
    private String productName;

    @Schema(description = "快照：商品图完整 URL")
    private String productImageUrl;

    @Schema(description = "快照：店名")
    private String shopName;

    @Schema(description = "订单金额（分）")
    private Long amount;

    @Schema(description = "订单状态")
    private OrderStatus status;

    @Schema(description = "下单时间")
    private LocalDateTime createTime;

    @Schema(description = "支付截止时间")
    private LocalDateTime payDeadline;
}
