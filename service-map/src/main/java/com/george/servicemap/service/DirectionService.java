package com.george.servicemap.service;

import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.DirectionResponse;
import com.george.servicemap.remote.MapDirectionException;
import com.george.servicemap.remote.MapServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Author: George Sun
 * @Date: 2024-10-24-18:13
 * @Description: com.george.servicemap.service
 */
@Service
@Slf4j
public class DirectionService {

    @Autowired
    MapServiceClient mapServiceClient;

    /**
     * This function is used to obtain the distance and journey duration,
     * according to the longitude and latitude of destination and origin.
     * @param depLatitude
     * @param depLongitude
     * @param destLatitude
     * @param destLongitude
     * @return
     */
    public ResponseResult driving(String depLatitude, String depLongitude,
                                  String destLatitude, String destLongitude) {
        // connect with map interface (lbs.amap.com)

        DirectionResponse directionResponse;
        try {
            directionResponse = mapServiceClient.direction(depLatitude, depLongitude,
                                                                            destLatitude, destLongitude);
        } catch (MapDirectionException e) {
            log.warn("Map direction request failed", e);
            return ResponseResult.fail(CommonStatus.MAP_DIRECTION_ERROR.getCode(),
                    CommonStatus.MAP_DIRECTION_ERROR.getMessage());
        }
        if (directionResponse == null) {
            return ResponseResult.fail(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(),
                    CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage());
        }

        return ResponseResult.success(directionResponse);
    }
}
