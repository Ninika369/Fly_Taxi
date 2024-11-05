package com.george.apiboss.service;

import com.george.apiboss.remote.ServiceDriverUserClient;
import com.george.internalCommon.dto.Car;
import com.george.internalCommon.dto.DriverUser;
import com.george.internalCommon.dto.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Author: George Sun
 * @Date: 2024-11-01-10:53
 * @Description: This class provides functionality for adding or updating a driver user
 */
@Service
public class DriverUserService {

    @Autowired
    private ServiceDriverUserClient client;

    public ResponseResult addDriverUser(DriverUser driverUser) {
        return client.addDriverUser(driverUser);
    }

    public ResponseResult updateDriverUser(DriverUser driverUser) {
        return client.updateDriverUser(driverUser);
    }


}
