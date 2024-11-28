package com.george.servicedriveruser.service;

import com.george.internalCommon.dto.ResponseResult;
import com.george.servicedriveruser.mapper.DriverUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * This class is to deal with drivers in a specific area
 */
@Service
public class CityDriverUserService {

    @Autowired
    DriverUserMapper driverUserMapper;

    /**
     * This function is to determine whether there is available driver in a specific area
     * @param cityCode
     * @return
     */
    public ResponseResult<Boolean> isAvailableDriver(String cityCode){
        int i = driverUserMapper.selectDriverUserCountByCityCode(cityCode);
        if (i > 0){
            return ResponseResult.success(true);
        }else{
            return ResponseResult.success(false);
        }
    }
}
