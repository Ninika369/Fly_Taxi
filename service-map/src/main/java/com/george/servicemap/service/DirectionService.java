package com.george.servicemap.service;

import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.DirectionResponse;
import com.george.servicemap.remote.MapServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Author: George Sun
 * @Date: 2024-10-24-18:13
 * @Description: com.george.servicemap.service
 */
@Service
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

        DirectionResponse directionResponse = mapServiceClient.direction(depLatitude, depLongitude,
                                                                        destLatitude, destLongitude);

        return ResponseResult.success(directionResponse);
    }
}
