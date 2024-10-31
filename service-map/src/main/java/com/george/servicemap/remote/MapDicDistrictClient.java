package com.george.servicemap.remote;

import com.george.internalCommon.constant.AmapConfigConstant;
import com.george.internalCommon.dto.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * @Author: George Sun
 * @Date: 2024-10-28-15:57
 * @Description: com.george.servicemap.remote
 */
@Service
public class MapDicDistrictClient {

    @Value("${amap.key}")
    private String mapKey;

    @Autowired
    private RestTemplate restTemplate;

    public String dicDistrict(String keywords) {

        // key=<用户的key>
        // create a url for visiting
        StringBuilder url = new StringBuilder();
        url.append(AmapConfigConstant.DISTRICT_URL);
        url.append("keywords=" + keywords + "&");
        url.append("subdistrict=3" + "&");
        url.append("key=" + mapKey);

        // access Amap service
        ResponseEntity<String> entity = restTemplate.getForEntity(url.toString(), String.class);

        return entity.getBody();
    }
}
