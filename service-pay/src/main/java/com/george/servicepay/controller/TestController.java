package com.george.servicepay.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: George Sun
 * @Date: 2024-11-25-15:59
 * @Description: Test whether Alipay has an effective response
 */
@RestController
@Slf4j
public class TestController {

    @PostMapping("/test")
    public String test() {
        log.info("Alipay test callback received");
        return "Succeed!";
    }
}
