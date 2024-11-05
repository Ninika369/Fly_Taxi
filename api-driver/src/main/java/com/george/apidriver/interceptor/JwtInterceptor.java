package com.george.apidriver.interceptor;

import com.auth0.jwt.exceptions.AlgorithmMismatchException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;

import com.george.internalCommon.constant.TokenConstant;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.dto.TokenResult;
import com.george.internalCommon.util.JwtUtils;
import com.george.internalCommon.util.RedisPrefixUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * This class is used to define the interceptor
 */
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        boolean result = true;
        String resutltString = "";

        String token = request.getHeader("Authorization");
        // decode token
        TokenResult tokenResult = JwtUtils.checkToken(token);

        if (tokenResult == null){
            resutltString = "access token invalid";
            result = false;
        }else{
            // generate key for tokens
            String phone = tokenResult.getPhone();
            String identity = tokenResult.getIdentity();

            String tokenKey = RedisPrefixUtils.generateTokenKey(phone,identity, TokenConstant.ACCESS_TOKEN_TYPE);
            // get token from Redis
            String tokenRedis = stringRedisTemplate.opsForValue().get(tokenKey);
            if (tokenRedis == null || tokenRedis.length() == 0 || (!token.trim().equals(tokenRedis.trim()))){
                resutltString = "access token invalid";
                result = false;
            }
        }

        if (!result){
            PrintWriter out = response.getWriter();
            out.print(new JSONObject(ResponseResult.fail(resutltString)).toString());
        }

        return result;
    }
}