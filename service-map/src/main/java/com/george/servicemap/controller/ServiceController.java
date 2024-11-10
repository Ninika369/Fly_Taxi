package com.george.servicemap.controller;

import com.george.internalCommon.dto.ResponseResult;
import com.george.servicemap.service.ServiceFromMapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * This class is used to deal with service in Amap
 */
@RestController
@RequestMapping("/service")
public class ServiceController {

    @Autowired
    private ServiceFromMapService serviceFromMapService;

    /**
     * This function is used to add a service in Amap
     * @param name
     * @return
     */
    @PostMapping("/add")
    public ResponseResult add(String name){

        return serviceFromMapService.add(name);
    }
}
