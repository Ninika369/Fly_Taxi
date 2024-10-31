package com.george.servicemap.controller;

import com.george.internalCommon.dto.DicDistrict;
import com.george.servicemap.mapper.DicDistrictMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: George Sun
 * @Date: 2024-10-27-18:04
 * @Description: com.george.servicemap.controller
 */
@RestController
public class TestController {
    @Autowired
    DicDistrictMapper mapper;

    @GetMapping("/test-map")
    public String testMap() {

        Map<String, Object> map = new HashMap<>();
        map.put("address_code", "110000");
        List<DicDistrict> dicDistricts = mapper.selectByMap(map);
        System.out.println(dicDistricts);
        return "test succeeds!";

    }
}
