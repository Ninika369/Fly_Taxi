package com.george.apidriver.service;


import com.george.apidriver.remote.ServiceOrderClient;
import com.george.apidriver.remote.ServiceSsePushClient;
import com.george.internalCommon.constant.UserIdentity;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.OrderRequest;
import com.george.internalCommon.request.PushRequest;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * This method is used to provide service support to the payment system
 */
@Service
public class PayService {

    @Autowired
    ServiceSsePushClient serviceSsePushClient;

    @Autowired
    ServiceOrderClient serviceOrderClient;

    /**
     * This method is used to push an order message to the passenger to complete the payment operation
     * @param orderId - the id number of the order
     * @param price - the price of the order
     * @param passengerId - the id number of the passenger
     * @return
     */
    public ResponseResult pushPayInfo(Long orderId, String price, Long passengerId){
        // Encapsulate message
        JSONObject message = new JSONObject();
        message.put("price",price);
        message.put("orderId",orderId);

        // Modify order status
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setOrderId(orderId);
        serviceOrderClient.pushPayInfo(orderRequest);

        PushRequest pushRequest = new PushRequest();
        pushRequest.setContent(message.toString());
        pushRequest.setUserId(passengerId);
        pushRequest.setIdentity(UserIdentity.PASSENGER.getIdentity());

        // push order information
        serviceSsePushClient.push(pushRequest);

        return ResponseResult.success();
    }
}
