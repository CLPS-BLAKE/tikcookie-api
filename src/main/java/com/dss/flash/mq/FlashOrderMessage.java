package com.dss.flash.mq;

public record FlashOrderMessage(String requestId, long userId, long productId, String orderId) {}
