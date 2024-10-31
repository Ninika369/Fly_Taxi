package com.george.internalCommon.dto;

import lombok.Data;

/**
 * @Author: George Sun
 * @Date: 2024-10-27-18:01
 * @Description: com.george.internalCommon.dto
 */
@Data
public class DicDistrict {
    private String addressCode;
    private String addressName;
    private String parentAddressCode;
    private Integer level;
}
