package com.george.apidriver.remote;

import com.george.internalCommon.dto.Car;
import com.george.internalCommon.dto.DriverUser;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.DriverUserExistsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: George Sun
 * @Date: 2024-11-01-18:26
 * @Description: This class provides remote access to another server to deal with the driver user
 */

@FeignClient("service-driver-user")
public interface ServiceDriverUserClient {

    // The function used to update the driver info
    @RequestMapping(method = RequestMethod.PUT, value = "/user")
    ResponseResult updateUser(@RequestBody DriverUser user);

    // The function used to check the identity of the driver using phone number
    @RequestMapping(method = RequestMethod.GET, value = "/check-driver/{driverPhone}")
    ResponseResult<DriverUserExistsResponse> checkDriver(@PathVariable("driverPhone") String driverPhone);

    // The function used to obtain the vehicle info
    @RequestMapping(method = RequestMethod.GET, value = "/car")
    ResponseResult<Car> getCarById(@RequestParam Long carId);
}
