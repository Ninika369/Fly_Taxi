package com.george.servicedriveruser.service;

import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.dto.DriverUserWorkStatus;
import com.george.internalCommon.dto.ResponseResult;
import com.george.servicedriveruser.mapper.DriverUserWorkStatusMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is to change the status of the driver
 * @author george
 * @since 2024-11-04
 */
@Service
public class DriverUserWorkStatusService{

    @Autowired
    DriverUserWorkStatusMapper driverUserWorkStatusMapper;

    /**
     * This function is to change the working status of the driver
     * @param driverId - the id of driver
     * @param workStatus - the working status of the driver
     * @return
     */
    public ResponseResult changeWorkStatus(Long driverId, Integer workStatus){
        // search for the driver using id in dataset
        Map<String,Object> queryMap = new HashMap<>();
        queryMap.put("driver_id",driverId);
        List<DriverUserWorkStatus> driverUserWorkStatuses = driverUserWorkStatusMapper.selectByMap(queryMap);

        if (driverUserWorkStatuses == null || driverUserWorkStatuses.isEmpty())
            return ResponseResult.fail(CommonStatus.DRIVER_NOT_EXISTS.getCode(), CommonStatus.DRIVER_NOT_EXISTS.getMessage());

        DriverUserWorkStatus driverUserWorkStatus = driverUserWorkStatuses.get(0);

        driverUserWorkStatus.setWorkStatus(workStatus);

        driverUserWorkStatus.setGmtModified(LocalDateTime.now());

        driverUserWorkStatusMapper.updateById(driverUserWorkStatus);

        return ResponseResult.success("");

    }

    /**
     * This function is responsible for obtaining the working status of a driver
     * @param driverId - the id of the driver
     * @return
     */
    public ResponseResult<DriverUserWorkStatus> getWorkStatus(Long driverId) {
        Map<String,Object> queryMap = new HashMap<>();
        queryMap.put("driver_id",driverId);
        List<DriverUserWorkStatus> driverUserWorkStatuses = driverUserWorkStatusMapper.selectByMap(queryMap);
        DriverUserWorkStatus driverUserWorkStatus = driverUserWorkStatuses.get(0);

        return ResponseResult.success(driverUserWorkStatus);

    }
}
