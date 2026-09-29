package com.dss.order.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dss.order.model.enums.CancelReason;
import com.dss.order.model.enums.OrderStatus;
import com.dss.order.model.enums.PayChannel;
import com.dss.order.model.enums.RefundReason;
import com.dss.product.model.enums.ProductType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单（表 orders）。一单一件商品、一张券；"已支付"并入"待使用"。表结构见 dss-init.sql。
 */
@Data
@TableName("orders")
public class Order {

    /** 订单 ID，MySQL 自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long productId;

    private Long shopId;

    /** 下单时的商品类型。 */
    private ProductType productType;

    // ---------- 下单时的快照，商品之后再改也不影响 ----------

    private String snapshotProductName;

    /** 商品图 fileId（OSS ObjectKey）。 */
    private String snapshotProductImage;

    private String snapshotShopName;

    /** 下单时的金额（分）。 */
    private Long snapshotPrice;

    /** 订单金额（分）= snapshotPrice。 */
    private Long amount;

    private OrderStatus status;

    /** 12 位数字券码，支付时生成，全局唯一（uk_orders_voucher_code）；只用于展示，不能凭它操作订单。 */
    private String voucherCode;

    private PayChannel payChannel;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 支付截止 = 下单时间 + dss.order.pay-timeout-minutes（默认 15 分钟）。 */
    private LocalDateTime payDeadline;

    private LocalDateTime paidAt;

    /** 券到期 = paidAt + validDays 天；到期未用自动退款。 */
    private LocalDateTime expiresAt;

    /** 用户点"去使用"的时间。 */
    private LocalDateTime usedAt;

    private LocalDateTime cancelledAt;

    private CancelReason cancelReason;

    private LocalDateTime refundedAt;

    private RefundReason refundReason;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
