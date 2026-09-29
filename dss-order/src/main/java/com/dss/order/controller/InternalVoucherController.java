package com.dss.order.controller;

import com.dss.common.result.Result;
import com.dss.order.model.vo.OrderVO;
import com.dss.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部：按券码模拟核销（请求头 X-Internal-Key）。接口文档 5.8.6。
 */
@Tag(name = "内部：核销")
@RestController
@RequiredArgsConstructor
public class InternalVoucherController {

    private final OrderService orderService;

    @Operation(summary = "核销", description = "待使用且没过期的券才能核销，核销后订单变为已使用")
    @PostMapping("/api/v1/internal/vouchers/{voucherCode}/redeem")
    public Result<OrderVO> redeem(@PathVariable @Pattern(regexp = "^\\d{12}$", message = "券码是 12 位数字") String voucherCode) {
        return Result.ok(orderService.redeem(voucherCode));
    }
}
