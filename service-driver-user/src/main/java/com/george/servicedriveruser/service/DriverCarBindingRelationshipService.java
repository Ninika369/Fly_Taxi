package com.george.servicedriveruser.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.constant.DriverCarConstant;
import com.george.internalCommon.dto.DriverCarBindingRelationship;
import com.george.internalCommon.dto.DriverUser;
import com.george.internalCommon.dto.ResponseResult;
import com.george.servicedriveruser.mapper.DriverCarBindingRelationshipMapper;
import com.george.servicedriveruser.mapper.DriverUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: George Sun
 * @Date: 2024-11-03-13:03
 * @Description: This class is to provide services for the driver and the vehicle to bind a relationship
 */
@Service
public class DriverCarBindingRelationshipService {
    @Autowired
    private DriverCarBindingRelationshipMapper mapper;

    @Autowired
    DriverUserMapper driverUserMapper;

    /**
     * This function is to bind a relationship between driver and vehicle
     * @param bindingRelationship - the given relationship
     * @return
     */
    public ResponseResult bind(DriverCarBindingRelationship bindingRelationship) {
        // if the vehicle and the driver have already been bound, then no need to rebind them
        QueryWrapper<DriverCarBindingRelationship> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("driver_id", bindingRelationship.getDriverId());
        queryWrapper.eq("car_id", bindingRelationship.getCarId());
        queryWrapper.eq("bind_state", DriverCarConstant.DRIVER_CAR_BIND);
        Integer value = mapper.selectCount(queryWrapper);
        if (value.intValue() > 0) {
            return ResponseResult.fail(CommonStatus.DRIVER_CAR_BIND_EXISTS.getCode(),
                    CommonStatus.DRIVER_CAR_BIND_EXISTS.getMessage());
        }

        // if the driver has already been bound, then no need to rebind
        queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("driver_id", bindingRelationship.getDriverId());
        queryWrapper.eq("bind_state", DriverCarConstant.DRIVER_CAR_BIND);
        value = mapper.selectCount(queryWrapper);
        if (value.intValue() > 0) {
            return ResponseResult.fail(CommonStatus.DRIVER_BIND_EXISTS.getCode(),
                    CommonStatus.DRIVER_BIND_EXISTS.getMessage());
        }

        // if the car has already been bound, then no need to rebind
        queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("car_id", bindingRelationship.getCarId());
        queryWrapper.eq("bind_state", DriverCarConstant.DRIVER_CAR_BIND);
        value = mapper.selectCount(queryWrapper);
        if (value.intValue() > 0) {
            return ResponseResult.fail(CommonStatus.CAR_BIND_EXISTS.getCode(),
                    CommonStatus.CAR_BIND_EXISTS.getMessage());
        }

        // set the binding parameters
        LocalDateTime now = LocalDateTime.now();
        bindingRelationship.setBindingTime(now);

        bindingRelationship.setBindState(DriverCarConstant.DRIVER_CAR_BIND);

        mapper.insert(bindingRelationship);
        return ResponseResult.success("");
    }

    /**
     * This function is to unbind a relationship between driver and vehicle
     * @param bindingRelationship - the given relationship
     * @return
     */
    public ResponseResult unbind(DriverCarBindingRelationship bindingRelationship) {
        // get the data from database
        Map<String, Object> map = new HashMap<>();
        map.put("driver_id", bindingRelationship.getDriverId());
        map.put("car_id", bindingRelationship.getCarId());
        map.put("bind_state", DriverCarConstant.DRIVER_CAR_BIND);
        List<DriverCarBindingRelationship> driverCarBindingRelationships = mapper.selectByMap(map);

        // if the relationship does not exist
        if (driverCarBindingRelationships.isEmpty()) {
            return ResponseResult.fail(CommonStatus.DRIVER_CAR_BIND_NOT_EXISTS.getCode(),
                    CommonStatus.DRIVER_CAR_BIND_NOT_EXISTS.getMessage());
        }

        // set the binding parameters
        DriverCarBindingRelationship relationship = driverCarBindingRelationships.get(0);
        relationship.setBindState(DriverCarConstant.DRIVER_CAR_UNBIND);
        relationship.setUnBindingTime(LocalDateTime.now());
        mapper.updateById(relationship);
        return ResponseResult.success("");
    }


    /**
     * This method is to get the relationship between a driver and a vehicle,
     * accroding to the phone number of that driver
     * @param driverPhone
     * @return
     */
    public ResponseResult<DriverCarBindingRelationship> getDriverCarRelationShipByDriverPhone(@RequestParam String driverPhone){
        // First get the driver information
        QueryWrapper<DriverUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("driver_phone",driverPhone);

        DriverUser driverUser = driverUserMapper.selectOne(queryWrapper);
        Long driverId = driverUser.getId();

        // Then get the relationship according to the driver
        QueryWrapper<DriverCarBindingRelationship> driverCarBindingRelationshipQueryWrapper = new QueryWrapper<>();
        driverCarBindingRelationshipQueryWrapper.eq("driver_id",driverId);
        driverCarBindingRelationshipQueryWrapper.eq("bind_state",DriverCarConstant.DRIVER_CAR_BIND);

        DriverCarBindingRelationship driverCarBindingRelationship = mapper.selectOne(driverCarBindingRelationshipQueryWrapper);
        return ResponseResult.success(driverCarBindingRelationship);

    }
}