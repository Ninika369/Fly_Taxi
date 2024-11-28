package com.george.servicedriveruser.controller;

import com.george.internalCommon.constant.DriverCarConstant;
import com.george.internalCommon.dto.DriverCarBindingRelationship;
import com.george.internalCommon.dto.DriverUser;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.DriverUserExistsResponse;
import com.george.internalCommon.response.OrderDriverResponse;
import com.george.servicedriveruser.service.DriverCarBindingRelationshipService;
import com.george.servicedriveruser.service.DriverUserService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: George Sun
 * @Date: 2024-10-31-11:06
 * @Description: This class is to control the function of a driver
 */
@RestController
@Slf4j
public class UserController {

    @Autowired
    private DriverUserService service;

    @Autowired
    private DriverUserService driverUserService;

    /**
     * add a driver in database
     * @param user
     * @return
     */
    @PostMapping("/user")
    public ResponseResult addUser(@RequestBody DriverUser user) {
        return service.addDriverUser(user);
    }

    /**
     * update the information of a driver
     * @param user
     * @return
     */
    @PutMapping("/user")
    public ResponseResult updateUser(@RequestBody DriverUser user) {
        return service.updateDriverUser(user);
    }

    /**
     * check the existence of a driver according to the phone number
     * @param driverPhone - the phone number of the driver
     * @return - 1 means the driver exists, otherwise 0
     */
    @GetMapping("/check-driver/{driverPhone}")
    public ResponseResult<DriverUserExistsResponse> getUser(@PathVariable("driverPhone") String driverPhone){

        ResponseResult<DriverUser> driverUserByPhone = driverUserService.getDriverUserByPhone(driverPhone);
        DriverUser driverUserDb = driverUserByPhone.getData();

        DriverUserExistsResponse response = new DriverUserExistsResponse();

        int ifExists = DriverCarConstant.DRIVER_EXISTS;
        if (driverUserDb == null){
            ifExists = DriverCarConstant.DRIVER_NOT_EXISTS;
            response.setDriverPhone(driverPhone);
        }else {
            response.setDriverPhone(driverUserDb.getDriverPhone());
        }
        response.setIfExists(ifExists);

        return ResponseResult.success(response);
    }

    /**
     * 根据车辆Id查询订单需要的司机信息
     * @param carId
     * @return
     */
    @GetMapping("/get-available-driver/{carId}")
    public ResponseResult<OrderDriverResponse> getAvailableDriver(@PathVariable("carId") Long carId){
        return driverUserService.getAvailableDriver(carId);
    }

    @Autowired
    DriverCarBindingRelationshipService driverCarBindingRelationshipService;

    /**
     * 根据司机手机号查询司机和车辆绑定关系
     * @param driverPhone
     * @return
     */
    @GetMapping("/driver-car-binding-relationship")
    public ResponseResult<DriverCarBindingRelationship> getDriverCarRelationShip(@RequestParam String driverPhone){
        return driverCarBindingRelationshipService.getDriverCarRelationShipByDriverPhone(driverPhone);
    }
}
