package com.george.servicepassengeruser.service;

import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.dto.PassengerUser;
import com.george.internalCommon.dto.ResponseResult;
import com.george.servicepassengeruser.mapper.PassengerUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: George Sun
 * @Date: 2024-10-17-16:59
 * @Description: This class is to check or get the user
 */
@Service
public class UserService {

    // mapper to connect with MySQL Database
    @Autowired
    private PassengerUserMapper passengerUserMapper;

    /**
     * This function is used to check the existence of user after assuring the correctness of verification code.
     * If the user does not exist in database, then create a new account for that user.
     * @param passengerPhone - the phone number of user
     * @return
     */
    public ResponseResult checkOrRegister(String passengerPhone) {
        // use MybatisPlus to connect with MySQL
        Map<String, Object> map = new HashMap<>();
        map.put("passenger_phone", passengerPhone);
        List<PassengerUser> passengerUsers = passengerUserMapper.selectByMap(map);

        // check whether current user exists in database
        if (passengerUsers.isEmpty()) {
            PassengerUser user = new PassengerUser();
            user.setPassengerName("张悦");
            // 0 means female; 1 means male
            user.setPassengerGender((byte) 0);
            user.setPassengerPhone(passengerPhone);
            // 0 means valid user; 1 for invalid
            user.setState((byte) 0);
            LocalDateTime now = LocalDateTime.now();
            user.setGmtCreate(now);
            user.setGmtModified(now);
            passengerUserMapper.insert(user);
        }

        return ResponseResult.success();
    }

    /**
     * This method is used to return the passenger user by phone-number check
     * @param phoneNum - the phone number of specific user
     * @return
     */
    public ResponseResult getUserByPhone(String phoneNum) {
        // use MybatisPlus to connect with MySQL
        Map<String, Object> map = new HashMap<>();
        map.put("passenger_phone", phoneNum);
        List<PassengerUser> passengerUsers = passengerUserMapper.selectByMap(map);

        if (passengerUsers.isEmpty()) {
            return ResponseResult.fail(CommonStatus.USER_NOT_EXISTS.getCode(), CommonStatus.USER_NOT_EXISTS.getMessage());
        }
        PassengerUser user = passengerUsers.get(0);
        return ResponseResult.success(user);
    }
}
