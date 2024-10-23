package com.george.servicepassengeruser.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.george.internalCommon.dto.PassengerUser;
import org.springframework.stereotype.Repository;

/**
 * @Author: George Sun
 * @Date: 2024-10-17-19:32
 * @Description: This class is to create a mapper object to connect with database
 */

@Repository
public interface PassengerUserMapper extends BaseMapper<PassengerUser> {

}
