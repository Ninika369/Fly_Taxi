package com.george.internalCommon.response;

import lombok.Data;

/**
 * @Author: George Sun
 * @Date: 2024-10-24-18:31
 * @Description: This class stores the distance and duration from place A to B
 */
@Data
public class DirectionResponse {

    private Integer distance;
    private Integer duration;

}
