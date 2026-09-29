package com.dss.order.controller;

import com.dss.common.config.OpenApiConfig;
import com.dss.common.context.UserContext;
import com.dss.common.result.PageResult;
import com.dss.common.result.Result;
import com.dss.order.model.dto.CreateOrderDTO;
import com.dss.order.model.dto.OrderQuery;
import com.dss.order.model.vo.OrderCreatedVO;
import com.dss.order.model.vo.OrderListItemVO;
import com.dss.order.model.vo.OrderVO;
import com.dss.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单（全部需要登录）。一单一件商品、一张券。接口文档 5.6（"去使用"是 5.6.8）。
 */
@Tag(name = "订单")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "普通商品下单", description = "同步创建待支付订单，15 分钟内要支付")
    @PostMapping
    public Result<OrderCreatedVO> create(@Valid @RequestBody CreateOrderDTO dto) {
        return Result.ok(orderService.createOrder(UserContext.requireUserId(), dto));
    }

    @Operation(summary = "抢购下单", description = "一个 MySQL 事务里锁商品、校验时间窗 / 库存 / 限购、扣库存并生成订单；返回的订单 ID 立即可查")
    @PostMapping("/flash")
    public Result<OrderCreatedVO> createFlash(@Valid @RequestBody CreateOrderDTO dto) {
        return Result.ok(orderService.createFlashOrder(UserContext.requireUserId(), dto));
    }

    @Operation(summary = "模拟支付", description = "调用即成功；订单变为待使用并生成 12 位券码")
    @PostMapping("/{orderId}/pay")
    public Result<OrderVO> pay(@PathVariable Long orderId) {
        return Result.ok(orderService.pay(UserContext.requireUserId(), orderId));
    }

    @Operation(summary = "取消待支付订单", description = "抢购订单会回补库存并释放限购名额")
    @PostMapping("/{orderId}/cancel")
    public Result<OrderVO> cancel(@PathVariable Long orderId) {
        return Result.ok(orderService.cancel(UserContext.requireUserId(), orderId));
    }

    @Operation(summary = "申请退款", description = "待使用订单随时可退，退款即时成功（模拟）")
    @PostMapping("/{orderId}/refund")
    public Result<OrderVO> refund(@PathVariable Long orderId) {
        return Result.ok(orderService.refund(UserContext.requireUserId(), orderId));
    }

    @Operation(summary = "我的订单", description = "可按状态筛选，按下单时间倒序")
    @GetMapping
    public Result<PageResult<OrderListItemVO>> list(@Valid @ParameterObject OrderQuery query) {
        return Result.ok(orderService.listMyOrders(UserContext.requireUserId(), query));
    }

    @Operation(summary = "订单详情", description = "只能看自己的订单；待使用和已使用时返回券码")
    @GetMapping("/{orderId}")
    public Result<OrderVO> detail(@PathVariable Long orderId) {
        return Result.ok(orderService.getMyOrder(UserContext.requireUserId(), orderId));
    }

    @Operation(summary = "去使用", description = "本人待使用且未到期的订单，点击后立即变为已使用（教学模拟核销）；不接收券码")
    @PostMapping("/{orderId}/use")
    public Result<OrderVO> use(@PathVariable Long orderId) {
        return Result.ok(orderService.use(UserContext.requireUserId(), orderId));
    }
}
