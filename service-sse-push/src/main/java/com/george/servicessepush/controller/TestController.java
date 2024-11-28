package com.george.servicessepush.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: George Sun
 * @Date: 2024-11-23-15:46
 * @Description: com.george.servicessepush.controller
 */
@RestController
public class TestController {
    @GetMapping("/test")
    public String test(){
        return "123";
    }
}
