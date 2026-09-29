package com.dss.order.model.enums;

import com.dss.common.error.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单模块业务码 104xxx。抢购下单的时间窗、库存、限购错误用商品模块的 103008–103011。
 */
@Getter
@AllArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND(104001, "订单不存在"),
    ORDER_STATUS_INVALID(104002, "订单当前状态不允许此操作"),
    ORDER_PAY_EXPIRED(104003, "订单已超过支付时限"),
    /** 历史预留：旧的内部券码核销接口已移除，本版接口不返回（接口文档第 3 节）。 */
    @Deprecated
    VOUCHER_NOT_FOUND(104004, "历史预留：旧内部券码核销接口已移除"),
    VOUCHER_EXPIRED(104005, "券已过期"),
    ORDER_NOT_USABLE(104006, "订单当前不可使用（已使用、取消或退款等）");

    private final int code;
    private final String msg;
}
