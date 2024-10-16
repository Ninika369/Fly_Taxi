package com.george.serviceverificationcode.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: George Sun
 * @Date: 2024-10-13-13:06
 * @Description: com.george.servicevirificationcode.controller
 */
@RestController
public class TestController {

    @GetMapping("/get")
    public String get() {
        return "aaa";
    }

}
