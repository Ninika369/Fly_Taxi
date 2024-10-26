package com.george.servicemap.controller;

import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.PredictPriceDTO;
import com.george.servicemap.service.DirectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: George Sun
 * @Date: 2024-10-24-18:04
 * @Description: The controller as the interface to connect with Amap
 */
@RestController
@RequestMapping("/direction")
public class DirectionController {

    @Autowired
    DirectionService service;

    @GetMapping("/driving")
    public ResponseResult driving(@RequestBody PredictPriceDTO priceDTO) {
        String destLatitude = priceDTO.getDestLatitude();
        String destLongitude = priceDTO.getDestLongitude();
        String depLongitude = priceDTO.getDepLongitude();
        String depLatitude = priceDTO.getDepLatitude();
        return service.driving(depLatitude, depLongitude, destLatitude, destLongitude);
    }
}
