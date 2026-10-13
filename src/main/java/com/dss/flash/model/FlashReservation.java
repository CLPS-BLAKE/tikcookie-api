package com.dss.flash.model;

public record FlashReservation(String requestId, long userId, long productId, String orderId,
                               long acceptedAt, long queueDeadline, String state, boolean fresh) {}
