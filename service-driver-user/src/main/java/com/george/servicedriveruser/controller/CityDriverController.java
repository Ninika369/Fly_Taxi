package com.george.servicedriveruser.controller;

import com.george.internalCommon.dto.ResponseResult;
import com.george.servicedriveruser.service.CityDriverUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * This class is to deal with situations in current city
 */
@RestController
@RequestMapping("/city-driver")
public class CityDriverController {

    @Autowired
    CityDriverUserService cityDriverUserService;

    /**
     * Check whether there are available drivers in the current city according to the city code
     * @param cityCode
     * @return
     */
    @GetMapping("/is-alailable-driver")
    public ResponseResult isAvailableDriver(String cityCode){
        return cityDriverUserService.isAvailableDriver(cityCode);
    }
}
