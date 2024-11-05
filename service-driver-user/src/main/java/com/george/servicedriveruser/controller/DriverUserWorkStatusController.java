package com.george.servicedriveruser.controller;


import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.dto.DriverUserWorkStatus;
import com.george.servicedriveruser.service.DriverUserWorkStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * The class is to change the working status of a driver (stop, start or suspend)
 */
@RestController
public class DriverUserWorkStatusController {
    @Autowired
    private DriverUserWorkStatusService service;

    @RequestMapping("/driver-user-work-status")
    public ResponseResult changeWorkStatus(@RequestBody DriverUserWorkStatus driverUserWorkStatus) {

        return service.changeWorkStatus(driverUserWorkStatus.getDriverId(), driverUserWorkStatus.getWorkStatus());
    }
}
