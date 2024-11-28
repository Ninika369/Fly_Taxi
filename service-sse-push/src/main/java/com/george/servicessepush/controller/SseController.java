package com.george.servicessepush.controller;

import com.george.internalCommon.request.PushRequest;
import com.george.internalCommon.util.SsePrefixUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author: George Sun
 * @Date: 2024-11-21-23:26
 * @Description: This class is used to coordinate all the business on the front end as well as the back end
 */
@RestController
@Slf4j
public class SseController {
    // the map to store sseEmitter specific to each user
    public static Map<String, SseEmitter> sseEmitterMap = new HashMap<>();

    /**
     * This method is used to establish a connection between the front end and the server
     * @param userId
     * @param identity
     * @return
     */
    @GetMapping("/connect")
    public SseEmitter connect(@RequestParam Long userId, @RequestParam String identity){
        SseEmitter sseEmitter = new SseEmitter(0l);

        String sseMapKey = SsePrefixUtils.generatorSseKey(userId,identity);

        sseEmitterMap.put(sseMapKey, sseEmitter);
        log.info("built connection");

        return sseEmitter;
    }

    /**
     * This function is used to send messages to the front-end server
     * @param pushRequest
     * @return
     */
    @PostMapping("/push")
    public String push(@RequestBody PushRequest pushRequest) {

        Long userId = pushRequest.getUserId();
        String identity = pushRequest.getIdentity();
        String content = pushRequest.getContent();
        String sseMapKey = SsePrefixUtils.generatorSseKey(userId,identity);
        try {
            if (sseEmitterMap.containsKey(sseMapKey)){
                sseEmitterMap.get(sseMapKey).send(content);
            }else {
                return "Push unsuccessfully";
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return "To the user："+sseMapKey+", sent a message："+content;
    }

    /**
     * This method is used to close the connection
     * @param userId
     * @param identity
     * @return
     */
    @GetMapping("/close")
    public String close(@RequestParam Long userId, @RequestParam String identity){
        String sseMapKey = SsePrefixUtils.generatorSseKey(userId,identity);
        System.out.println("Closed："+sseMapKey);
        if (sseEmitterMap.containsKey(sseMapKey)){
            sseEmitterMap.remove(sseMapKey);
        }
        return "close successfully!";

    }
}
