package com.george.internalCommon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * This class contains information related to the pricing rule of the orders
 * @author george
 * @since 2024-11-12
 */
@Data
@Schema(description = "Pricing rule defining fares for a specific city and vehicle type")
public class PriceRule implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "City code", example = "110000")
    private String cityCode;

    @Schema(description = "Vehicle type code", example = "1")
    private String vehicleType;

    @Schema(description = "Base fare charged at ride start", example = "10.0")
    private Double startFare;

    @Schema(description = "Free distance in km before extra mileage charge", example = "3")
    private Integer startMile;

    @Schema(description = "Price per km beyond the free distance", example = "2.0")
    private Double unitPricePerMile;

    @Schema(description = "Price per minute of ride duration", example = "0.5")
    private Double unitPricePerMinute;

    @Schema(description = "Fare type identifier (cityCode$vehicleType)", example = "110000$1")
    private String fareType;

    @Schema(description = "Rule version number (auto-incremented on edit)", example = "1")
    private Integer fareVersion;

}
