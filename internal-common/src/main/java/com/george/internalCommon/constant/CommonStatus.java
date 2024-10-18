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

    // The response meaning an incorrect verification code
    VERIFICATION_ERROR(1099, "Incorrect Verification Code"),

    // Pairs of codes and messages
    SUCCESS(1, "success"),
    FAIL(0, "fail")

    ;

    @Getter
    private int code;
    @Getter
    private String message;

}
