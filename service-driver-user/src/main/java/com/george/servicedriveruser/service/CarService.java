package com.george.servicedriveruser.service;

import com.george.internalCommon.dto.Car;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.TerminalResponse;
import com.george.internalCommon.response.TrackResponse;
import com.george.servicedriveruser.mapper.CarMapper;
import com.george.servicedriveruser.remote.ServiceMapClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: George Sun
 * @Date: 2024-11-01-22:34
 * @Description: This class provides service to add a car in database
 */
@Service
public class CarService {

    @Autowired
    private CarMapper mapper;

    @Autowired
    private ServiceMapClient serviceMapClient;

    public ResponseResult addCar(Car car) {
        car.setGmtCreate(LocalDateTime.now());
        car.setGmtModified(LocalDateTime.now());
        // insert car into database
        mapper.insert(car);

        // register this vehicle as a terminal in Amap before inserting it into database and get tid
        ResponseResult<TerminalResponse> responseResult = serviceMapClient.addTerminal(car.getVehicleNo(),
                car.getId()+"");
        String tid = responseResult.getData().getTid();
        car.setTid(tid);

        // create a trace and get the trid
        ResponseResult<TrackResponse> trackResponseResult = serviceMapClient.addTrack(tid);
        String trid = trackResponseResult.getData().getTrid();
        String trname = trackResponseResult.getData().getTrname();
        car.setTrid(trid);
        car.setTrname(trname);

        mapper.updateById(car);
        return ResponseResult.success("");
    }


    public ResponseResult getCarById(Long id){
        Map<String,Object> map = new HashMap<>();
        map.put("id",id);

        List<Car> cars = mapper.selectByMap(map);

        return ResponseResult.success(cars.get(0));

    }

}
