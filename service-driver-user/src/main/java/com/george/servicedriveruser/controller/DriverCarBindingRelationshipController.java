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
 * <p>
 *  前端控制器
 * </p>
 *
 * @author george
 * @since 2024-11-03
 */
@RestController
@RequestMapping("/driver-car-binding-relationship")
public class DriverCarBindingRelationshipController {
    @Autowired
    private DriverCarBindingRelationshipService service;

    @PostMapping("/bind")
    public ResponseResult bind(@RequestBody DriverCarBindingRelationship bindingRelationship) {
        return service.bind(bindingRelationship);
    }

    @PostMapping("/unbind")
    public ResponseResult unbind(@RequestBody DriverCarBindingRelationship bindingRelationship) {
        return service.unbind(bindingRelationship);
    }
}
