package com.dss.flash.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.dss.order.model.enums.OrderStatus;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FlashResultVO(String state, String orderId, OrderStatus orderStatus) {}
