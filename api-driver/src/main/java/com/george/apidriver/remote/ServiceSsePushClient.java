package com.george.apidriver.remote;

import com.george.internalCommon.request.PushRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

// This class is used to send messages to the passenger client
@FeignClient("service-sse-push")
public interface ServiceSsePushClient {


    @RequestMapping(method = RequestMethod.POST,value = "/push")
    String push(@RequestBody PushRequest pushRequest);
}
