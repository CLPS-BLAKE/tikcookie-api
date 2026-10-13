package com.dss.flash.model;

import java.time.LocalDateTime;
public record FlashAcceptedVO(String orderId, String state, LocalDateTime acceptedAt, LocalDateTime queueDeadline) {}
