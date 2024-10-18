package com.geroge.apipassenger.service;

import com.alibaba.nacos.common.utils.StringUtils;
import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.VerificationCodeDTO;
import com.george.internalCommon.response.DataResponse;
import com.george.internalCommon.response.TokenResponse;
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

    // This variable represents the prefix of verification code stored in Redis
    private String verificationCodePrefix = "passenger-verification-code-";

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
        String key = generateKey(passengerPhone);

        // Store verification code in Redis
        redisTemplate.opsForValue().set(key, String.valueOf(codeNumber), 2, TimeUnit.MINUTES);

        // Through the SMS service provider, send the verification code to the mobile phone.

        return ResponseResult.success("");
    }


    /**
     * Generate a key using an independent function to facilitate the process
     * @param passengerPhone
     * @return
     */
    private String generateKey(String passengerPhone) {
        return verificationCodePrefix + passengerPhone;
    }


    /**
     * This function is designed to check the correctness of verification codes sent from passenger's phone.
     * @param passengerPhone - The phone number of passengers
     * @param verificationCode - The verification codes sent by the passengers
     * @return - a container of response messages
     */
    public ResponseResult checkCode(String passengerPhone, String verificationCode) {
        // generate the key for checking
        String key = generateKey(passengerPhone);

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


        // return the token to passenger
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setToken("token value");
        return ResponseResult.success(tokenResponse);
    }
}
