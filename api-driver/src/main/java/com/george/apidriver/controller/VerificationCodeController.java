package com.george.apidriver.controller;

import com.george.apidriver.service.VerificationCodeService;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.VerificationCodeDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * This class is to verify the identiry of a driver
 */
@RestController
@Slf4j
public class VerificationCodeController {

    @Autowired
    VerificationCodeService verificationCodeService;

    /**
     * This function is to check the identity of a driver and send a verification code back
     * @param verificationCodeDTO
     * @return
     */
    @PostMapping("/verification-code")
    public ResponseResult verificationCode(@RequestBody VerificationCodeDTO verificationCodeDTO){
        String driverPhone = verificationCodeDTO.getDriverPhone();
        log.info("Driver Phone: "+driverPhone);
        return verificationCodeService.checkAndsendVerificationCode(driverPhone);
    }


    /**
     * This function is to check the correctness of a verification code
     * @param verificationCodeDTO
     * @return
     */
    @PostMapping("/verification-code-check")
    public ResponseResult checkVerificationCode(@RequestBody VerificationCodeDTO verificationCodeDTO){

        String driverPhone = verificationCodeDTO.getDriverPhone();
        String verificationCode = verificationCodeDTO.getVerificationCode();

        System.out.println("Number: "+driverPhone+", VC："+verificationCode);

        return verificationCodeService.checkCode(driverPhone,verificationCode);
    }
}
