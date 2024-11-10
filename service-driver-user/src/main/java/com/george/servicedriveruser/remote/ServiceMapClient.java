package com.george.servicedriveruser.remote;


import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.TerminalResponse;
import com.george.internalCommon.response.TrackResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * This class is used to connect with the map service to add terminal or track in Amap
 */
@FeignClient("service-map")
public interface ServiceMapClient {

    @RequestMapping(method = RequestMethod.POST, value = "/terminal/add")
    ResponseResult<TerminalResponse> addTerminal(@RequestParam String name , @RequestParam String desc);

    @RequestMapping(method = RequestMethod.POST, value = "/track/add")
    ResponseResult<TrackResponse> addTrack(@RequestParam String tid);
}
