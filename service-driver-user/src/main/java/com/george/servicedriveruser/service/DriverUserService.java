package com.george.servicedriveruser.service;

import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.constant.DriverCarConstant;
import com.george.internalCommon.dto.DriverUser;
import com.george.internalCommon.dto.DriverUserWorkStatus;
import com.george.internalCommon.dto.ResponseResult;
import com.george.servicedriveruser.mapper.DriverUserMapper;
import com.george.servicedriveruser.mapper.DriverUserWorkStatusMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: George Sun
 * @Date: 2024-10-30-21:11
 * @Description: This class provides service to deal with the driver users
 */
@Service
public class DriverUserService {
    @Autowired
    private DriverUserMapper driverUserMapper;

    @Autowired
    private DriverUserWorkStatusMapper driverUserWorkStatusMapper;

    /**
     * This method is used to add a new driver to database
     * @param driverUser - the driver to be added
     * @return
     */
    public ResponseResult addDriverUser(DriverUser driverUser) {
        // add a new driver
        LocalDateTime now = LocalDateTime.now();
        driverUser.setGmtCreate(now);
        driverUser.setGmtModified(now);
        driverUserMapper.insert(driverUser);

        // initialize driver status table
        DriverUserWorkStatus driverUserWorkStatus = new DriverUserWorkStatus();
        driverUserWorkStatus.setDriverId(driverUser.getId());

        // at the beginning, after the driver just login
        driverUserWorkStatus.setWorkStatus(DriverCarConstant.DRIVER_WORK_STATUS_STOP);
        driverUserWorkStatus.setGmtModified(now);
        driverUserWorkStatus.setGmtCreate(now);
        driverUserWorkStatusMapper.insert(driverUserWorkStatus);

        return ResponseResult.success("");
    }

    /**
     * This method is used to add a new driver to database
     * @param driverUser - the driver to be added
     * @return
     */
    public ResponseResult updateDriverUser(DriverUser driverUser) {
        LocalDateTime now = LocalDateTime.now();
        driverUser.setGmtModified(now);
        driverUserMapper.updateById(driverUser);
        return ResponseResult.success("");
    }

    /**
     * This method is used to get the driver info by the given phone number
     * @param driverPhone - the given phone number
     * @return
     */
    public ResponseResult<DriverUser> getDriverUserByPhone(String driverPhone){
        Map<String,Object> map = new HashMap<>();
        map.put("driver_phone", driverPhone);

        // the target driver must be a valid one
        map.put("state", DriverCarConstant.DRIVER_STATE_VALID);
        List<DriverUser> driverUsers = driverUserMapper.selectByMap(map);

        // if the driver does not exist
        if (driverUsers.isEmpty()){
            return ResponseResult.fail(CommonStatus.DRIVER_NOT_EXISTS.getCode(),CommonStatus.DRIVER_NOT_EXISTS.getMessage());
        }
        DriverUser driverUser = driverUsers.get(0);
        return ResponseResult.success(driverUser);
    }
}
