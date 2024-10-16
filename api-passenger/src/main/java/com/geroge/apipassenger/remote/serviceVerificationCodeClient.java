package com.geroge.apipassenger.remote;

import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.DataResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * @Author: George Sun
 * @Date: 2024-10-16-12:48
 * @Description: com.geroge.apipassenger.remote
 */

/**
 * Class use to build connection with service function using openFeign
 */
@FeignClient("service-verificationCode")
public interface serviceVerificationCodeClient {

    @RequestMapping(method = RequestMethod.GET, value = "/numberCode/{size}")
    ResponseResult<DataResponse> getResponse(@PathVariable int size);
}
