package com.geroge.apipassenger.controller;

import com.george.internalCommon.dto.ResponseResult;
import com.geroge.apipassenger.remote.ServiceOrderClient;
import lombok.Getter;
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
     * 测试派单逻辑
     * @param orderId
     * @return
     */
    @GetMapping("/test-real-time-order/{orderId}")
    public String dispatchRealTimeOrder(@PathVariable("orderId") long orderId){
        System.out.println("并发测试：api-passenger："+orderId);
        serviceOrderClient.dispatchRealTimeOrder(orderId);
        return "test-real-time-order   success";
    }
}
