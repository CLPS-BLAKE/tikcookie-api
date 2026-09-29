package com.dss.order.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 下单时复制进订单的商品信息（嵌在 orders.snapshot 里）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderSnapshot {

    private String productName;

    /** 商品图 fileId。 */
    private String productImage;

    private String shopName;

    /** 下单时的金额（分）。 */
    private Long price;
}
