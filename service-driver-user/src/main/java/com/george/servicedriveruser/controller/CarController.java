package com.george.servicedriveruser.controller;


import com.george.internalCommon.dto.Car;
import com.george.internalCommon.dto.ResponseResult;
import com.george.servicedriveruser.service.CarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author george
 * @since 2024-11-01
 */
@RestController
public class CarController {
    @Autowired
    private CarService service;

    @PostMapping("/car")
    public ResponseResult addCar(@RequestBody Car car) {

        return service.addCar(car);
    }
}
