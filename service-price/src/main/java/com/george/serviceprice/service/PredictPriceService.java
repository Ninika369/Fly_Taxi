package com.george.serviceprice.service;

import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.PredictPriceDTO;
import com.george.internalCommon.response.DirectionResponse;
import com.george.internalCommon.response.PredictPriceResponse;
import com.george.serviceprice.remote.ServiceMapClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Author: George Sun
 * @Date: 2024-10-24-15:28
 * @Description: com.george.serviceprice.service
 */
@Service
@Slf4j
public class PredictPriceService {

    @Autowired
    ServiceMapClient client;

    public ResponseResult predictPrice(String depLatitude, String depLongitude,
                                       String destLatitude, String destLongitude) {


        PredictPriceDTO priceDTO = new PredictPriceDTO();
        priceDTO.setDepLatitude(depLatitude);
        priceDTO.setDepLongitude(depLongitude);
        priceDTO.setDestLatitude(destLatitude);
        priceDTO.setDestLongitude(destLongitude);

        // call map function to get distance and time
        ResponseResult<DirectionResponse> directionResponse = client.direction(priceDTO);


        // load calculation rules

        // calculate the predicted price

        return directionResponse;
    }
}
