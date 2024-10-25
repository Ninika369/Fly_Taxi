package com.geroge.apipassenger.service;

import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.PredictPriceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Author: George Sun
 * @Date: 2024-10-23-20:14
 * @Description: com.geroge.apipassenger.service
 */
@Service
@Slf4j
public class PredictPriceService {

    /**
     * This function is used to predict the price of a journey according to the latitude and l
     * ongitude of origin and destination.
     * @param depLatitude
     * @param depLongitude
     * @param destLatitude
     * @param destLongitude
     * @return
     */
    public ResponseResult predictPrice(String depLatitude, String depLongitude,
                                       String destLatitude, String destLongitude) {

        log.info("origin latitude "+depLatitude);

        // call calculation function to get predicted price

        PredictPriceResponse response = new PredictPriceResponse();
        response.setPrice(21.34);
        return ResponseResult.success(response);
    }
}
