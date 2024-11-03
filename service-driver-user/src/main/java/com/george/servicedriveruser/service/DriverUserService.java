package com.george.servicedriveruser.service;

import com.george.internalCommon.dto.DriverUser;
import com.george.internalCommon.dto.ResponseResult;
import com.george.servicedriveruser.mapper.DriverUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @Author: George Sun
 * @Date: 2024-10-30-21:11
 * @Description: com.george.servicedriveruser.service
 */
@Service
public class DriverUserService {
    @Autowired
    private DriverUserMapper driverUserMapper;

    public ResponseResult testGetDriverUser() {
        DriverUser driverUser = driverUserMapper.selectById(1);
        return ResponseResult.success(driverUser);
    }

    public ResponseResult addDriverUser(DriverUser driverUser) {
        LocalDateTime now = LocalDateTime.now();
        driverUser.setGmtCreate(now);
        driverUser.setGmtModified(now);
        driverUserMapper.insert(driverUser);
        return ResponseResult.success("");
    }

    public ResponseResult updateDriverUser(DriverUser driverUser) {
        LocalDateTime now = LocalDateTime.now();
        driverUser.setGmtModified(now);
        driverUserMapper.updateById(driverUser);
        return ResponseResult.success("");
    }
}
