package com.geroge.apipassenger.controller;

import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.TokenResponse;
import com.geroge.apipassenger.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: George Sun
 * @Date: 2024-10-21-15:54
 * @Description: com.geroge.apipassenger.controller
 */
@RestController
public class TokenController {

    @Autowired
    private TokenService service;

    @PostMapping("/token-refresh")
    public ResponseResult refreshToken (@RequestBody TokenResponse tokenResponse) {

        String inputToken = tokenResponse.getRefreshToken();


        return service.refreshToken(inputToken);
    }
}
