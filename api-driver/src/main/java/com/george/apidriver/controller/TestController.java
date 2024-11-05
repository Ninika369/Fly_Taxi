package com.george.apidriver.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    /**
     * The interface that needs authorization with tokens
     * @return
     */
    @GetMapping("/auth")
    public String testAuth(){
        return  "auth";
    }

    /**
     * The interface that needs no authorization with tokens
     * @return
     */
    @GetMapping("/noauth")
    public String testNoAuth(){
        return "no auth";
    }
}
