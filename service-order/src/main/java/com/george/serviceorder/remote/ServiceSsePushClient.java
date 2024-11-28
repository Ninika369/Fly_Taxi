package com.george.serviceorder.remote;

import com.george.internalCommon.request.PushRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * This class is to build connection with the front-end service
 */
@FeignClient("service-sse-push")
public interface ServiceSsePushClient {

    @PostMapping(value = "/push")
    String push(@RequestBody PushRequest pushRequest);
}
