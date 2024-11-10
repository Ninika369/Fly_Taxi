package com.george.servicemap.remote;


import com.george.internalCommon.constant.AmapConfigConstant;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.TerminalResponse;
import com.george.internalCommon.response.TrsearchResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is to deal with the terminal in a service, a terminal meaning a vehicle
 */
@Service
public class TerminalClient {

    @Value("${amap.key}")
    private String amapKey;

    @Value("${amap.sid}")
    private String amapSid;

    // used to build connection with Amap server
    @Autowired
    private RestTemplate restTemplate;


    /**
     * This function is to add a terminal in a service according to its name and description(usually its carID)
     * @param name - the name of the terminal
     * @param desc - the id of the vehicle
     * @return
     */
    public ResponseResult<TerminalResponse> add(String name, String desc){
        // assemble the url for request
        StringBuilder url = new StringBuilder();
        url.append(AmapConfigConstant.TERMINAL_ADD);
        url.append("?");
        url.append("key="+amapKey);
        url.append("&");
        url.append("sid="+amapSid);
        url.append("&");
        url.append("name="+name);
        url.append("&");
        url.append("desc="+desc);
        ResponseEntity<String> stringResponseEntity = restTemplate.postForEntity(url.toString(), null, String.class);

        // extract the terminal id from response
        String body = stringResponseEntity.getBody();
        JSONObject result = new JSONObject(body);
        JSONObject data = result.getJSONObject("data");
        Integer tid = data.getInt("tid");

        TerminalResponse terminalResponse = new TerminalResponse();
        terminalResponse.setTid(tid.toString());

        return  ResponseResult.success(terminalResponse);
    }


    /**
     * This function is used to search the surrounding terminals about the center
     * @param center - the central terminal
     * @param radius - the description of the terminal (usually the range of the request)
     * @return
     */
    public ResponseResult<List<TerminalResponse>> aroundsearch(String center, Integer radius){
        //assemble the url for request
        StringBuilder url = new StringBuilder();
        url.append(AmapConfigConstant.TERMINAL_AROUNDSEARCH);
        url.append("?");
        url.append("key="+amapKey);
        url.append("&");
        url.append("sid="+amapSid);
        url.append("&");
        url.append("center="+center);
        url.append("&");
        url.append("radius="+radius);

        ResponseEntity<String> stringResponseEntity = restTemplate.postForEntity(url.toString(), null, String.class);

        // Parse terminal search results
        String body = stringResponseEntity.getBody();
        JSONObject result = new JSONObject(body);
        JSONObject data = result.getJSONObject("data");

        List<TerminalResponse> terminalResponseList = new ArrayList<>();

        JSONArray results = data.getJSONArray("results");

        // analyze all the surrounding terminals from the Amap server
        for (int i=0;i < results.length();i++){
            TerminalResponse terminalResponse = new TerminalResponse();

            JSONObject jsonObject = results.getJSONObject(i);
            String desc = jsonObject.getString("desc");
            Long carId = Long.parseLong(desc);
            String tid = String.valueOf(jsonObject.getInt("tid"));

            JSONObject location = jsonObject.getJSONObject("location");
            String longitude = String.valueOf(location.getBigDecimal("longitude"));
            String latitude = String.valueOf(location.getBigDecimal("latitude"));

            terminalResponse.setCarId(carId);
            terminalResponse.setTid(tid);
            terminalResponse.setLongitude(longitude);
            terminalResponse.setLatitude(latitude);

            terminalResponseList.add(terminalResponse);
        }



        return ResponseResult.success(terminalResponseList);
    }

    /**
     * This method is to find all the traces nearby and obtain how far and how long they need to get here
     * @param tid - the id of the terminal
     * @param starttime - the start time of the trace
     * @param endtime - the end time of the trace
     * @return
     */
    public ResponseResult<TrsearchResponse> trsearch(String tid, Long starttime , Long endtime){
        // assemble the requesting urls
        StringBuilder url = new StringBuilder();
        url.append(AmapConfigConstant.TERMINAL_TRSEARCH);
        url.append("?");
        url.append("key="+amapKey);
        url.append("&");
        url.append("sid="+amapSid);
        url.append("&");
        url.append("tid="+tid);
        url.append("&");
        url.append("starttime="+starttime);
        url.append("&");
        url.append("endtime="+endtime);

        System.out.println("高德地图查询轨迹结果请求："+url.toString());
        ResponseEntity<String> forEntity = restTemplate.getForEntity(url.toString(), String.class);
        System.out.println("高德地图查询轨迹结果响应："+forEntity.getBody());

        JSONObject result = new JSONObject(forEntity.getBody());
        JSONObject data = result.getJSONObject("data");
        int counts = data.getInt("counts");
        if (counts == 0){
            return null;
        }
        JSONArray tracks = data.getJSONArray("tracks");
        long driveMile = 0L;
        long driveTime = 0L;
        for (int i=0;i<tracks.length();i++){
            JSONObject jsonObject = tracks.getJSONObject(i);

            long distance = jsonObject.getLong("distance");
            driveMile = driveMile + distance;

            long time = jsonObject.getLong("time");
            time = time / (1000 * 60);
            driveTime = driveTime + time;
        }
        TrsearchResponse trsearchResponse = new TrsearchResponse();
        trsearchResponse.setDriveMile(driveMile);
        trsearchResponse.setDriveTime(driveTime);
        return ResponseResult.success(trsearchResponse);

    }
}
