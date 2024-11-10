package com.george.servicemap.service;


import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.TrackResponse;
import com.george.servicemap.remote.TrackClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TrackService {

    @Autowired
    TrackClient trackClient;

    public ResponseResult<TrackResponse> add(String tid){

        return trackClient.add(tid);
    }
}
