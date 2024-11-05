package com.george.apiboss.service;

import com.george.apiboss.remote.ServiceDriverUserClient;
import com.george.internalCommon.dto.Car;
import com.george.internalCommon.dto.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Author: George Sun
 * @Date: 2024-11-03-16:35
 * @Description: This class is aimed to add a car using remote connection
 */
@Service
public class CarService {

    @Autowired
    ServiceDriverUserClient client;

    public ResponseResult addCar(Car car) {
        return client.addCar(car);
    }
}
