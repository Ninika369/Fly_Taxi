package com.geroge.apipassenger.service;


import com.george.internalCommon.constant.UserIdentity;
import com.george.internalCommon.dto.OrderInfo;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.OrderRequest;
import com.geroge.apipassenger.remote.ServiceOrderClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.geom.RectangularShape;

/**
 * This class is used to invoke the remote connection order system and
 * provides support for the controller layer
 */
@Service
public class OrderService {

    @Autowired
    ServiceOrderClient serviceOrderClient;

    public ResponseResult add(OrderRequest orderRequest){
        return serviceOrderClient.add(orderRequest);
    }

    public ResponseResult cancel(Long orderId){
        return serviceOrderClient.cancel(orderId, UserIdentity.PASSENGER.getIdentity());
    }


}
