package com.george.apidriver.service;

import com.george.apidriver.remote.ServiceDriverUserClient;
import com.george.internalCommon.dto.DriverUser;
import com.george.internalCommon.dto.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Author: George Sun
 * @Date: 2024-11-01-18:24
 * @Description: This class provides a function to update the info of a driver
 */
@Service
public class UserService {

    @Autowired
    private ServiceDriverUserClient client;

    public ResponseResult updateUser(DriverUser driverUser) {
        return client.updateUser(driverUser);
    }
}
