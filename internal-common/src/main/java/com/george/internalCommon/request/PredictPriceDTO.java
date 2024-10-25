package com.george.internalCommon.request;

import lombok.Data;

/**
 * @Author: George Sun
 * @Date: 2024-10-23-20:06
 * @Description: com.george.internalCommon.request
 */

@Data
public class PredictPriceDTO {
    private String depLongitude;
    private String depLatitude;
    private String destLongitude;
    private String destLatitude;
}