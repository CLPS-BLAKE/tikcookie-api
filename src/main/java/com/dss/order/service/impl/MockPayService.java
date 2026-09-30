package com.dss.order.service.impl;

import com.dss.order.model.entity.Order;
import com.dss.order.service.PayService;
import org.springframework.stereotype.Service;

/**
 * 模拟支付：调用即成功（payChannel = MOCK）。以后接支付宝沙箱时再加一个实现。
 */
@Service
public class MockPayService implements PayService {

    @Override
    public boolean pay(Order order) {
        return true;
    }
}