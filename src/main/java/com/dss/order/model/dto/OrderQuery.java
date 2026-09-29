package com.dss.order.model.dto;

import com.dss.common.result.PageQuery;
import com.dss.order.model.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 我的订单查询参数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderQuery extends PageQuery {

    @Schema(description = "订单状态，不传就是全部", example = "UNUSED")
    private OrderStatus status;
}
