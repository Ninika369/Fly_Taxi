package com.george.apiboss.controller;

import com.george.apiboss.service.CarService;
import com.george.apiboss.service.DriverUserService;
import com.george.internalCommon.dto.Car;
import com.george.internalCommon.dto.DriverUser;
import com.george.internalCommon.dto.ResponseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: George Sun
 * @Date: 2024-11-01-10:51
 * @Description: This class provides methods to deal with deriver information
 */
@RestController
@Slf4j
public class DriverUserController {

    @Autowired
    private DriverUserService driverUserService;

    @Autowired
    private CarService carService;

    /**
     * This method is to add a driver in dataset
     * @param driverUser
     * @return
     */
    @PostMapping("/driver-user")
    public ResponseResult addDriverUser(@RequestBody DriverUser driverUser) {
        return driverUserService.addDriverUser(driverUser);
    }

    /**
     * This method is to update the driver info in dataset
     * @param driverUser
     * @return
     */
    @PutMapping("/driver-user")
    public ResponseResult updateDriverUser(@RequestBody DriverUser driverUser) {
        return driverUserService.updateDriverUser(driverUser);
    }

    /**
     * This method is to add a car in dataset
     * @param car
     * @return
     */
    @PostMapping("/car")
    public ResponseResult addCar(@RequestBody Car car) {
        return carService.addCar(car);
    }
}
