package com.george.internalCommon.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author: George Sun
 * @Date: 2024-10-18-20:45
 * @Description: com.george.internalCommon.constant
 */
@AllArgsConstructor
public enum UserIdentity {
    PASSENGER("1"),
    DRIVER("2");

    @Getter
    private String identity;
}
