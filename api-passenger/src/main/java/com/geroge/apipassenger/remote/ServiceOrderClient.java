package com.geroge.apipassenger.remote;


import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.OrderRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * This class is used to connect the order system directly using openfeign, so that it can be
 * successfully invoked by the service provider
 */
@FeignClient("service-order")
public interface ServiceOrderClient {

    @RequestMapping(method = RequestMethod.POST, value = "/order/add")
    ResponseResult add(@RequestBody OrderRequest orderRequest);

    @RequestMapping(method = RequestMethod.GET,value = "/test-real-time-order/{orderId}")
    String dispatchRealTimeOrder(@PathVariable("orderId") long orderId);

    @RequestMapping(method = RequestMethod.POST, value = "/order/cancel")
    ResponseResult cancel(@RequestParam Long orderId , @RequestParam String identity);

}
