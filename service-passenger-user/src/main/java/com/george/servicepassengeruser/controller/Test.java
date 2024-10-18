package com.george.servicepassengeruser.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: George Sun
 * @Date: 2024-10-17-15:08
 * @Description: com.george.servicepassengeruser.controller
 */
@RestController
public class Test {

    @GetMapping("/Hello")
    public String test(){
        return "Hello";
    }
}
