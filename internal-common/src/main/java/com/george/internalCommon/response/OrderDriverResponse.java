package com.george.internalCommon.response;

import lombok.Data;

/**
 * This class contains the associated driver and vehicle information
 */
@Data
public class OrderDriverResponse {

    private Long driverId;

    private String driverPhone;

    private Long carId;

    private String licenseId;

    private String vehicleNo;

    private String vehicleType;
}
