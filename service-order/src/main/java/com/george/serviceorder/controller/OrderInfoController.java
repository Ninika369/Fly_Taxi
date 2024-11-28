package com.george.serviceorder.controller;


import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.OrderRequest;
import com.george.serviceorder.service.OrderInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * This class is used to provide an external interface and to handle any order-related operations
 * @author George Sun
 */
@RestController
@RequestMapping("/order")
@Slf4j
public class OrderInfoController {

    @Autowired
    OrderInfoService orderInfoService;

    /**
     * This function is to create a new order
     * @param orderRequest
     * @return
     */
    @PostMapping("/add")
    public ResponseResult add(@RequestBody OrderRequest orderRequest , HttpServletRequest httpServletRequest){
        return orderInfoService.add(orderRequest);
    }



    /**
     * This function is to deal with the situation where the driver initiates to pick up the passenger
     * @param orderRequest
     * @return
     */
    @PostMapping("/to-pick-up-passenger")
    public ResponseResult changeStatus(@RequestBody OrderRequest orderRequest){

        return orderInfoService.toPickUpPassenger(orderRequest);
    }

    /**
     * This function is to deal with the situation where the driver arrives the departure
     * @param orderRequest
     * @return
     */
    @PostMapping("/arrived-departure")
    public ResponseResult arrivedDeparture(@RequestBody OrderRequest orderRequest){
        return orderInfoService.arrivedDeparture(orderRequest);
    }

    /**
     * This function is to deal with the situation where the driver picks up the passenger
     * @param orderRequest
     * @return
     */
    @PostMapping("/pick-up-passenger")
    public ResponseResult pickUpPassenger(@RequestBody OrderRequest orderRequest){
        return orderInfoService.pickUpPassenger(orderRequest);
    }

    /**
     * This function is to deal with the situation where the passenger gets off the vehicle
     * @param orderRequest
     * @return
     */
    @PostMapping("/passenger-getoff")
    public ResponseResult passengerGetoff(@RequestBody OrderRequest orderRequest){
        return orderInfoService.passengerGetoff(orderRequest);
    }

    /**
     * This function is to push the payment when the order ends
     * @param orderRequest
     * @return
     */
    @PostMapping("/push-pay-info")
    public ResponseResult pushPayInfo(@RequestBody OrderRequest orderRequest){
        return orderInfoService.pushPayInfo(orderRequest);
    }

    /**
     * This function is to deal with the payment
     * @param orderRequest
     * @return
     */
    @PostMapping("/pay")
    public ResponseResult pay(@RequestBody OrderRequest orderRequest){

        return orderInfoService.pay(orderRequest);
    }

    /**
     * This function is to cancel the order
     * @param orderId
     * @param identity
     * @return
     */
    @PostMapping("/cancel")
    public ResponseResult cancel(Long orderId, String identity){

        return orderInfoService.cancel(orderId,identity);
    }

    /**
     * This function is to query the current order information,
     * according to the user identity and mobile phone number
     * @param phone
     * @param identity
     * @return
     */
    @GetMapping("/current")
    public ResponseResult current(String phone , String identity){
        return orderInfoService.current(phone , identity);
    }
}
