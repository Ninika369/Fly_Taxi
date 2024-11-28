package com.george.servicepay.remote;


import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.OrderRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * This class is used to establish a valid connection to the order service
 */
@FeignClient("service-order")
public interface ServiceOrderClient {

    @RequestMapping(method = RequestMethod.POST, value = "/order/pay")
    ResponseResult pay(@RequestBody OrderRequest orderRequest);
}
