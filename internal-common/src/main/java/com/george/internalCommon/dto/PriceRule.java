package com.george.internalCommon.dto;

import lombok.Data;

/**
 * @Author: George Sun
 * @Date: 2024-10-25-19:55
 * @Description: Ths class used to connect with dataset and containing each element of the table
 */
@Data
public class PriceRule {

    private String cityCode;

    private String vehicleType;

    private Double startFare;

    private Integer startMile;

    private Double unitPricePerMile;

    private Double unitPricePerMinute;
}
