package com.dss.order.service.impl;

import com.dss.common.config.DssProperties;
import com.dss.common.exception.NotImplementedException;
import com.dss.common.file.FileUrlResolver;
import com.dss.common.id.RedisIdGenerator;
import com.dss.common.result.PageResult;
import com.dss.order.model.dto.CreateOrderDTO;
import com.dss.order.model.dto.OrderQuery;
import com.dss.order.model.vo.OrderCreatedVO;
import com.dss.order.model.vo.OrderListItemVO;
import com.dss.order.model.vo.OrderVO;
import com.dss.order.mq.FlashOrderMessage;
import com.dss.order.mq.OrderMessageSender;
import com.dss.order.repository.OrderRepository;
import com.dss.order.service.OrderService;
import com.dss.order.service.PayService;
import com.dss.product.service.ProductService;
import com.dss.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 订单实现（骨架期是桩）。
 * 抢购用到的 Lua 脚本在 resources/lua/flash_order.lua（只有契约注释），实现时用 DefaultRedisScript 加载。
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final ShopService shopService;
    private final PayService payService;
    private final OrderMessageSender orderMessageSender;
    private final RedisIdGenerator idGenerator;
    private final StringRedisTemplate redisTemplate;
    private final MongoTemplate mongoTemplate;
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
    public OrderVO redeem(String voucherCode) {
        throw new NotImplementedException();
    }

    @Override
    public void handleFlashOrderCreate(FlashOrderMessage message) {
        throw new NotImplementedException();
    }

    @Override
    public void handlePayTimeout(Long orderId) {
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
