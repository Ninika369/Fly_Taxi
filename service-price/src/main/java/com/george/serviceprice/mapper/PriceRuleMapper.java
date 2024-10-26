package com.george.serviceprice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.george.internalCommon.dto.PriceRule;
import org.springframework.stereotype.Repository;

/**
 * @Author: George Sun
 * @Date: 2024-10-25-19:54
 * @Description: remotely connect with map service using mybatis-plus
 */
@Repository
public interface PriceRuleMapper extends BaseMapper<PriceRule> {

}
