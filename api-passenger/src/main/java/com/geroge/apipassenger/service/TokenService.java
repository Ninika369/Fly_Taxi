package com.geroge.apipassenger.service;

import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.constant.TokenConstant;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.dto.TokenResult;
import com.george.internalCommon.response.TokenResponse;
import com.george.internalCommon.util.JwtUtils;
import com.george.internalCommon.util.RedisPrefixUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * @Author: George Sun
 * @Date: 2024-10-21-16:01
 * @Description: com.geroge.apipassenger.service
 */
@Service
public class TokenService {
    @Autowired
    StringRedisTemplate redisTemplate;

    public ResponseResult refreshToken(String inputRefreshToken) {
        // decode input refresh token
        TokenResult result = JwtUtils.checkToken(inputRefreshToken);
        if (result == null) {
            return ResponseResult.fail(CommonStatus.TOKEN_ERROR.getCode(), CommonStatus.TOKEN_ERROR.getMessage());
        }
        String phone = result.getPhone();
        String identity = result.getIdentity();


        // extract stored refresh token from Redis
        String refreshTokenKey = RedisPrefixUtils.generateTokenKey(phone, identity, TokenConstant.REFRESH_TOKEN_TYPE);
        String refreshInRedis = redisTemplate.opsForValue().get(refreshTokenKey);

        // check whether they match
        if (refreshInRedis.trim().equals("") || refreshInRedis == null || !refreshInRedis.trim().equals(inputRefreshToken.trim())) {
            return ResponseResult.fail(CommonStatus.TOKEN_ERROR.getCode(), CommonStatus.TOKEN_ERROR.getMessage());
        }

        // generate new tokens
        String refreshNewToken = JwtUtils.generateToken(phone, identity, TokenConstant.REFRESH_TOKEN_TYPE);
        String accessNewToken = JwtUtils.generateToken(phone, identity, TokenConstant.ACCESS_TOKEN_TYPE);

        // generate new keys for new tokens to store in Redis
        String accessNewTokenKey = RedisPrefixUtils.generateTokenKey(phone, identity, TokenConstant.ACCESS_TOKEN_TYPE);
        redisTemplate.opsForValue().set(accessNewTokenKey, accessNewToken, 30, TimeUnit.DAYS);
        redisTemplate.opsForValue().set(refreshTokenKey, refreshNewToken, 32, TimeUnit.DAYS);



        // return the token to passenger
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken(accessNewToken);
        tokenResponse.setRefreshToken(refreshNewToken);

        return ResponseResult.success(tokenResponse);
    }
}
