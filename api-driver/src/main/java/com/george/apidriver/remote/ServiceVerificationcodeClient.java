package com.george.apidriver.remote;

import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.DataResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * This class provides remote access to another server to generate verification code
 */
@FeignClient("service-verificationCode")
public interface ServiceVerificationcodeClient {

    @RequestMapping(method = RequestMethod.GET,value = "/numberCode/{size}")
    ResponseResult<DataResponse> getNumberCode(@PathVariable("size") int size);
}
