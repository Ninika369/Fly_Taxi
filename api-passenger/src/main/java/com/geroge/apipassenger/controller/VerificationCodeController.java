package com.geroge.apipassenger.controller;

import com.geroge.apipassenger.request.VerificationCodeDTO;
import com.geroge.apipassenger.service.VerificationCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: George Sun
 * @Date: 2024-10-12-19:08
 * @Description: com.geroge.apipassenger.controller
 */
@RestController
public class VerificationCodeController {

    @Autowired
    private VerificationCodeService service;



    @GetMapping("/verification-code")
    public String verificationCode(@RequestBody VerificationCodeDTO codeDTO) {
        System.out.println("-----");
        String phone = codeDTO.getPassengerPhone();
        System.out.println("phone number is: " + phone);
        String res = service.generateCode(phone);

        return res;
    }
}
