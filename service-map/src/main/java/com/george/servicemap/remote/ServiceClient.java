package com.george.servicemap.remote;


import com.george.internalCommon.constant.AmapConfigConstant;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.ServiceResponse;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * This class is used to deal with services in Amap
 */
@Service
public class ServiceClient {

    // the key of a valid account
    @Value("${amap.key}")
    private String amapKey;

    // used to connect with Amap
    @Autowired
    private RestTemplate restTemplate;


    /**
     * This function is used to add a service to the Amap service according to the input name
     * @param name
     * @return
     */
    public ResponseResult add(String name){
        //assemble url for request
        StringBuilder url = new StringBuilder();
        url.append(AmapConfigConstant.SERVICE_ADD_URL);
        url.append("?");
        url.append("key="+amapKey);
        url.append("&");
        url.append("name="+name);

        ResponseEntity<String> forEntity = restTemplate.postForEntity(url.toString(), null,String.class);
        String body = forEntity.getBody();
        JSONObject result = new JSONObject(body);
        JSONObject data = result.getJSONObject("data");
        Integer sid = data.getInt("sid");
        ServiceResponse serviceResponse = new ServiceResponse();
        serviceResponse.setSid(sid.toString());

        return ResponseResult.success(serviceResponse);
    }
}
