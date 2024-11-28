package com.george.apidriver.controller;


import com.george.apidriver.service.PayService;
import com.george.internalCommon.dto.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * This class is used to connect the driver side to the payment system
 */
@RestController
@RequestMapping("/pay")
public class PayController {

    @Autowired
    PayService payService;

    /**
     * This method is used to allow the driver to initiate payment
     * @param orderId - the order ID code
     * @param price - the price of the order
     * @return
     */
    @PostMapping("/push-pay-info")
    public ResponseResult pushPayInfo(@RequestParam Long orderId , @RequestParam String price, @RequestParam Long passengerId){

        return payService.pushPayInfo(orderId,price,passengerId);
    }
}
