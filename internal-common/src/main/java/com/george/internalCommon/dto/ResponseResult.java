package com.george.internalCommon.dto;

import com.george.internalCommon.constant.CommonStatus;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: George Sun
 * @Date: 2024-10-13-15:33
 * @Description: com.george.internalCommon.dto
 */

// This class is aimed to create a class which contains different responses
@Data
@Accessors(chain = true)
public class ResponseResult<T> {

    private int code;
    private String message;
    private T data;

    // This function is used to return a successful response
    public static <T> ResponseResult success(T data) {
        return new ResponseResult().setCode(CommonStatus.SUCCESS.getCode()).
                setMessage(CommonStatus.SUCCESS.getMessage()).
                setData(data);
    }




    // This function is used to return a successful response with no params
    public static <T> ResponseResult success() {
        return new ResponseResult().setCode(CommonStatus.SUCCESS.getCode()).
                setMessage(CommonStatus.SUCCESS.getMessage());
    }


    // This function is used to return a default failure response
    public static <T> ResponseResult fail(T data) {
        return new ResponseResult().setCode(CommonStatus.FAIL.getCode()).
                setMessage(CommonStatus.FAIL.getMessage()).
                setData(data);
    }

    /**
     * This function is used to return a specific failure response.
     * For instance, 1001 represents the failure of generating verification code.
     */
    public static ResponseResult fail(int code, String message) {
        return new ResponseResult().setCode(code).setMessage(message);
    }

    /**
     * This function is used to return a specific failure response,
     * which contains the error's type, description and additional info.
     */
    public static ResponseResult fail(int code, String message, String data) {
        return new ResponseResult().setCode(code).setMessage(message).setData(data);
    }

}
