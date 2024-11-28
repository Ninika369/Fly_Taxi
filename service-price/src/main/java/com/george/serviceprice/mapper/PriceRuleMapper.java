package com.george.serviceprice.mapper;

import com.george.internalCommon.dto.PriceRule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.stereotype.Repository;

/**
 * This class is used to join the table of pricing rules
 * @author george
 * @since 2024-11-12
 */
@Repository
public interface PriceRuleMapper extends BaseMapper<PriceRule> {

}
