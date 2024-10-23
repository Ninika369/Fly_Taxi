package com.geroge.apipassenger.remote;

import com.george.internalCommon.dto.PassengerUser;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.VerificationCodeDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * @Author: George Sun
 * @Date: 2024-10-18-14:34
 * @Description: This class is used to connect with the remote server
 */

@FeignClient("service-passenger-user")
public interface ServicePassengerUserClient {

    @RequestMapping(method = RequestMethod.POST, value = "/user")
    ResponseResult loginOrRegister(@RequestBody VerificationCodeDTO verificationCodeDTO);


    @RequestMapping(method = RequestMethod.GET, value = "/get-user/{phone}")
    ResponseResult getUser(@PathVariable("phone") String phoneNum);
}
