package com.geroge.apipassenger.service;

import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.PredictPriceDTO;
import com.george.internalCommon.response.PredictPriceResponse;
import com.geroge.apipassenger.remote.ServicePriceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Author: George Sun
 * @Date: 2024-10-23-20:14
 * @Description: com.geroge.apipassenger.service
 */
@Service
@Slf4j
public class PredictPriceService {

    @Autowired
    ServicePriceClient client;

    /**
     * This function is used to predict the price of a journey according to the latitude and l
     * ongitude of origin and destination.
     * @param depLatitude - the latitude of origin
     * @param depLongitude - the longitude of origin
     * @param destLatitude - the latitude of destination
     * @param destLongitude - the longitude of destination
     * @return
     */
    public ResponseResult predictPrice(String depLatitude, String depLongitude,
                                       String destLatitude, String destLongitude) {

        PredictPriceDTO priceDTO = new PredictPriceDTO();
        priceDTO.setDepLongitude(depLongitude);
        priceDTO.setDepLatitude(depLatitude);
        priceDTO.setDestLongitude(destLongitude);
        priceDTO.setDestLatitude(destLatitude);

        // call calculation function to get predicted price
        ResponseResult responseResult = client.predictPrice(priceDTO);

        return responseResult;
    }
}
