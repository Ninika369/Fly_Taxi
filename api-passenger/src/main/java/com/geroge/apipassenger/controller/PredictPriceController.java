package com.geroge.apipassenger.controller;

import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.PredictPriceDTO;
import com.geroge.apipassenger.service.PredictPriceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: George Sun
 * @Date: 2024-10-23-20:02
 * @Description: com.geroge.apipassenger.controller
 */
@RestController
@Slf4j
public class PredictPriceController {

    @Autowired
    PredictPriceService priceService;

    @PostMapping("/predict-price")
    public ResponseResult predictPrice(@RequestBody PredictPriceDTO priceDTO) {

        log.info("origin longitude: " + priceDTO.getDepLongitude());
        String depLatitude = priceDTO.getDepLatitude();
        String depLongitude = priceDTO.getDepLongitude();
        String destLatitude = priceDTO.getDestLatitude();
        String destLongitude = priceDTO.getDestLongitude();

        return priceService.predictPrice(depLatitude, depLongitude, destLatitude, destLongitude);
    }
}
