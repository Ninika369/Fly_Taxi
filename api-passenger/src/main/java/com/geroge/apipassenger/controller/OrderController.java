package com.geroge.apipassenger.controller;


import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.constant.UserIdentity;
import com.george.internalCommon.dto.OrderInfo;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.dto.TokenResult;
import com.george.internalCommon.request.OrderRequest;
import com.george.internalCommon.util.JwtUtils;
import com.geroge.apipassenger.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * This class is used to manipulate orders from the passenger side
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    OrderService orderService;
    /**
     * This method is used to create order/place order from the passenger side
     * @return
     */
    @PostMapping("/add")
    public ResponseResult add(@RequestBody OrderRequest orderRequest){
        System.out.println(orderRequest);
        return orderService.add(orderRequest);
    }

    /**
     * This method is used to create cancle order from the passenger side
     * @param orderId
     * @return
     */
    @PostMapping("/cancel")
    public ResponseResult cancel(@RequestParam Long orderId){
        return orderService.cancel(orderId);
    }

}
