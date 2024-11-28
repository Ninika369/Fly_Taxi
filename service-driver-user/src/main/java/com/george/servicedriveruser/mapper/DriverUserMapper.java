package com.george.servicedriveruser.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.george.internalCommon.dto.DriverUser;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

/**
 * @Author: George Sun
 * @Date: 2024-10-30-21:10
 * @Description: mapper interface for deal with driver user
 */
@Repository
public interface DriverUserMapper extends BaseMapper<DriverUser> {
    /**
     * This method is to get the drivers' count in an area
     * @param cityCode
     * @return
     */
    int selectDriverUserCountByCityCode(@Param("cityCode") String cityCode);
}
