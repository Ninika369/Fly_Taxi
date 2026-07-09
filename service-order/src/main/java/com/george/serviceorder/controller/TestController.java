package com.george.serviceorder.controller;

import com.george.internalCommon.dto.OrderInfo;
import com.george.internalCommon.dto.ResponseResult;
import com.george.serviceorder.mapper.OrderInfoMapper;
import com.george.serviceorder.service.OrderInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: George Sun
 * @Date: 2024-11-11-16:33
 * @Description: com.example.serviceorder.controller
 */
@RestController
@Slf4j
public class TestController {
    @Autowired
    OrderInfoService service;

    @Autowired
    OrderInfoMapper orderInfoMapper;

    @GetMapping("/test")
    public String test() {
        return "SV!";
    }

    @Value("${server.port}")
    String port;

//    @GetMapping("/testmapper")
//    public String testMapper() {
//        return service.testMapper();
//    }

    /**
     * test sending orders
     * @param orderId
     * @return
     */
    @GetMapping("/test-real-time-order/{orderId}")
    public String dispatchRealTimeOrder(@PathVariable("orderId") long orderId){
        log.info("Dispatch test request received in service-order on port {}, orderId={}", port, orderId);
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        service.dispatchRealTimeOrder(orderInfo);
        return "test-real-time-order   success";
    }
}
