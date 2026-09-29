package com.dss.order.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单状态。最初设计里的"已支付"并入 UNUSED（付完即出券码），新增 REFUNDED。
 * 迁移：UNPAID → UNUSED → USED；UNPAID → CANCELLED；UNUSED → REFUNDED。USED、CANCELLED、REFUNDED 是终态。
 */
@Getter
@AllArgsConstructor
public enum OrderStatus {

    UNPAID("待支付"),
    UNUSED("待使用"),
    USED("已使用"),
    CANCELLED("已取消"),
    REFUNDED("已退款");

    private final String desc;
}
