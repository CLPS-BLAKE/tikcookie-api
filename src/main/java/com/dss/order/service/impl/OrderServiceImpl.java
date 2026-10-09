package com.dss.order.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dss.common.config.DssProperties;
import com.dss.common.error.CommonErrorCode;
import com.dss.common.exception.BizException;
import com.dss.common.file.FileUrlResolver;
import com.dss.common.result.PageResult;
import com.dss.order.mapper.OrderMapper;
import com.dss.order.model.dto.CreateOrderDTO;
import com.dss.order.model.dto.OrderQuery;
import com.dss.order.model.entity.Order;
import com.dss.order.model.enums.CancelReason;
import com.dss.order.model.enums.OrderErrorCode;
import com.dss.order.model.enums.OrderStatus;
import com.dss.order.model.enums.PayChannel;
import com.dss.order.model.enums.RefundReason;
import com.dss.order.model.vo.OrderCreatedVO;
import com.dss.order.model.vo.OrderListItemVO;
import com.dss.order.model.vo.OrderVO;
import com.dss.order.service.OrderService;
import com.dss.order.service.PayService;
import com.dss.product.model.entity.Product;
import com.dss.product.model.enums.ProductErrorCode;
import com.dss.product.model.enums.ProductStatus;
import com.dss.product.model.enums.ProductType;
import com.dss.product.service.ProductService;
import com.dss.shop.model.entity.Shop;
import com.dss.shop.service.ShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单实现。规则见需求文档 4.2、中间件配置 3.3，"去使用"见接口文档 5.6.8。
 * <p>
 * 所有会改数据的路径都在事务里，状态迁移用"id + status = 原状态"的条件 UPDATE，
 * 更新行数不为 1 就按状态已变处理：重复点击、权限竞争、定时任务重复处理都不会重复加减。
 * <p>
 * 加锁顺序统一为"先商品行（SELECT … FOR UPDATE）再订单行"，支付 / 取消 / 退款 / 抢购下单一致，
 * 避免商品与订单行交叉加锁造成死锁。
 */
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    /** 定时任务单批条数上限（中间件配置 3.3）。 */
    private static final int JOB_BATCH_SIZE = 100;
    /** 定时任务每轮最多扫过的批数，防止异常数据让循环空转。 */
    private static final int JOB_MAX_BATCHES = 20;
    private static final int VOUCHER_CODE_LENGTH = 12;
    private static final String VOUCHER_CODE_ALPHABET = "0123456789";
    /** 券码按唯一索引撞号后，新事务里最多重试的次数。 */
    private static final int VOUCHER_CODE_MAX_ATTEMPTS = 3;

    private final OrderMapper orderMapper;
    private final ProductService productService;
    private final ShopService shopService;
    private final PayService payService;
    private final DssProperties properties;
    private final FileUrlResolver fileUrlResolver;
    /** 券码撞号后用的"新事务"：支付重试必须跳过已回滚的旧事务。 */
    private final TransactionTemplate voucherRetryTemplate;
    /** 定时任务逐条提交用的事务模板：每条都是独立事务，失败不影响其他条。 */
    private final TransactionTemplate unitOfWorkTemplate;
    private final SecureRandom random = new SecureRandom();

    public OrderServiceImpl(OrderMapper orderMapper, ProductService productService, ShopService shopService,
                            PayService payService, DssProperties properties, FileUrlResolver fileUrlResolver,
                            PlatformTransactionManager transactionManager) {
        this.orderMapper = orderMapper;
        this.productService = productService;
        this.shopService = shopService;
        this.payService = payService;
        this.properties = properties;
        this.fileUrlResolver = fileUrlResolver;
        this.voucherRetryTemplate = new TransactionTemplate(transactionManager);
        this.voucherRetryTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.unitOfWorkTemplate = new TransactionTemplate(transactionManager);
    }

    // ---------- 下单 ----------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCreatedVO createOrder(Long userId, CreateOrderDTO dto) {
        Long productId = parseId(dto.getProductId(), "productId");
        Product product = productService.getRequiredProduct(productId);
        if (product.getStatus() != ProductStatus.ON_SHELF) {
            throw new BizException(ProductErrorCode.PRODUCT_OFF_SHELF);
        }
        if (product.getType() == ProductType.FLASH) {
            throw new BizException(ProductErrorCode.NOT_NORMAL_PRODUCT);
        }
        Shop shop = shopService.getRequiredShop(product.getShopId());
        Order order = buildOrder(userId, product, shop);
        orderMapper.insert(order);
        return new OrderCreatedVO(String.valueOf(order.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCreatedVO createFlashOrder(Long userId, CreateOrderDTO dto) {
        Long productId = parseId(dto.getProductId(), "productId");
        // 第一个锁步骤：锁商品行，抢购下单、支付、取消、退款保持同一把加锁顺序
        Product product = productService.lockProduct(productId);
        if (product.getStatus() != ProductStatus.ON_SHELF) {
            throw new BizException(ProductErrorCode.PRODUCT_OFF_SHELF);
        }
        if (product.getType() != ProductType.FLASH) {
            throw new BizException(ProductErrorCode.NOT_FLASH_PRODUCT);
        }
        LocalDateTime now = LocalDateTime.now();
        if (product.getFlashStartTime() == null || now.isBefore(product.getFlashStartTime())) {
            throw new BizException(ProductErrorCode.FLASH_NOT_STARTED);
        }
        if (product.getFlashEndTime() == null || !now.isBefore(product.getFlashEndTime())) {
            throw new BizException(ProductErrorCode.FLASH_ENDED);
        }
        int limit = product.getLimitPerUser() == null ? 1 : product.getLimitPerUser();
        long purchased = orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getProductId, productId)
                .eq(Order::getUserId, userId)
                .ne(Order::getStatus, OrderStatus.CANCELLED));
        if (purchased >= limit) {
            throw new BizException(ProductErrorCode.FLASH_LIMIT_EXCEEDED);
        }
        // 扣库存和"判断还有没有货"是同一条条件 UPDATE，在商品行的锁后面，不会超卖
        if (!productService.decreaseFlashStock(productId)) {
            throw new BizException(ProductErrorCode.FLASH_SOLD_OUT);
        }
        Shop shop = shopService.getRequiredShop(product.getShopId());
        Order order = buildOrder(userId, product, shop);
        orderMapper.insert(order);
        // 事务提交返回后订单已入库，立即可查（接口文档 5.6.2）
        return new OrderCreatedVO(String.valueOf(order.getId()));
    }

    // ---------- 支付 / 取消 / 退款 / 去使用 ----------

    @Override
    public OrderVO pay(Long userId, Long orderId) {
        // 券码撞唯一索引时整笔回滚，并在新事务里重试（中间件配置 3.3）
        for (int attempt = 1; attempt <= VOUCHER_CODE_MAX_ATTEMPTS; attempt++) {
            try {
                return voucherRetryTemplate.execute(status -> doPay(userId, orderId));
            } catch (DuplicateKeyException e) {
                log.warn("券码撞号，第 {} 次支付重试：orderId={}", attempt, orderId);
            }
        }
        throw new BizException(CommonErrorCode.SYSTEM_ERROR, "券码生成失败，请重试");
    }

    private OrderVO doPay(Long userId, Long orderId) {
        Order order = requireMyOrder(userId, orderId);
        if (order.getStatus() != OrderStatus.UNPAID) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        if (order.getPayDeadline() == null || !order.getPayDeadline().isAfter(now)) {
            throw new BizException(OrderErrorCode.ORDER_PAY_EXPIRED);
        }
        // 先商品行再订单行，和取消 / 退款 / 抢购下单同一把顺序
        Product product = productService.lockProduct(order.getProductId());
        if (!payService.pay(order)) {
            throw new BizException(CommonErrorCode.SYSTEM_ERROR, "支付失败，请重试");
        }
        LocalDateTime paidAt = now;
        LocalDateTime expiresAt = paidAt.plusDays(product.getValidDays());
        String voucherCode = generateVoucherCode();
        int rows = orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderStatus.UNPAID)
                .set(Order::getStatus, OrderStatus.UNUSED)
                .set(Order::getVoucherCode, voucherCode)
                .set(Order::getPayChannel, PayChannel.MOCK)
                .set(Order::getPaidAt, paidAt)
                .set(Order::getExpiresAt, expiresAt)
                .set(Order::getUpdatedAt, paidAt));
        if (rows != 1) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID);
        }
        productService.changeSoldCount(order.getProductId(), 1);
        return toVO(requireOrder(orderId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO cancel(Long userId, Long orderId) {
        Order order = requireMyOrder(userId, orderId);
        if (order.getStatus() != OrderStatus.UNPAID) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID);
        }
        OrderVO canceled = transitionToCancelled(order, CancelReason.USER);
        if (canceled == null) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID);
        }
        return canceled;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO refund(Long userId, Long orderId) {
        Order order = requireMyOrder(userId, orderId);
        if (order.getStatus() != OrderStatus.UNUSED) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID);
        }
        OrderVO refunded = transitionToRefunded(order, RefundReason.USER, null);
        if (refunded == null) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID);
        }
        return refunded;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO use(Long userId, Long orderId) {
        Order order = requireMyOrder(userId, orderId);
        // 已使用 / 已取消 / 已退款 / 未支付：都算"当前不可使用"
        if (order.getStatus() != OrderStatus.UNUSED) {
            throw new BizException(OrderErrorCode.ORDER_NOT_USABLE);
        }
        LocalDateTime now = LocalDateTime.now();
        if (order.getExpiresAt() == null || !order.getExpiresAt().isAfter(now)) {
            throw new BizException(OrderErrorCode.VOUCHER_EXPIRED);
        }
        // 条件更新本身带上 expires_at 的校验：接口即使不等定时任务，过期订单也用不掉
        int rows = orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderStatus.UNUSED)
                .gt(Order::getExpiresAt, now)
                .set(Order::getStatus, OrderStatus.USED)
                .set(Order::getUsedAt, now)
                .set(Order::getUpdatedAt, now));
        if (rows != 1) {
            // 重复点击只有第一次成功；退款和使用只有一方能成功。兜底判断给对二维码
            Order latest = orderMapper.selectById(orderId);
            if (latest != null && latest.getStatus() == OrderStatus.UNUSED) {
                throw new BizException(OrderErrorCode.VOUCHER_EXPIRED);
            }
            throw new BizException(OrderErrorCode.ORDER_NOT_USABLE);
        }
        return toVO(requireOrder(orderId));
    }

    // ---------- 我的订单 ----------

    @Override
    public PageResult<OrderListItemVO> listMyOrders(Long userId, OrderQuery query) {
        var wrapper = Wrappers.<Order>lambdaQuery()
                .eq(Order::getUserId, userId)
                .eq(query.getStatus() != null, Order::getStatus, query.getStatus())
                .orderByDesc(Order::getCreatedAt)
                .orderByDesc(Order::getId);
        Page<Order> page = orderMapper.selectPage(Page.of(query.getPage(), query.getSize()), wrapper);
        List<OrderListItemVO> items = page.getRecords().stream().map(order -> {
            OrderListItemVO vo = new OrderListItemVO();
            vo.setId(String.valueOf(order.getId()));
            vo.setProductType(order.getProductType());
            vo.setProductName(order.getSnapshotProductName());
            vo.setProductImageUrl(fileUrlResolver.toUrl(order.getSnapshotProductImage()));
            vo.setShopName(order.getSnapshotShopName());
            vo.setAmount(order.getAmount());
            vo.setStatus(order.getStatus());
            vo.setCreateTime(order.getCreatedAt());
            vo.setPayDeadline(order.getPayDeadline());
            return vo;
        }).toList();
        return PageResult.of(items, page.getTotal(), query.getPage(), query.getSize());
    }

    @Override
    public OrderVO getMyOrder(Long userId, Long orderId) {
        return toVO(requireMyOrder(userId, orderId));
    }

    // ---------- 定时任务（每分钟，单实例，不加锁） ----------

    @Override
    public int cancelOverdueUnpaidOrders() {
        int total = 0;
        for (int batch = 0; batch < JOB_MAX_BATCHES; batch++) {
            List<Long> ids = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                            .select(Order::getId)
                            .eq(Order::getStatus, OrderStatus.UNPAID)
                            .le(Order::getPayDeadline, LocalDateTime.now())
                            .orderByAsc(Order::getId)
                            .last("LIMIT " + JOB_BATCH_SIZE))
                    .stream()
                    .map(Order::getId)
                    .toList();
            if (ids.isEmpty()) {
                break;
            }
            int done = 0;
            for (Long id : ids) {
                if (Boolean.TRUE.equals(unitOfWorkTemplate.execute(
                        status -> cancelOverdueOne(id)))) {
                    done++;
                }
            }
            total += done;
            if (done == 0) {
                // 这批要么已经被处理要么状态已变，避免空转死循环
                break;
            }
        }
        return total;
    }

    @Override
    public int refundExpiredVouchers() {
        int total = 0;
        for (int batch = 0; batch < JOB_MAX_BATCHES; batch++) {
            List<Long> ids = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                            .select(Order::getId)
                            .eq(Order::getStatus, OrderStatus.UNUSED)
                            .le(Order::getExpiresAt, LocalDateTime.now())
                            .orderByAsc(Order::getId)
                            .last("LIMIT " + JOB_BATCH_SIZE))
                    .stream()
                    .map(Order::getId)
                    .toList();
            if (ids.isEmpty()) {
                break;
            }
            int done = 0;
            for (Long id : ids) {
                if (Boolean.TRUE.equals(unitOfWorkTemplate.execute(
                        status -> refundExpiredOne(id)))) {
                    done++;
                }
            }
            total += done;
            if (done == 0) {
                break;
            }
        }
        return total;
    }

    // ---------- 事务内的状态迁移核心 ----------

    /**
     * UNPAID → CANCELLED（同一事务里回补抢购库存，限购名额随 CANCELLED 自动释放）。
     * 和用户取消走同一个方法，定时任务超时取消复用它。
     *
     * @return null 表示订单已经不是 UNPAID（重复处理，跳过），否则返回取消后的订单
     */
    private OrderVO transitionToCancelled(Order order, CancelReason reason) {
        if (order.getProductType() == ProductType.FLASH) {
            productService.lockProduct(order.getProductId());
        }
        LocalDateTime now = LocalDateTime.now();
        int rows = orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, OrderStatus.UNPAID)
                .set(Order::getStatus, OrderStatus.CANCELLED)
                .set(Order::getCancelledAt, now)
                .set(Order::getCancelReason, reason)
                .set(Order::getUpdatedAt, now));
        if (rows != 1) {
            return null;
        }
        if (order.getProductType() == ProductType.FLASH) {
            // 只有订单真的从 UNPAID 改成功了才回补库存
            productService.increaseFlashStock(order.getProductId());
        }
        return toVO(requireOrder(order.getId()));
    }

    /**
     * UNUSED → REFUNDED。用户退款即时成功；到期退款多带"expires_at 已过"的条件。
     * 已售数 −1；抢购不回补库存、不释放限购名额（需求文档 4.2）。
     *
     * @return null 表示订单已经不是 UNUSED（重复处理，跳过），否则返回退款后的订单
     */
    private OrderVO transitionToRefunded(Order order, RefundReason reason, LocalDateTime expiredBefore) {
        productService.lockProduct(order.getProductId());
        LocalDateTime now = LocalDateTime.now();
        var update = Wrappers.<Order>lambdaUpdate()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, OrderStatus.UNUSED)
                .gt(expiredBefore != null, Order::getExpiresAt, expiredBefore)
                .set(Order::getStatus, OrderStatus.REFUNDED)
                .set(Order::getRefundedAt, now)
                .set(Order::getRefundReason, reason)
                .set(Order::getUpdatedAt, now);
        int rows = orderMapper.update(null, update);
        if (rows != 1) {
            return null;
        }
        productService.changeSoldCount(order.getProductId(), -1);
        return toVO(requireOrder(order.getId()));
    }

    private boolean cancelOverdueOne(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || order.getStatus() != OrderStatus.UNPAID) {
            return false;
        }
        return transitionToCancelled(order, CancelReason.TIMEOUT) != null;
    }

    private boolean refundExpiredOne(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || order.getStatus() != OrderStatus.UNUSED) {
            return false;
        }
        return transitionToRefunded(order, RefundReason.EXPIRED, LocalDateTime.now()) != null;
    }

    // ---------- 内部工具 ----------

    private Order buildOrder(Long userId, Product product, Shop shop) {
        Order order = new Order();
        order.setUserId(userId);
        order.setProductId(product.getId());
        order.setShopId(product.getShopId());
        order.setProductType(product.getType());
        order.setSnapshotProductName(product.getName());
        order.setSnapshotProductImage(product.getImage());
        order.setSnapshotShopName(shop.getName());
        order.setSnapshotPrice(product.getPrice());
        order.setAmount(product.getPrice());
        order.setStatus(OrderStatus.UNPAID);
        order.setPayDeadline(LocalDateTime.now().plusMinutes(properties.getOrder().getPayTimeoutMinutes()));
        return order;
    }

    /** 本人订单，不存在或不是本人的一律按 104001 处理。 */
    @Override
    public Order requireMyOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId));
        if (order == null) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    /** 仅按 ID 取订单，调用方保证它在事务内已存在。 */
    private Order requireOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    /** 12 位随机数字券码。开头可以是 0：CHAR(12) 会保留，读取时也是完整 12 位字符串。 */
    private String generateVoucherCode() {
        StringBuilder sb = new StringBuilder(VOUCHER_CODE_LENGTH);
        for (int i = 0; i < VOUCHER_CODE_LENGTH; i++) {
            sb.append(VOUCHER_CODE_ALPHABET.charAt(random.nextInt(VOUCHER_CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** 给 VO 填字段。券码只在待使用 / 已使用的时候输出。 */
    private OrderVO toVO(Order order) {
        OrderVO vo = new OrderVO();
        vo.setId(String.valueOf(order.getId()));
        vo.setProductId(String.valueOf(order.getProductId()));
        vo.setShopId(String.valueOf(order.getShopId()));
        vo.setProductType(order.getProductType());
        vo.setProductName(order.getSnapshotProductName());
        vo.setProductImageUrl(fileUrlResolver.toUrl(order.getSnapshotProductImage()));
        vo.setShopName(order.getSnapshotShopName());
        vo.setAmount(order.getAmount());
        vo.setStatus(order.getStatus());
        boolean showVoucher = order.getStatus() == OrderStatus.UNUSED || order.getStatus() == OrderStatus.USED;
        vo.setVoucherCode(showVoucher ? order.getVoucherCode() : null);
        vo.setPayChannel(order.getPayChannel());
        vo.setCreateTime(order.getCreatedAt());
        vo.setPayDeadline(order.getPayDeadline());
        vo.setPayTime(order.getPaidAt());
        vo.setExpireTime(order.getExpiresAt());
        vo.setUseTime(order.getUsedAt());
        vo.setCancelTime(order.getCancelledAt());
        vo.setCancelReason(order.getCancelReason());
        vo.setRefundTime(order.getRefundedAt());
        vo.setRefundReason(order.getRefundReason());
        return vo;
    }

    private Long parseId(String raw, String field) {
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, field + " 必须是数字");
        }
    }
}