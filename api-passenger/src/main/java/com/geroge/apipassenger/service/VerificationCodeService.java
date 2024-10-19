package com.geroge.apipassenger.service;

import com.alibaba.nacos.common.utils.StringUtils;
import com.auth0.jwt.JWT;
import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.constant.UserIdentity;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.VerificationCodeDTO;
import com.george.internalCommon.response.DataResponse;
import com.george.internalCommon.response.TokenResponse;
import com.george.internalCommon.util.JwtUtils;
import com.george.internalCommon.util.RedisPrefixUtils;
import com.geroge.apipassenger.remote.ServicePassengerUserClient;
import com.geroge.apipassenger.remote.serviceVerificationCodeClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;


import java.util.concurrent.TimeUnit;

/**
 * @Author: George Sun
 * @Date: 2024-10-12-19:17
 * @Description: com.geroge.apipassenger.service
 */
@Service
public class VerificationCodeService {

    // This object is used to connect with the service part
    @Autowired
    private serviceVerificationCodeClient serviceClient;

    @Autowired
    private ServicePassengerUserClient userClient;


    // This variable is used to store String object in Redis
    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * This function is aimed to generate verification codes for passengers and
     * then store these codes in Redis
     * @param passengerPhone - The phone number of passengers
     * @return - a container of response messages
     */
    public ResponseResult generateCode (String passengerPhone) {
        // Obtain a verification code
        System.out.println("get a VC");

        ResponseResult<DataResponse> codeResponse = serviceClient.getResponse(6);
        int codeNumber = codeResponse.getData().getNumberCode();

        System.out.println("Received code: " + codeNumber);
        String key = RedisPrefixUtils.generateKey(passengerPhone);

        // Store verification code in Redis
        redisTemplate.opsForValue().set(key, String.valueOf(codeNumber), 2, TimeUnit.MINUTES);

        // Through the SMS service provider, send the verification code to the mobile phone.

        return ResponseResult.success("");
    }



    /**
     * This function is designed to check the correctness of verification codes sent from passenger's phone.
     * @param passengerPhone - The phone number of passengers
     * @param verificationCode - The verification codes sent by the passengers
     * @return - a container of response messages
     */
    public ResponseResult checkCode(String passengerPhone, String verificationCode) {
        // generate the key for checking
        String key = RedisPrefixUtils.generateKey(passengerPhone);

        // attempt to extract value from Redis
        String redisValue = redisTemplate.opsForValue().get(key);
        System.out.println("value in redis: " +  redisValue);

        // according to the phone number. check the correctness of vc
        if (StringUtils.isBlank(redisValue) || !redisValue.trim().equals(verificationCode.trim())) {
            return ResponseResult.fail(CommonStatus.VERIFICATION_ERROR.getCode(), CommonStatus.VERIFICATION_ERROR.getMessage());
        }

        // once the verification code matches, check the existence of users using service function
        VerificationCodeDTO verificationCodeDTO = new VerificationCodeDTO();
        verificationCodeDTO.setPassengerPhone(passengerPhone);
        userClient.loginOrRegister(verificationCodeDTO);

        // create a token for the user
        String token = JwtUtils.generateToken(passengerPhone, UserIdentity.PASSENGER.getIdentity());
        //store token in Redis
        String tokenKey = RedisPrefixUtils.generateTokenKey(passengerPhone, UserIdentity.PASSENGER.getIdentity());
        //store it for up to 30 days
        redisTemplate.opsForValue().set(tokenKey, token, 30, TimeUnit.DAYS);


        // return the token to passenger
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setToken(token);
        return ResponseResult.success(tokenResponse);
    }
}
