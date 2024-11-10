package com.george.servicemap.service;


import com.george.internalCommon.dto.ResponseResult;
import com.george.servicemap.remote.ServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ServiceFromMapService {

    @Autowired
    private ServiceClient serviceClient;


    public ResponseResult add(String name){

        return serviceClient.add(name);

    }
}
