package com.geroge.apipassenger.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: George Sun
 * @Date: 2024-10-12-18:33
 * @Description: com.geroge.apipassenger.controller
 */
@RestController
public class TestController {
    @GetMapping("/get")
    public String test() {
        return "test api succeeds";
    }
}
