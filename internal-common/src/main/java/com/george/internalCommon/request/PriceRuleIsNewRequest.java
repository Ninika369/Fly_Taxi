package com.george.internalCommon.request;

import lombok.Data;

/**
 * This class contains variables that are used to determine whether a pricing rule is latest
 */
@Data
public class PriceRuleIsNewRequest {

    private String fareType;

    private Integer fareVersion;
}
