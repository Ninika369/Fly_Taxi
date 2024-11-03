package com.george.servicedriveruser.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.constant.DriverCarConstant;
import com.george.internalCommon.dto.DriverCarBindingRelationship;
import com.george.internalCommon.dto.ResponseResult;
import com.george.servicedriveruser.mapper.DriverCarBindingRelationshipMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: George Sun
 * @Date: 2024-11-03-13:03
 * @Description: com.george.servicedriveruser.service
 */
@Service
public class DriverCarBindingRelationshipService {
    @Autowired
    private DriverCarBindingRelationshipMapper mapper;

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


        LocalDateTime now = LocalDateTime.now();
        bindingRelationship.setBindingTime(now);

        bindingRelationship.setBindState(DriverCarConstant.DRIVER_CAR_BIND);

        mapper.insert(bindingRelationship);
        return ResponseResult.success("");
    }

    public ResponseResult unbind(DriverCarBindingRelationship bindingRelationship) {
        Map<String, Object> map = new HashMap<>();
        map.put("driver_id", bindingRelationship.getDriverId());
        map.put("car_id", bindingRelationship.getCarId());
        map.put("bind_state", DriverCarConstant.DRIVER_CAR_BIND);

        List<DriverCarBindingRelationship> driverCarBindingRelationships = mapper.selectByMap(map);
        if (driverCarBindingRelationships.isEmpty()) {
            return ResponseResult.fail(CommonStatus.DRIVER_CAR_BIND_NOT_EXISTS.getCode(),
                    CommonStatus.DRIVER_CAR_BIND_NOT_EXISTS.getMessage());
        }
        DriverCarBindingRelationship relationship = driverCarBindingRelationships.get(0);
        relationship.setBindState(DriverCarConstant.DRIVER_CAR_UNBIND);
        relationship.setUnBindingTime(LocalDateTime.now());

        mapper.updateById(relationship);
        return ResponseResult.success("");
    }
}