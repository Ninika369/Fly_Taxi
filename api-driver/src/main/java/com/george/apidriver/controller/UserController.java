package com.george.apidriver.controller;

import com.george.apidriver.service.UserService;
import com.george.internalCommon.dto.DriverUser;
import com.george.internalCommon.dto.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: George Sun
 * @Date: 2024-11-01-18:24
 * @Description: com.george.apidriver.controller
 */
@RestController
public class UserController {

    @Autowired
    private UserService service;

    @PutMapping("/user")
    public ResponseResult updateUser(@RequestBody DriverUser driverUser) {
        return service.updateUser(driverUser);
    }
}
