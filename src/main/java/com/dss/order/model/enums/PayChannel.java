package com.dss.order.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付渠道。本期只有模拟支付；以后接支付宝沙箱再加 ALIPAY_SANDBOX。
 */
@Getter
@AllArgsConstructor
public enum PayChannel {

    MOCK("模拟支付");

    private final String desc;
}
