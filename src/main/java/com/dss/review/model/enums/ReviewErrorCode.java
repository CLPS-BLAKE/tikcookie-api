package com.dss.review.model.enums;

import com.dss.common.error.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 评价模块业务码 108xxx。
 */
@Getter
@AllArgsConstructor
public enum ReviewErrorCode implements ErrorCode {

    REVIEW_ORDER_NOT_USED(108001, "订单核销后才能评价"),
    REVIEW_ALREADY_EXISTS(108002, "已经评价过了");

    private final int code;
    private final String msg;
}
