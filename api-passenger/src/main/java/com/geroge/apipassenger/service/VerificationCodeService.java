package com.geroge.apipassenger.service;

import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.DataResponse;
import com.geroge.apipassenger.remote.serviceVerificationCodeClient;
import com.geroge.apipassenger.request.VerificationCodeDTO;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Author: George Sun
 * @Date: 2024-10-12-19:17
 * @Description: com.geroge.apipassenger.service
 */
@Service
public class VerificationCodeService {

    @Autowired
    private serviceVerificationCodeClient serviceClient;

    // Receive client's phone number and return a verification code
    public String generateCode (String passengerPhone) {
        // Obtain a verification code
        System.out.println("get a VC");

        ResponseResult<DataResponse> codeResponse = serviceClient.getResponse(6);
        int codeNumber = codeResponse.getData().getNumberCode();

        System.out.println("Received code: " + codeNumber);

        // Store verification code in Redis
        System.out.println("store in Redis");

        JSONObject result = new JSONObject();
        result.put("code", 1);
        result.put("message", "success");
        return result.toString();
    }
}
