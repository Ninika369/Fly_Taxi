package com.george.servicepassengeruser.controller;

import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.VerificationCodeDTO;
import com.george.servicepassengeruser.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: George Sun
 * @Date: 2024-10-17-16:47
 * @Description: This class is used to check the existences of user or
 * promote them to register for new accounts.
 */
@RestController
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/user")
    public ResponseResult loginOrRegister(@RequestBody VerificationCodeDTO codeDTO) {
        String phoneNum = codeDTO.getPassengerPhone();
        System.out.println("Cellphone: " + phoneNum);
        return userService.checkOrRegister(phoneNum);
    }
}

