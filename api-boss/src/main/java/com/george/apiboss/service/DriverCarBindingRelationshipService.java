package com.george.apiboss.service;

import com.george.apiboss.remote.ServiceDriverUserClient;
import com.george.internalCommon.dto.DriverCarBindingRelationship;
import com.george.internalCommon.dto.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Author: George Sun
 * @Date: 2024-11-03-17:26
 * @Description: com.george.apiboss.service
 */
@Service
public class DriverCarBindingRelationshipService {

    @Autowired
    ServiceDriverUserClient client;

    public ResponseResult bind(DriverCarBindingRelationship relationship) {
        return client.bind(relationship);
    }

    public ResponseResult unbind(DriverCarBindingRelationship relationship) {
        return client.unbind(relationship);
    }
}
