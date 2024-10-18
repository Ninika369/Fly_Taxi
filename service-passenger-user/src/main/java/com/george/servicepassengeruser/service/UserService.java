package com.george.servicepassengeruser.service;

import com.george.internalCommon.dto.ResponseResult;
import com.george.servicepassengeruser.dto.PassengerUser;
import com.george.servicepassengeruser.mapper.PassengerUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: George Sun
 * @Date: 2024-10-17-16:59
 * @Description: com.george.servicepassengeruser.service
 */
@Service
public class UserService {

    @Autowired
    private PassengerUserMapper passengerUserMapper;

    public ResponseResult checkOrRegister(String passengerPhone) {
        System.out.println("user service phone: " + passengerPhone);

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
}
