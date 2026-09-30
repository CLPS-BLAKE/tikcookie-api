package com.dss.order.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "下单结果（普通和抢购共用；抢购事务提交后订单已入库，返回的订单 ID 立即可查）")
public class OrderCreatedVO {

    @Schema(description = "订单 ID（字符串）", example = "101487012379131905")
    private String orderId;
}
