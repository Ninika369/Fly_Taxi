package com.george.internalCommon.constant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

/**
 * @Author: George Sun
 * @Date: 2024-10-13-15:22
 * @Description: com.george.internalCommon.constant
 */
@AllArgsConstructor
// Enums to represent the status of returning values
public enum CommonStatus {

    // The response meaning an incorrect verification code, from 1000-1099
    VERIFICATION_ERROR(1099, "Incorrect Verification Code"),

    // Pairs of codes and messages
    SUCCESS(1, "success"),
    FAIL(0, "fail"),

    // Token Error, from 1100-1199
    TOKEN_ERROR(1199, "Wrong Token"),

    // User Error, from 1200-1299
    USER_NOT_EXISTS(1200, "User is not existed"),

    // price rule not exists, from 1300-1399
    PRICE_RULE_NOT_EXISTS(1300, "price rules do not exist")

    ;

    @Getter
    private int code;
    @Getter
    private String message;

}
