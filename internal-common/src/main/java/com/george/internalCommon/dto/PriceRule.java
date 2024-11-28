package com.george.internalCommon.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * This class contains information related to the pricing rule of the orders
 * @author george
 * @since 2024-11-12
 */
@Data
public class PriceRule implements Serializable {

    private static final long serialVersionUID = 1L;

    private String cityCode;

    private String vehicleType;

    private Double startFare;

    private Integer startMile;

    private Double unitPricePerMile;

    private Double unitPricePerMinute;

    private String fareType;

    private Integer fareVersion;

}
