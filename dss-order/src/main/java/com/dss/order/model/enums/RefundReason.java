package com.dss.order.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RefundReason {

    USER("用户申请"),
    EXPIRED("过期自动退");

    private final String desc;
}
