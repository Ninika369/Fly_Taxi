package com.george.serviceorder.remote;

import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.TrsearchResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "service-map",
        contextId = "finalizationMapClient")
public interface FinalizationMapClient {

    @RequestMapping(method = RequestMethod.POST, value = "/terminal/trsearch")
    ResponseResult<TrsearchResponse> trsearch(@RequestParam String tid, @RequestParam Long starttime, @RequestParam Long endtime);
}
