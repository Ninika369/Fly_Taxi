package com.geroge.apipassenger.interceptor;

import com.auth0.jwt.exceptions.AlgorithmMismatchException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.george.internalCommon.constant.TokenConstant;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.dto.TokenResult;
import com.george.internalCommon.util.JwtUtils;
import com.george.internalCommon.util.RedisPrefixUtils;
import jdk.nashorn.internal.parser.Token;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * @Author: George Sun
 * @Date: 2024-10-19-13:06
 * @Description: This class is aimed to set a interceptor that can check the existence and correctness
 *              of token sent by users.
 */
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        boolean hasNoError = true;
        String resultMessage = "";

        String token = request.getHeader("Authorization");
        TokenResult res = JwtUtils.checkToken(token);



        if (res == null) {
            resultMessage = "token invalid";
            hasNoError = false;
        }
        else {
            // generate the token prefix for further check in Redis
            String phone = res.getPhone();
            String identity = res.getIdentity();
            String tokenKey = RedisPrefixUtils.generateTokenKey(phone, identity, TokenConstant.ACCESS_TOKEN_TYPE);

            // check the token using key
            String tokenValue = redisTemplate.opsForValue().get(tokenKey);
            if (tokenValue.trim().equals("") || tokenValue == null || !tokenValue.trim().equals(token.trim())) {
                resultMessage = "token invalid";
                hasNoError = false;
            }
        }

        if (!hasNoError) {
            PrintWriter writer = response.getWriter();
            writer.print(new JSONObject(ResponseResult.fail(resultMessage)));
        }


        return hasNoError;
    }
}
