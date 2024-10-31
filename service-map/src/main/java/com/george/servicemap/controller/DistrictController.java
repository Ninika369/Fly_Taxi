package com.george.servicemap.controller;

import com.george.internalCommon.dto.ResponseResult;
import com.george.servicemap.service.DictDistrictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: George Sun
 * @Date: 2024-10-28-13:31
 * @Description: com.george.servicemap.controller
 */
@RestController
public class DistrictController {

    @Autowired
    private DictDistrictService service;

    @GetMapping("/dic-district")
    public ResponseResult initDicDistrict(String keywords) {
        return service.initDicDistrict(keywords);
    }
}
