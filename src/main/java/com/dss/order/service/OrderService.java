package com.dss.order.service;

import com.dss.common.result.PageResult;
import com.dss.order.model.dto.CreateOrderDTO;
import com.dss.order.model.dto.OrderQuery;
import com.dss.order.model.vo.OrderCreatedVO;
import com.dss.order.model.vo.OrderListItemVO;
import com.dss.order.model.vo.OrderVO;

/**
 * 订单。规则见需求文档 4.2、中间件配置 3.3；"去使用"见接口文档 5.6.8。
 * <ul>
 *     <li>下单、支付、取消、退款、去使用都是 @Transactional：订单状态、库存、已售数在同一个 MySQL 事务里变；</li>
 *     <li>状态迁移都用带原状态条件的 UPDATE（id + status = 原状态），更新行数不为 1 就按状态不符处理，
 *     重复点击、定时任务重复处理都不会重复加减；</li>
 *     <li>不是本人的订单一律按"不存在"处理（104001）；</li>
 *     <li>RabbitMQ 不参与订单的正确性，已售数变化只在提交后通知搜索（product.changed）。</li>
 * </ul>
 */
public interface OrderService {

    /**
     * 普通商品下单：商品不存在 103001、已下架 103002、是 FLASH 103013；
     * 插入 UNPAID 订单（ID 自增；写 4 个快照字段；amount = price；payDeadline = 现在 + dss.order.pay-timeout-minutes）。
     */
    OrderCreatedVO createOrder(Long userId, CreateOrderDTO dto);

    /**
     * 抢购下单，在一个短事务里完成，提交后返回的订单 ID 立即可查：
     * <ol>
     *     <li>ProductService.lockProduct 锁商品行（SELECT … FOR UPDATE）；不存在 103001、已下架 103002、不是 FLASH 103012；</li>
     *     <li>未开始 103008、已结束 103009；</li>
     *     <li>该用户在这个商品上未取消的订单数（UNPAID / UNUSED / USED / REFUNDED）≥ limitPerUser：103011；</li>
     *     <li>ProductService.decreaseFlashStock 返回 false：103010；</li>
     *     <li>插入 UNPAID 订单（同普通下单）。</li>
     * </ol>
     */
    OrderCreatedVO createFlashOrder(Long userId, CreateOrderDTO dto);

    /**
     * 模拟支付：不存在或不是本人 104001；不是 UNPAID 104002；已过 payDeadline 104003（不等定时任务，当场拒绝）。
     * 调 PayService；UNPAID → UNUSED：生成 12 位随机数字券码、payChannel=MOCK、paidAt、expiresAt = paidAt + validDays 天；
     * 同一事务里 ProductService.changeSoldCount(+1)。券码撞上 uk_orders_voucher_code 时回滚，在新事务里重试。
     */
    OrderVO pay(Long userId, Long orderId);

    /**
     * 取消：不存在或不是本人 104001；不是 UNPAID 104002。UNPAID → CANCELLED（USER）。
     * 抢购订单：同一事务里先 lockProduct，订单确实从 UNPAID 改成功了才 increaseFlashStock；
     * 限购名额随订单变成 CANCELLED 自动释放。
     */
    OrderVO cancel(Long userId, Long orderId);

    /**
     * 退款：不存在或不是本人 104001；不是 UNUSED 104002。UNUSED → REFUNDED（USER），退款即时成功（模拟）；
     * 同一事务里 changeSoldCount(−1)。抢购订单不回补库存，限购名额也不释放。
     */
    OrderVO refund(Long userId, Long orderId);

    /**
     * 我的订单：status 可选，按 createdAt 倒序分页。
     */
    PageResult<OrderListItemVO> listMyOrders(Long userId, OrderQuery query);

    /**
     * 订单详情：不存在或不是本人 104001；只有 UNUSED、USED 时返回券码。
     */
    OrderVO getMyOrder(Long userId, Long orderId);

    /**
     * 去使用（本人订单，教学模拟核销）：不存在或不是本人 104001；已过 expiresAt 104005；不是 UNUSED 104006。
     * UNUSED → USED（条件：status = UNUSED 且 expires_at 晚于现在），写 usedAt，返回更新后的订单。
     * 重复点击只有第一次成功，退款和使用也只会有一个成功。接口不接收券码，券码不能代替登录和归属校验。
     */
    OrderVO use(Long userId, Long orderId);

    // ---------- 定时任务调用（每分钟，单实例，不加锁） ----------

    /**
     * 超时取消（OrderTimeoutJob 调用）：分批（每批最多 100 条）取 status = UNPAID 且 pay_deadline ≤ 现在 的订单，
     * 逐个调用和用户取消同一个事务方法（原因 TIMEOUT）；已经不是 UNPAID 的跳过。
     *
     * @return 本次取消的订单数
     */
    int cancelOverdueUnpaidOrders();

    /**
     * 到期退款（VoucherExpireJob 调用）：分批取 status = UNUSED 且 expires_at ≤ 现在 的订单，
     * 逐个 → REFUNDED（EXPIRED），已售数 −1；已经不是 UNUSED 的跳过。
     *
     * @return 本次退款的订单数
     */
    int refundExpiredVouchers();
}
