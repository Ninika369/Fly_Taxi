package com.george.servicedriveruser.controller;


import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.dto.DriverCarBindingRelationship;
import com.george.servicedriveruser.service.DriverCarBindingRelationshipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * This class is to bind the relationship between a driver and a vehicle
 */
@RestController
@RequestMapping("/driver-car-binding-relationship")
public class DriverCarBindingRelationshipController {
    @Autowired
    private DriverCarBindingRelationshipService service;

    /**
     * The function used to bind the relationship between a driver and a vehicle
     * @param bindingRelationship - the binding relationship
     * @return
     */
    @PostMapping("/bind")
    public ResponseResult bind(@RequestBody DriverCarBindingRelationship bindingRelationship) {
        return service.bind(bindingRelationship);
    }

    /**
     * The function used to unbind the relationship between a driver and a vehicle
     * @param bindingRelationship - the un-binding relationship
     * @return
     */
    @PostMapping("/unbind")
    public ResponseResult unbind(@RequestBody DriverCarBindingRelationship bindingRelationship) {
        return service.unbind(bindingRelationship);
    }
}
