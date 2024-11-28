package com.george.servicepay.service;


import com.george.internalCommon.request.OrderRequest;
import com.george.servicepay.remote.ServiceOrderClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * This class is used to handle requests sent from the controller layer
 */
@Service
public class AlipayService {

    @Autowired
    ServiceOrderClient serviceOrderClient;

    public void pay(Long orderId){
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setOrderId(orderId);
        serviceOrderClient.pay(orderRequest);

    }
}
