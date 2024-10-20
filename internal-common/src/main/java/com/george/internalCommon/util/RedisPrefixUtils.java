package com.george.internalCommon.util;

/**
 * @Author: George Sun
 * @Date: 2024-10-19-18:22
 * @Description: com.george.internalCommon.util
 */
public class RedisPrefixUtils {

    // This variable represents the prefix of verification code stored in Redis
    public static String verificationCodePrefix = "passenger-verification-code-";

    // This variable represents the prefix of tokens stored in Redis
    public static String tokenPrefix = "token-";

    /**
     * Generate a key using an independent function to facilitate the process
     * @param passengerPhone
     * @return
     */
    public static String generateKey(String passengerPhone) {
        return verificationCodePrefix + passengerPhone;
    }

    /**
     * Generate the key for tokens
     * @param phone - phone number of users
     * @param identity - identity of users, for example, passengers or drivers
     * @return
     */
    public static String generateTokenKey(String phone, String identity) {
        return tokenPrefix + phone + "-" + identity;
    }
}
