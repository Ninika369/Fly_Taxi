package com.george.apidriver.remote;

import com.george.internalCommon.dto.DriverUser;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.DriverUserExistsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * @Author: George Sun
 * @Date: 2024-11-01-18:26
 * @Description: This class provides remote access to another server to deal with the driver user
 */

@FeignClient("service-driver-user")
public interface ServiceDriverUserClient {

    @RequestMapping(method = RequestMethod.PUT, value = "/user")
    ResponseResult updateUser(@RequestBody DriverUser user);

    @RequestMapping(method = RequestMethod.GET, value = "/check-driver/{driverPhone}")
    ResponseResult<DriverUserExistsResponse> checkDriver(@PathVariable("driverPhone") String driverPhone);
}
