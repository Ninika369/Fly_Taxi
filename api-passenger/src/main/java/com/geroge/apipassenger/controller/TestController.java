package com.geroge.apipassenger.controller;

import com.george.internalCommon.dto.ResponseResult;
import com.geroge.apipassenger.remote.ServiceOrderClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: George Sun
 * @Date: 2024-10-12-18:33
 * @Description: com.geroge.apipassenger.controller
 */
@RestController
@Slf4j
public class TestController {
    @GetMapping("/get")
    public String test() {
        return "test api succeeds";
    }

    @GetMapping("/authTest")
    public ResponseResult authTest() {
        return ResponseResult.success("auth test");
    }

    @GetMapping("/noAuthTest")
    public ResponseResult noauthTest() {
        return ResponseResult.success("no auth test");
    }

    @Autowired
    ServiceOrderClient serviceOrderClient;

    /**
     * Test dispatch logic.
     * @param orderId
     * @return
     */
    @GetMapping("/test-real-time-order/{orderId}")
    public String dispatchRealTimeOrder(@PathVariable("orderId") long orderId){
        log.info("Dispatch test request received in api-passenger, orderId={}", orderId);
        serviceOrderClient.dispatchRealTimeOrder(orderId);
        return "test-real-time-order   success";
    }
}
