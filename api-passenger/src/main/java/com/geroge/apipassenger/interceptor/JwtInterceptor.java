package com.geroge.apipassenger.interceptor;

import com.auth0.jwt.exceptions.AlgorithmMismatchException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.dto.TokenResult;
import com.george.internalCommon.util.JwtUtils;
import com.george.internalCommon.util.RedisPrefixUtils;
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
        boolean noError = true;
        String resultMessage = "";

        String token = request.getHeader("Authorization");
        TokenResult res = null;

        try {
            res = JwtUtils.parseToken(token);
        }
        catch (SignatureVerificationException e) {
            resultMessage = "token sign error";
            noError = false;
        }
        catch (TokenExpiredException e) {
            resultMessage = "token expired error";
            noError = false;
        }
        catch (AlgorithmMismatchException e) {
            resultMessage = "token algorithm error";
            noError = false;
        }
        catch (Exception e) {
            resultMessage = "token invalid";
            noError = false;
        }

        if (res == null) {
            resultMessage = "token invalid";
            noError = false;
        }
        else {
            // generate the token prefix for further check in Redis
            String phone = res.getPhone();
            String identity = res.getIdentity();
            String tokenKey = RedisPrefixUtils.generateTokenKey(phone, identity);

            // check the token using key
            String tokenValue = redisTemplate.opsForValue().get(tokenKey);
            if (tokenValue.trim().equals("") || tokenValue == null || !tokenValue.trim().equals(token.trim())) {
                resultMessage = "token invalid";
                noError = false;
            }
        }

        if (!noError) {
            PrintWriter writer = response.getWriter();
            writer.print(new JSONObject(ResponseResult.fail(resultMessage)));
        }


        return noError;
    }
}
