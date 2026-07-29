package com.george.serviceorder.remote;

import com.george.internalCommon.dto.Car;
import com.george.internalCommon.dto.ResponseResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "service-driver-user",
        contextId = "finalizationDriverUserClient")
public interface FinalizationDriverUserClient {

    @GetMapping("/car")
    ResponseResult<Car> getCarById(@RequestParam  Long carId);
}
