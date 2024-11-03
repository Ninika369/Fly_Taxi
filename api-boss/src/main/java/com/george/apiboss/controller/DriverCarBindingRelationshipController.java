package com.george.apiboss.controller;

import com.george.apiboss.service.DriverCarBindingRelationshipService;
import com.george.internalCommon.dto.DriverCarBindingRelationship;
import com.george.internalCommon.dto.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: George Sun
 * @Date: 2024-11-03-17:11
 * @Description: com.george.apiboss.controller
 */
@RestController
@RequestMapping("/driver-car-binding-relationship")
public class DriverCarBindingRelationshipController {
    @Autowired
    private DriverCarBindingRelationshipService service;

    @PostMapping("/bind")
    public ResponseResult bind(@RequestBody DriverCarBindingRelationship relationship) {
        return service.bind(relationship);
    }

    @PostMapping("/unbind")
    public ResponseResult unbind(@RequestBody DriverCarBindingRelationship relationship) {
        return service.unbind(relationship);
    }
}
