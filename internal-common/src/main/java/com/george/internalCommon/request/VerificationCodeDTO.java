package com.george.internalCommon.request;

import lombok.Data;

/**
 * @Author: George Sun
 * @Date: 2024-10-12-19:11
 * @Description: This class used to store the phone number of clients
 */

@Data
public class VerificationCodeDTO {

    private String passengerPhone;

    private String verificationCode;

    private String driverPhone;
}
