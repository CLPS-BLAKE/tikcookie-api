package com.dss.order.model.vo;

import com.dss.order.model.enums.CancelReason;
import com.dss.order.model.enums.OrderStatus;
import com.dss.order.model.enums.PayChannel;
import com.dss.order.model.enums.RefundReason;
import com.dss.product.model.enums.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "订单详情")
public class OrderVO {

    @Schema(description = "订单 ID（字符串）", example = "101487012379131905")
    private String id;

    @Schema(description = "商品 ID（字符串）")
    private String productId;

    @Schema(description = "店铺 ID（字符串）")
    private String shopId;

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

    @Schema(description = "12 位券码，只有待使用、已使用时有值", example = "538201946617")
    private String voucherCode;

    @Schema(description = "支付渠道")
    private PayChannel payChannel;

    @Schema(description = "下单时间")
    private LocalDateTime createTime;

    @Schema(description = "支付截止时间")
    private LocalDateTime payDeadline;

    @Schema(description = "支付时间")
    private LocalDateTime payTime;

    @Schema(description = "券到期时间")
    private LocalDateTime expireTime;

    @Schema(description = "核销时间")
    private LocalDateTime useTime;

    @Schema(description = "取消时间")
    private LocalDateTime cancelTime;

    @Schema(description = "取消原因")
    private CancelReason cancelReason;

    @Schema(description = "退款时间")
    private LocalDateTime refundTime;

    @Schema(description = "退款原因")
    private RefundReason refundReason;
}
