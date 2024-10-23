package com.geroge.apipassenger.service;

import com.george.internalCommon.dto.PassengerUser;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.dto.TokenResult;
import com.george.internalCommon.request.VerificationCodeDTO;
import com.george.internalCommon.util.JwtUtils;
import com.geroge.apipassenger.remote.ServicePassengerUserClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Author: George Sun
 * @Date: 2024-10-22-17:06
 * @Description: This class is aimed to achieve the goal to find the users' info
 */

@Service
@Slf4j
public class UserService {

    @Autowired
    ServicePassengerUserClient userClient;

    /**
     * This function is used to connect with the remote server to extract users' info
     * @param accessToken - the token needed to examine the identity of user
     * @return
     */
    public ResponseResult getUserInfo (String accessToken) {
        // decode accessToken to get phone number
        TokenResult tokenResult = JwtUtils.checkToken(accessToken);
        String phone = tokenResult.getPhone();
        log.info("Phone number: " + phone);


        // find user info according to phone number
        ResponseResult<PassengerUser> responseResult = userClient.getUser(phone);


        return responseResult;
    }
}
