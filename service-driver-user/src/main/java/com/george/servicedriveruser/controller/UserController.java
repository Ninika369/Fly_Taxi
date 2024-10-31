package com.george.servicedriveruser.controller;

import com.george.internalCommon.dto.DriverUser;
import com.george.internalCommon.dto.ResponseResult;
import com.george.servicedriveruser.service.DriverUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: George Sun
 * @Date: 2024-10-31-11:06
 * @Description: com.george.servicedriveruser.controller
 */
@RestController
public class UserController {

    @Autowired
    private DriverUserService service;

    @PostMapping("/user")
    public ResponseResult addUser(@RequestBody DriverUser user) {
        return service.addDriverUser(user);
    }
}
