package com.dss.order.service.impl;

import com.dss.common.exception.NotImplementedException;
import com.dss.order.model.entity.Order;
import com.dss.order.service.PayService;
import org.springframework.stereotype.Service;

/**
 * 模拟支付：实现后直接返回 true（payChannel = MOCK）。骨架期是桩。
 */
@Service
public class MockPayService implements PayService {

    @Override
    public boolean pay(Order order) {
        throw new NotImplementedException();
    }
}
