package com.dss.order.model.entity;

import com.dss.order.model.enums.CancelReason;
import com.dss.order.model.enums.OrderStatus;
import com.dss.order.model.enums.PayChannel;
import com.dss.order.model.enums.RefundReason;
import com.dss.product.model.enums.ProductType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 订单（集合 orders）。一单一件商品、一张券；"已支付"并入"待使用"。字段说明见 docs/中间件配置.md 4.5。
 */
@Data
@Document(collection = "orders")
public class Order {

    /** 订单 ID（biz=order）。抢购单在写库前就生成，重复消息会因主键冲突被忽略。 */
    @Id
    private Long id;

    private Long userId;

    private Long productId;

    private Long shopId;

    /** 下单时的商品类型。 */
    private ProductType productType;

    /** 下单时的快照，商品之后再改也不影响。 */
    private OrderSnapshot snapshot;

    /** 订单金额（分）= snapshot.price。 */
    private Long amount;

    private OrderStatus status;

    /** 12 位券码，支付时生成，全局唯一（uk_voucherCode）。 */
    private String voucherCode;

    private PayChannel payChannel;

    private LocalDateTime createTime;

    /** 支付截止 = createTime + 15 分钟。 */
    private LocalDateTime payDeadline;

    private LocalDateTime payTime;

    /** 券到期 = payTime + validDays 天；到期未用自动退款。 */
    private LocalDateTime expireTime;

    private LocalDateTime useTime;

    private LocalDateTime cancelTime;

    private CancelReason cancelReason;

    private LocalDateTime refundTime;

    private RefundReason refundReason;

    private LocalDateTime updateTime;
}
