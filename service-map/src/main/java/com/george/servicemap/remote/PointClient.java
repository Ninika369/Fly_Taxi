package com.george.servicemap.remote;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.george.internalCommon.constant.AmapConfigConstant;
import com.george.internalCommon.dto.PointDTO;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.PointRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

/**
 * This class is to connect with Amap and deal with the points inside the trace
 */

@Service
public class PointClient {

    // the key to initiate the operation
    @Value("${amap.key}")
    private String amapKey;

    // the service id
    @Value("${amap.sid}")
    private String amapSid;

    // used to connect with Amap
    @Autowired
    private RestTemplate restTemplate;

    /**
     * This function is used to upload the points
     * @param pointRequest - the class that contains the points
     * @return
     */
    public ResponseResult upload (PointRequest pointRequest){
        // assemble the url for request
        StringBuilder url = new StringBuilder();
        url.append(AmapConfigConstant.POINT_UPLOAD);
        url.append("?");
        url.append("key="+amapKey);
        url.append("&");
        url.append("sid="+amapSid);
        url.append("&");
        url.append("tid="+pointRequest.getTid());
        url.append("&");
        url.append("trid="+pointRequest.getTrid());
        url.append("&");

        // Convert points to a JSON string using Jackson
        ObjectMapper objectMapper = new ObjectMapper();
        PointDTO[] points = pointRequest.getPoints();
        String pointsJson = null;
        try {
            pointsJson = objectMapper.writeValueAsString(points);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        // URL encoding of the points JSON string
        String encodedPoints = null;
        try {
            encodedPoints = URLEncoder.encode(pointsJson, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        // Add the encoded JSON to the URL
        url.append("points=").append(encodedPoints);

        ResponseEntity<String> stringResponseEntity = restTemplate.postForEntity(URI.create(url.toString()), null, String.class);

        return ResponseResult.success(stringResponseEntity.getBody());
    }
}
