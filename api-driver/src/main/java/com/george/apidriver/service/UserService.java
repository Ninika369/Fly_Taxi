package com.george.apidriver.service;

import com.george.apidriver.remote.ServiceDriverUserClient;
import com.george.internalCommon.dto.DriverCarBindingRelationship;
import com.george.internalCommon.dto.DriverUser;
import com.george.internalCommon.dto.DriverUserWorkStatus;
import com.george.internalCommon.dto.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Author: George Sun
 * @Date: 2024-11-01-18:24
 * @Description: This class provides specific services to the controller layer to process driver user information
 */
@Service
public class UserService {

    @Autowired
    private ServiceDriverUserClient client;

    public ResponseResult updateUser(DriverUser driverUser) {
        return client.updateUser(driverUser);
    }

    public ResponseResult changeWorkStatus(DriverUserWorkStatus driverUserWorkStatus){
        return client.changeWorkStatus(driverUserWorkStatus);
    }

    public ResponseResult<DriverCarBindingRelationship> getDriverCarBindingRelationship(String driverPhone){
        return client.getDriverCarRelationShip(driverPhone);

    }

    public ResponseResult<DriverUserWorkStatus> getWorkStatus(Long driverId){
        return client.getWorkStatus(driverId);
    }
}
