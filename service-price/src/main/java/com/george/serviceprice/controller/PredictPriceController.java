package com.george.serviceprice.controller;

import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.PredictPriceDTO;
import com.george.serviceprice.service.PredictPriceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: George Sun
 * @Date: 2024-10-24-15:24
 * @Description: This controller is used to provide calculations for the price of orders
 */
@RestController
public class PredictPriceController {
    @Autowired
    PredictPriceService service;


    /**
     * This method is used to calculate an estimated price for the passenger before the order begins
     * @param priceDTO
     * @return
     */
    @PostMapping("/predict-price")
    public ResponseResult predictPrice(@RequestBody PredictPriceDTO priceDTO) {
        String depLatitude = priceDTO.getDepLatitude();
        String depLongitude = priceDTO.getDepLongitude();
        String destLatitude = priceDTO.getDestLatitude();
        String destLongitude = priceDTO.getDestLongitude();
        String cityCode = priceDTO.getCityCode();
        String vehicleType = priceDTO.getVehicleType();


        return service.predictPrice(depLatitude, depLongitude, destLatitude, destLongitude, cityCode, vehicleType) ;
    }


    /**
     * This method is used to calculate an actual price for the passenger after the order ends
     * @param distance - the distance from the departure to the destination
     * @param duration - the time spent in this ride
     * @param cityCode - the area of this ride
     * @param vehicleType - the vehicle type
     * @return
     */
    @PostMapping("/calculate-price")
    public ResponseResult<Double> calculatePrice(@RequestParam Integer distance , @RequestParam Integer duration, @RequestParam String cityCode, @RequestParam String vehicleType){
        return service.getPrice(distance,duration,cityCode,vehicleType);
    }
}
