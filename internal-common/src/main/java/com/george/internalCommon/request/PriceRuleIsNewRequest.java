package com.george.internalCommon.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * This class contains variables that are used to determine whether a pricing rule is latest
 */
@Data
@Schema(description = "Request body for checking if a pricing rule version is current")
public class PriceRuleIsNewRequest {

    @Schema(description = "Fare type identifier (cityCode$vehicleType)", example = "110000$1")
    private String fareType;

    @Schema(description = "Version number to check against the latest", example = "1")
    private Integer fareVersion;
}
