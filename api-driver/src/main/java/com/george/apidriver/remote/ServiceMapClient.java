package com.george.apidriver.remote;


import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.PointRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * This class is ued to connect with the Amap service
 */
@FeignClient("service-map")
public interface ServiceMapClient {

    // This class is used to upload the tracks of a terminal
    @RequestMapping(method = RequestMethod.POST, value = "/point/upload")
    ResponseResult upload(@RequestBody PointRequest pointRequest);

}
