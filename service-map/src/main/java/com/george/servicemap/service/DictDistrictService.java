package com.george.servicemap.service;

import com.george.internalCommon.constant.AmapConfigConstant;
import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.dto.DicDistrict;
import com.george.internalCommon.dto.ResponseResult;
import com.george.servicemap.mapper.DicDistrictMapper;
import com.george.servicemap.remote.MapDicDistrictClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @Author: George Sun
 * @Date: 2024-10-28-13:32
 * @Description: com.george.servicemap.service
 */
@Service
public class DictDistrictService {

    /**
     * Construct a new DictDistrictService  with
     */
    @Autowired
    private MapDicDistrictClient client;

    @Autowired
    private DicDistrictMapper mapper;

    public ResponseResult initDicDistrict(String keywords) {

        String dicDistrictResult = client.dicDistrict(keywords);
        System.out.println(dicDistrictResult);
        // interpret the result json object
        JSONObject dicDistrictJsonObject = new JSONObject(dicDistrictResult);
        int status = dicDistrictJsonObject.getInt(AmapConfigConstant.STATUS);
        if (status != 1) {
            ResponseResult.fail(CommonStatus.MAP_DISTRICT_ERROR.getCode(),
                    CommonStatus.MAP_DISTRICT_ERROR.getMessage());
        }

        JSONArray countryArray = dicDistrictJsonObject.getJSONArray(AmapConfigConstant.DISTRICTS);
        for (int i = 0; i < countryArray.length(); i++) {
            JSONObject countryObject  = countryArray.getJSONObject(i);
            String countryAddressCode = countryObject.getString(AmapConfigConstant.ADCODE);
            String countryNameCode = countryObject.getString(AmapConfigConstant.NAME);
            String countryParentAddressCode = "0";
            String countryLevel = countryObject.getString(AmapConfigConstant.LEVEL);
            int countryLevelInt = getLevel(countryLevel);

            addDistrict(countryAddressCode, countryNameCode, countryParentAddressCode, countryLevelInt);

            JSONArray provinceArray = countryObject.getJSONArray(AmapConfigConstant.DISTRICTS);
            for (int j = 0; j < provinceArray.length(); j++) {
                JSONObject provinceObject  = provinceArray.getJSONObject(j);
                String provinceAddressCode = provinceObject.getString(AmapConfigConstant.ADCODE);
                String provinceNameCode = provinceObject.getString(AmapConfigConstant.NAME);
                String provinceParentAddressCode = countryAddressCode;
                String provinceLevel = provinceObject.getString(AmapConfigConstant.LEVEL);
                int provinceLevelInt = getLevel(provinceLevel);

                addDistrict(provinceAddressCode, provinceNameCode, provinceParentAddressCode, provinceLevelInt);

                JSONArray cityArray = provinceObject.getJSONArray(AmapConfigConstant.DISTRICTS);
                for (int k = 0; k < cityArray.length(); k++) {
                    JSONObject cityObject  = cityArray.getJSONObject(k);
                    String cityAddressCode = cityObject.getString(AmapConfigConstant.ADCODE);
                    String cityNameCode = cityObject.getString(AmapConfigConstant.NAME);
                    String cityParentAddressCode = provinceAddressCode;
                    String cityLevel = cityObject.getString(AmapConfigConstant.LEVEL);
                    int cityLevelInt = getLevel(cityLevel);

                    addDistrict(cityAddressCode, cityNameCode, cityParentAddressCode, cityLevelInt);

                    JSONArray districtArray = cityObject.getJSONArray(AmapConfigConstant.DISTRICTS);
                    for (int x = 0; x < districtArray.length(); x++) {
                        JSONObject districtObject  = districtArray.getJSONObject(x);
                        String districtAddressCode = districtObject.getString(AmapConfigConstant.ADCODE);
                        String districtNameCode = districtObject.getString(AmapConfigConstant.NAME);
                        String districtParentAddressCode = cityAddressCode;
                        String districtLevel = districtObject.getString(AmapConfigConstant.LEVEL);
                        if (districtLevel.equals(AmapConfigConstant.STREET))
                            continue;
                        int districtLevelInt = getLevel(districtLevel);

                        addDistrict(districtAddressCode, districtNameCode, districtParentAddressCode, districtLevelInt);

                    }
                }
            }

        }

        return ResponseResult.success("");
    }

    public void addDistrict(String addressCode, String nameCode, String parentAddressCode, int levelInt) {
        DicDistrict dicDistrict = new DicDistrict();
        dicDistrict.setAddressCode(addressCode);
        dicDistrict.setAddressName(nameCode);
        dicDistrict.setParentAddressCode(parentAddressCode);
        dicDistrict.setLevel(levelInt);
        // insert into dataset
        mapper.insert(dicDistrict);
    }

    public int getLevel(String level) {
        int levelInt = 0;
        if (level.trim().equals("country")) {
            levelInt = 0;
        }
        else if (level.trim().equals("province")) {
            levelInt = 1;
        }
        else if (level.trim().equals("city")) {
            levelInt = 2;
        }
        else {
            levelInt = 3;
        }
        return levelInt;
    }
}
