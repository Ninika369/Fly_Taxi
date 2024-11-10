package com.george.apidriver.service;


import com.george.apidriver.remote.ServiceDriverUserClient;
import com.george.apidriver.remote.ServiceMapClient;
import com.george.internalCommon.dto.Car;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.ApiDriverPointRequest;
import com.george.internalCommon.request.PointRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * This class contains the methods to upload the tracks info
 */
@Service
public class PointService {

    // client used to connect with map service directly
    @Autowired
    private ServiceMapClient serviceMapClient;

    // client used to connect with a driver server
    @Autowired
    private ServiceDriverUserClient serviceDriverUserClient;

    /**
     * This method is used to upload the traces using terminal and track ids
     * @param apiDriverPointRequest - the class that contains the id of the terminal
     * @return
     */
    public ResponseResult upload(ApiDriverPointRequest apiDriverPointRequest){
        // obtain carId
        Long carId = apiDriverPointRequest.getCarId();

        // get tid and trid by carid，using the interface of service-driver-user
        ResponseResult<Car> carById = serviceDriverUserClient.getCarById(carId);
        Car car = carById.getData();
        String tid = car.getTid();
        String trid = car.getTrid();

        // upload to Amap using terminal and track id
        PointRequest pointRequest = new PointRequest();
        pointRequest.setTid(tid);
        pointRequest.setTrid(trid);
        pointRequest.setPoints(apiDriverPointRequest.getPoints());

        return serviceMapClient.upload(pointRequest);

    }
}
