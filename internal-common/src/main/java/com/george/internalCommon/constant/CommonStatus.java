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
    PRICE_RULE_NOT_EXISTS(1300, "Price rules do not exist"),

    // the map returned from Amap is invalid, from 1300-1399
    MAP_DISTRICT_ERROR(1400, "Request map error"),

    // indicates the relationship between the driver and the vehicle from 1500-1599
    DRIVER_CAR_BIND_NOT_EXISTS(1500, "Binding relationship between driver and vehicle does not exist"),

    DRIVER_NOT_EXISTS(1501, "Driver does not exist"),

    DRIVER_CAR_BIND_EXISTS(1502,
            "Binding relationship between driver and vehicle has already existed, please don't rebind"),

    DRIVER_BIND_EXISTS(1503,
            "Driver has already been bound, please don't rebind"),

    CAR_BIND_EXISTS(1504,
            "Car has already been bound, please don't rebind")
    ;

    @Getter
    private int code;
    @Getter
    private String message;

}
