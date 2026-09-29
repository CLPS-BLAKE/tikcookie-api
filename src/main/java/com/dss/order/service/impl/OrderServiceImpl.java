package com.dss.order.service.impl;

import com.dss.common.config.DssProperties;
import com.dss.common.exception.NotImplementedException;
import com.dss.common.file.FileUrlResolver;
import com.dss.common.result.PageResult;
import com.dss.order.mapper.OrderMapper;
import com.dss.order.model.dto.CreateOrderDTO;
import com.dss.order.model.dto.OrderQuery;
import com.dss.order.model.vo.OrderCreatedVO;
import com.dss.order.model.vo.OrderListItemVO;
import com.dss.order.model.vo.OrderVO;
import com.dss.order.service.OrderService;
import com.dss.order.service.PayService;
import com.dss.product.service.ProductService;
import com.dss.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 订单实现（骨架期是桩）。
 * 实现时：会改数据的方法加 @Transactional；券码撞号要在新事务里重试（在事务外循环，或 Propagation.REQUIRES_NEW）。
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final ProductService productService;
    private final ShopService shopService;
    private final PayService payService;
    private final DssProperties properties;
    private final FileUrlResolver fileUrlResolver;

    @Override
    public OrderCreatedVO createOrder(Long userId, CreateOrderDTO dto) {
        throw new NotImplementedException();
    }

    @Override
    public OrderCreatedVO createFlashOrder(Long userId, CreateOrderDTO dto) {
        throw new NotImplementedException();
    }

    @Override
    public OrderVO pay(Long userId, Long orderId) {
        throw new NotImplementedException();
    }

    @Override
    public OrderVO cancel(Long userId, Long orderId) {
        throw new NotImplementedException();
    }

    @Override
    public OrderVO refund(Long userId, Long orderId) {
        throw new NotImplementedException();
    }

    @Override
    public PageResult<OrderListItemVO> listMyOrders(Long userId, OrderQuery query) {
        throw new NotImplementedException();
    }

    @Override
    public OrderVO getMyOrder(Long userId, Long orderId) {
        throw new NotImplementedException();
    }

    @Override
    public OrderVO use(Long userId, Long orderId) {
        throw new NotImplementedException();
    }

    @Override
    public int cancelOverdueUnpaidOrders() {
        throw new NotImplementedException();
    }

    @Override
    public int refundExpiredVouchers() {
        throw new NotImplementedException();
    }
}
