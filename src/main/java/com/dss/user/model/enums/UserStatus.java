package com.dss.user.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserStatus {

    NORMAL("正常"),
    DISABLED("禁用");

    private final String desc;
}
