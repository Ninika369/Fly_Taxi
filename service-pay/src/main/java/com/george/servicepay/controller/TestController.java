package com.george.servicepay.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: George Sun
 * @Date: 2024-11-25-15:59
 * @Description: Test whether Alipay has an effective response
 */
@RestController
public class TestController {

    @PostMapping("/test")
    public String test() {
        System.out.println("Alipay Back");
        return "Succeed!";
    }
}
