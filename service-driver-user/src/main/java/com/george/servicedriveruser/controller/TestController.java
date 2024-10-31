package com.george.servicedriveruser.controller;

import com.george.internalCommon.dto.ResponseResult;
import com.george.servicedriveruser.service.DriverUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: George Sun
 * @Date: 2024-10-30-18:45
 * @Description: com.george.servicedriveruser.controller
 */
@RestController
public class TestController {

    @Autowired
    private DriverUserService service;

    @GetMapping("/test")
    public String test() {
        return "service-driver-user";
    }

    @GetMapping("/test-db")
    public ResponseResult test1() {
        return service.testGetDriverUser();
    }
}
