package com.george.servicemap.remote;


import com.george.internalCommon.constant.AmapConfigConstant;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.TrackResponse;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * This class is to deal with all the traces
 */
@Service
@Slf4j
public class TrackClient {

    @Value("${amap.key}")
    private String amapKey;

    @Value("${amap.sid}")
    private String amapSid;

    //the one needed to connect with Amap
    @Autowired
    private RestTemplate restTemplate;


    /**
     * This function is used to add a new trace to the terminal
     * @param tid - the terminal id
     * @return
     */
    public ResponseResult<TrackResponse> add(String tid){
        // assemble url for request
        StringBuilder url = new StringBuilder();
        url.append(AmapConfigConstant.TRACK_ADD);
        url.append("?");
        url.append("key="+amapKey);
        url.append("&");
        url.append("sid="+amapSid);
        url.append("&");
        url.append("tid="+tid);
        ResponseEntity<String> stringResponseEntity = restTemplate.postForEntity(url.toString(), null, String.class);
        String body = stringResponseEntity.getBody();
        JSONObject result = new JSONObject(body);
        JSONObject data = result.getJSONObject("data");
        // trace id
        Integer trid = data.getInt("trid");
        // trace name
        String trname = "";
        if (data.has("trname")){
            trname = data.getString("trname");
        }

        TrackResponse trackResponse = new TrackResponse();
        trackResponse.setTrid(trid.toString());
        trackResponse.setTrname(trname);


        return ResponseResult.success(trackResponse);
    }
}
