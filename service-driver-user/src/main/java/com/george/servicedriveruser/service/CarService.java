package com.george.servicedriveruser.service;

import com.george.internalCommon.dto.Car;
import com.george.internalCommon.dto.ResponseResult;
import com.george.servicedriveruser.mapper.CarMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * @Author: George Sun
 * @Date: 2024-11-01-22:34
 * @Description: This class provides service to add a car in database
 */
@Service
public class CarService {

    @Autowired
    private CarMapper mapper;

    public ResponseResult addCar(Car car) {
        car.setGmtCreate(LocalDateTime.now());
        car.setGmtModified(LocalDateTime.now());
        mapper.insert(car);
        return ResponseResult.success("");
    }

}
