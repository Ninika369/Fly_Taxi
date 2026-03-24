package com.george.internalCommon.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Author: George Sun
 * @Date: 2024-10-23-20:06
 * @Description: This class stores the information needed to predict the price of a journey
 */

@Data
@Schema(description = "Request body for ride price prediction")
public class PredictPriceDTO {
    @Schema(description = "Departure longitude", example = "116.481028")
    private String depLongitude;
    @Schema(description = "Departure latitude", example = "39.989643")
    private String depLatitude;
    @Schema(description = "Destination longitude", example = "116.434446")
    private String destLongitude;
    @Schema(description = "Destination latitude", example = "39.90816")
    private String destLatitude;

    @Schema(description = "City code (e.g. '110000' for Beijing)", example = "110000")
    private String cityCode;
    @Schema(description = "Vehicle type code", example = "1")
    private String vehicleType;
}
