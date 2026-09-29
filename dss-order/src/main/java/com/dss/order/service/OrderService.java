package com.dss.order.service;

import com.dss.common.result.PageResult;
import com.dss.order.model.dto.CreateOrderDTO;
import com.dss.order.model.dto.OrderQuery;
import com.dss.order.model.vo.OrderCreatedVO;
import com.dss.order.model.vo.OrderListItemVO;
import com.dss.order.model.vo.OrderVO;
import com.dss.order.mq.FlashOrderMessage;

/**
 * 订单。规则和状态机见需求文档 6.4 和第 7 节。
 * 所有状态迁移都用"带原状态条件的原子更新"（findAndModify：_id + status = 原状态），并发和重复消息下都幂等。
 * 不是本人的订单一律按"不存在"处理（104001）。
 */
public interface OrderService {

    /**
     * 普通商品下单：商品不存在 103001、已下架 103002、是 FLASH 103013；
     * 创建 UNPAID 订单（ID 用 RedisIdGenerator biz=order；写快照；amount = price；payDeadline = 现在 + dss.order.pay-timeout-minutes），
     * 然后投递超时延迟消息。
     */
    OrderCreatedVO createOrder(Long userId, CreateOrderDTO dto);

    /**
     * 抢购下单：
     * <ol>
     *     <li>商品不存在 103001、已下架 103002、不是 FLASH 103012、未开始 103008、已结束 103009；</li>
     *     <li>执行 lua/flash_order.lua：返回 1 → 103010，返回 2 → 103011；</li>
     *     <li>成功就生成订单 ID，投递 FlashOrderMessage（order.flash.create），立即返回订单 ID。</li>
     * </ol>
     */
    OrderCreatedVO createFlashOrder(Long userId, CreateOrderDTO dto);

    /**
     * 模拟支付：不存在或不是本人 104001；不是 UNPAID 104002；已过 payDeadline 104003。
     * 调 PayService；UNPAID → UNUSED：生成 12 位券码（唯一索引冲突就重新生成）、payChannel=MOCK、payTime、
     * expireTime = payTime + validDays 天；然后 ProductService.changeSoldCount(+1)。
     */
    OrderVO pay(Long userId, Long orderId);

    /**
     * 取消：不存在或不是本人 104001；不是 UNPAID 104002。UNPAID → CANCELLED（USER）。
     * 抢购订单：回补 Mongo 库存（ProductService.increaseFlashStock）、Redis 库存 INCR、已购数 HINCRBY −1。
     */
    OrderVO cancel(Long userId, Long orderId);

    /**
     * 退款：不存在或不是本人 104001；不是 UNUSED 104002。UNUSED → REFUNDED（USER），退款即时成功；
     * ProductService.changeSoldCount(−1)。抢购订单不回补库存，也不减少已购数。
     */
    OrderVO refund(Long userId, Long orderId);

    /**
     * 我的订单：status 可选，按 createTime 倒序分页。
     */
    PageResult<OrderListItemVO> listMyOrders(Long userId, OrderQuery query);

    /**
     * 订单详情：不存在或不是本人（包括还没写库的抢购单）104001；只有 UNUSED、USED 时返回券码。
     */
    OrderVO getMyOrder(Long userId, Long orderId);

    /**
     * 内部核销：券码不存在 104004；不是 UNUSED 104006；已过 expireTime 104005。UNUSED → USED，写 useTime。
     */
    OrderVO redeem(String voucherCode);

    // ---------- MQ 消费者和定时任务调用 ----------

    /**
     * 抢购落单（FlashOrderConsumer 调用）：在一个 Mongo 事务里插入 UNPAID 订单（_id = message.orderId，写快照）
     * 并调 ProductService.decreaseFlashStock；提交后投递超时延迟消息。
     * _id 已存在（重复消息）就直接返回，不当成失败。
     */
    void handleFlashOrderCreate(FlashOrderMessage message);

    /**
     * 超时取消（OrderTimeoutConsumer 调用）：订单仍是 UNPAID 就 → CANCELLED（TIMEOUT），抢购订单按 cancel 回补；
     * 已经不是 UNPAID 的直接忽略。
     */
    void handlePayTimeout(Long orderId);

    /**
     * 兜底（OrderTimeoutFallbackJob 调用）：先拿 Redis 锁 dss:lock:job:order-timeout-fallback，
     * 再把 status=UNPAID 且 payDeadline 已过的订单逐个按 handlePayTimeout 处理。
     *
     * @return 本次取消的订单数
     */
    int cancelOverdueUnpaidOrders();

    /**
     * 过期自动退款（VoucherExpireJob 调用）：先拿 Redis 锁 dss:lock:job:voucher-expire，
     * 再把 status=UNUSED 且 expireTime 已过的订单 → REFUNDED（EXPIRED），已售数 −1。
     *
     * @return 本次退款的订单数
     */
    int refundExpiredVouchers();
}
