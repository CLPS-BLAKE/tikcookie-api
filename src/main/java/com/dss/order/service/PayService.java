package com.dss.order.service;

import com.dss.order.model.entity.Order;

/**
 * 支付抽象。本期只有 MockPayService（调用即成功）；以后接支付宝沙箱时加一个实现，并加支付回调接口。
 */
public interface PayService {

    /**
     * 为订单发起支付。
     *
     * @return true 表示支付成功
     */
    boolean pay(Order order);
}
