package com.george.serviceprice.controller;

import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.PredictPriceDTO;
import com.george.serviceprice.service.PredictPriceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: George Sun
 * @Date: 2024-10-24-15:24
 * @Description: com.george.serviceprice.controller
 */
@RestController
public class PredictPriceController {
    @Autowired
    PredictPriceService service;

    @PostMapping("/predict-price")
    public ResponseResult predictPrice(@RequestBody PredictPriceDTO priceDTO) {

        return service.predictPrice(priceDTO.getDepLatitude(), priceDTO.getDepLongitude(),
                priceDTO.getDestLatitude(), priceDTO.getDestLongitude()) ;
    }
}
