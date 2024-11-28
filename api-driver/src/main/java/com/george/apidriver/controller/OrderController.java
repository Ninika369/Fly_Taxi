package com.george.apidriver.controller;


import com.george.apidriver.service.ApiDriverOrderInfoService;
import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.constant.UserIdentity;
import com.george.internalCommon.dto.OrderInfo;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.dto.TokenResult;
import com.george.internalCommon.request.OrderRequest;
import com.george.internalCommon.util.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * This class is used to connect to the order system and change its status
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    ApiDriverOrderInfoService apiDriverOrderInfoService;

    /**
     * This function is to change the order status to "to-pick-up-passenger"
     * @param orderRequest
     * @return
     */
    @PostMapping("/to-pick-up-passenger")
    public ResponseResult changeStatus(@RequestBody OrderRequest orderRequest){

        return apiDriverOrderInfoService.toPickUpPassenger(orderRequest);
    }

    /**
     * This function is to change the order status to "arrived-departure"
     * @param orderRequest
     * @return
     */
    @PostMapping("/arrived-departure")
    public ResponseResult arrivedDeparture(@RequestBody OrderRequest orderRequest){
        return apiDriverOrderInfoService.arrivedDeparture(orderRequest);
    }

    /**
     * This function is to change the order status to "pick-up-passenger"
     * @param orderRequest
     * @return
     */
    @PostMapping("/pick-up-passenger")
    public ResponseResult pickUpPassenger(@RequestBody OrderRequest orderRequest){
        return apiDriverOrderInfoService.pickUpPassenger(orderRequest);
    }

    /**
     * This function is to change the order status to "passenger-getoff"
     * @param orderRequest
     * @return
     */
    @PostMapping("/passenger-getoff")
    public ResponseResult passengerGetoff(@RequestBody OrderRequest orderRequest){
        return apiDriverOrderInfoService.passengerGetoff(orderRequest);
    }

    /**
     * This function is to change the order status to "cancelled"
     * @param orderId
     * @return
     */
    @PostMapping("/cancel")
    public ResponseResult cancel(@RequestParam Long orderId){
        return apiDriverOrderInfoService.cancel(orderId);
    }


}
