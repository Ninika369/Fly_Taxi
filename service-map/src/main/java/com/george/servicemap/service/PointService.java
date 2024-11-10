package com.george.servicemap.service;


import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.PointRequest;
import com.george.servicemap.remote.PointClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PointService {

    @Autowired
    PointClient pointClient;

    public ResponseResult upload(PointRequest pointRequest){

        return pointClient.upload(pointRequest);
    }
}
