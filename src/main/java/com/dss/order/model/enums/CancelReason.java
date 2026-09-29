package com.dss.order.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CancelReason {

    USER("用户取消"),
    TIMEOUT("超时未支付");

    private final String desc;
}
