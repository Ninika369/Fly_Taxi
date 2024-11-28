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
 * @Description: This class is used to obtain a predicted price of a ride
 */
@RestController
@Slf4j
public class PredictPriceController {

    @Autowired
    PredictPriceService priceService;

    /**
     * This function is aimed to return a predicted price of a single ride to the user,
     * by giving the longitude and latitude of the origin and destination.
     * @param priceDTO - the input parameter including all the longitudes and latitudes
     * @return - an estimated price
     */
    @PostMapping("/predict-price")
    public ResponseResult predictPrice(@RequestBody PredictPriceDTO priceDTO) {

        String depLatitude = priceDTO.getDepLatitude();
        String depLongitude = priceDTO.getDepLongitude();
        String destLatitude = priceDTO.getDestLatitude();
        String destLongitude = priceDTO.getDestLongitude();
        String cityCode = priceDTO.getCityCode();
        String vehicleType = priceDTO.getVehicleType();

        return priceService.predictPrice(depLatitude, depLongitude, destLatitude, destLongitude, cityCode, vehicleType);
    }
}
