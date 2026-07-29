package com.george.serviceorder.remote;

import com.george.internalCommon.dto.ResponseResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "service-price",
        contextId = "finalizationPriceClient")
public interface FinalizationPriceClient {

    @RequestMapping(method = RequestMethod.POST, value = "/calculate-price")
    ResponseResult<Double> calculatePrice(@RequestParam Integer distance , @RequestParam Integer duration, @RequestParam String cityCode, @RequestParam String vehicleType);
}
