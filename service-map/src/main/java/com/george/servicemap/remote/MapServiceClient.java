package com.george.servicemap.remote;

import com.george.internalCommon.constant.AmapConfigConstant;
import com.george.internalCommon.response.DirectionResponse;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * @Author: George Sun
 * @Date: 2024-10-24-20:03
 * @Description: This class is used to call Amap service
 */
@Service
@Slf4j
public class MapServiceClient {

    // user key to access info
    @Value("${amap.key}")
    private String mapKey;

    // send HTTP requests and receiving responses
    @Autowired
    private RestTemplate restTemplate;

    /**
     * This function is used to combine a list of input parameters into an important url to
     * connect with Amap and to return the necessary info
     * @param depLatitude - the latitude of the origin
     * @param depLongitude - the longitude of the origin
     * @param destLatitude - the latitude of the destination
     * @param destLongitude - the longitude of the destination
     * @return - the object containing distance and duration of a ride
     */
    public DirectionResponse direction(String depLatitude, String depLongitude,
                                       String destLatitude, String destLongitude) {
        // combination request to call url
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(AmapConfigConstant.DIRECTION_URL);
        urlBuilder.append("origin=" + depLongitude + "," + depLatitude + "&");
        urlBuilder.append("destination=" + destLongitude + "," + destLatitude + "&");
        urlBuilder.append("extensions=base" + "&");
        urlBuilder.append("output=json" + "&");
        urlBuilder.append("key=" + mapKey);

        // connect with the map interface
        ResponseEntity<String> directionEntity = restTemplate.getForEntity(urlBuilder.toString(), String.class);
        String directionString = directionEntity.getBody();

        // parse the response from map to get distance and duration
        DirectionResponse response = parseDirectionEntity(directionString);

        return response;
    }


    /**
     * This function is to extract necessary info, distance and duration, from input string
     * @param directionString - a long string from which essential data is needed to be extracted
     * @return - an object containing essential info
     */
    public DirectionResponse parseDirectionEntity(String directionString) {
        DirectionResponse result = null;
        try {
            JSONObject jsonObject  = new JSONObject(directionString);
            if(jsonObject.has(AmapConfigConstant.STATUS)) {
                int status = jsonObject.getInt(AmapConfigConstant.STATUS);
                if (status == 1) {
                    if (jsonObject.has(AmapConfigConstant.ROUTE)) {
                        JSONObject routeObject = jsonObject.getJSONObject(AmapConfigConstant.ROUTE);
                        JSONArray pathsArray = routeObject.getJSONArray(AmapConfigConstant.PATHS);
                        JSONObject pathObject = pathsArray.getJSONObject(0);
                        result = new DirectionResponse();

                        if (pathObject.has(AmapConfigConstant.DISTANCE)) {
                            int distance = pathObject.getInt(AmapConfigConstant.DISTANCE);
                            result.setDistance(distance);
                        }
                        if (pathObject.has(AmapConfigConstant.DURATION)) {
                            int duration = pathObject.getInt(AmapConfigConstant.DURATION);
                            result.setDuration(duration);
                        }
                    }
                }
            }

        }
        catch (Exception e) {

        }

        return result;
    }

}
