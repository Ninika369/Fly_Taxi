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
 * This class is to control the client side
 */
@RestController
public class CarController {
    @Autowired
    private CarService service;

    /**
     * The functino to add a mew car in dataset
     * @param car - the car to be added
     * @return
     */
    @PostMapping("/car")
    public ResponseResult addCar(@RequestBody Car car) {

        return service.addCar(car);
    }
}
