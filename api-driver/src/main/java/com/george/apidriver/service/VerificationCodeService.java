package com.george.apidriver.service;

import com.george.apidriver.remote.ServiceVerificationcodeClient;

import com.george.apidriver.remote.ServiceDriverUserClient;
import com.george.internalCommon.constant.*;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.DataResponse;
import com.george.internalCommon.response.DriverUserExistsResponse;
import com.george.internalCommon.response.TokenResponse;
import com.george.internalCommon.util.JwtUtils;
import com.george.internalCommon.util.RedisPrefixUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * This class is used to deal with the verification code the driver sends
 */
@Service
@Slf4j
public class VerificationCodeService {

    @Autowired
    ServiceDriverUserClient serviceDriverUserClient;

    @Autowired
    ServiceVerificationcodeClient serviceVerificationcodeClient;

    // used to store the verification code in Redis
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    /**
     * This function is used to check the existence of a driver and if he exists, send back the verification code
     * @param driverPhone - the phone number according to which the identity of the driver would be assured
     * @return
     */
    public ResponseResult checkAndsendVerificationCode(String driverPhone){
        // connect with service-driver-user and find whether the driver exists
        ResponseResult<DriverUserExistsResponse> driverUserExistsResponseResponseResult = serviceDriverUserClient.checkDriver(driverPhone);
        DriverUserExistsResponse data = driverUserExistsResponseResponseResult.getData();
        int ifExists = data.getIfExists();
        if (ifExists == DriverCarConstant.DRIVER_NOT_EXISTS){
            return ResponseResult.fail(CommonStatus.DRIVER_NOT_EXISTS.getCode(),CommonStatus.DRIVER_NOT_EXISTS.getMessage());
        }
        log.info(driverPhone+" 的司机存在");
        // get VC
        ResponseResult<DataResponse> numberCodeResult = serviceVerificationcodeClient.getNumberCode(6);
        DataResponse numberCodeResponse = numberCodeResult.getData();
        int verificationCode = numberCodeResponse.getNumberCode();
        log.info("验证码: "+verificationCode);
        // Call third party to generate verification code, third party: Ali SMS service, Tencent, Huaxin, Ronglian

        // store in reids。1：key，2：存入value
        String key = RedisPrefixUtils.generateKey(driverPhone, UserIdentity.DRIVER.getIdentity());
        stringRedisTemplate.opsForValue().set(key, verificationCode+"", 2, TimeUnit.MINUTES);

        return ResponseResult.success("");
    }



    /**
     * This method is to check the correctness sent back by the driver
     * @param driverPhone the phone number of the driver
     * @param verificationCode the verification code
     * @return
     */
    public ResponseResult checkCode(String driverPhone , String verificationCode){
        // extract the verification code from Redis according to the phone number
        // generate key
        String key = RedisPrefixUtils.generateKey(driverPhone,UserIdentity.DRIVER.getIdentity());
        // get value
        String codeRedis = stringRedisTemplate.opsForValue().get(key);
        System.out.println("redis中的value："+codeRedis);

        // check verification code
        if (codeRedis.equals("") || codeRedis == null) {
            return ResponseResult.fail(CommonStatus.VERIFICATION_ERROR.getCode(),
                    CommonStatus.VERIFICATION_ERROR.getMessage());
        }

        if (!verificationCode.trim().equals(codeRedis.trim())){
            return ResponseResult.fail(CommonStatus.VERIFICATION_ERROR.getCode(),
                    CommonStatus.VERIFICATION_ERROR.getMessage());
        }

        // generate tokens if passed
        String accessToken = JwtUtils.generateToken(driverPhone, UserIdentity.DRIVER.getIdentity(), TokenConstant.ACCESS_TOKEN_TYPE);
        String refreshToken = JwtUtils.generateToken(driverPhone, UserIdentity.DRIVER.getIdentity(), TokenConstant.REFRESH_TOKEN_TYPE);

        // store token in redis
        String accessTokenKey = RedisPrefixUtils.generateTokenKey(driverPhone , UserIdentity.DRIVER.getIdentity() , TokenConstant.ACCESS_TOKEN_TYPE);
        stringRedisTemplate.opsForValue().set(accessTokenKey , accessToken , 30, TimeUnit.DAYS);

        String refreshTokenKey = RedisPrefixUtils.generateTokenKey(driverPhone , UserIdentity.DRIVER.getIdentity() , TokenConstant.REFRESH_TOKEN_TYPE);
        stringRedisTemplate.opsForValue().set(refreshTokenKey , refreshToken , 31, TimeUnit.DAYS);

        // return response
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken(accessToken);
        tokenResponse.setRefreshToken(refreshToken);
        return ResponseResult.success(tokenResponse);
    }
}
