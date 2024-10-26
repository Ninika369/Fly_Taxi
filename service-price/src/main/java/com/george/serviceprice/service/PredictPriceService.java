package com.george.serviceprice.service;

import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.dto.PriceRule;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.PredictPriceDTO;
import com.george.internalCommon.response.DirectionResponse;
import com.george.internalCommon.response.PredictPriceResponse;
import com.george.internalCommon.util.BigDecimalUtils;
import com.george.serviceprice.mapper.PriceRuleMapper;
import com.george.serviceprice.remote.ServiceMapClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: George Sun
 * @Date: 2024-10-24-15:28
 * @Description: This class is to predict the price of a single ride
 */
@Service
@Slf4j
public class PredictPriceService {

    // connect with map
    @Autowired
    private ServiceMapClient client;

    // connect with dataset
    @Autowired
    private PriceRuleMapper mapper;


    /**
     * This function is to predict the price of a single ride based on the longitude and latitude
     * of origin and destination.
     * @param depLatitude - the latitude of origin
     * @param depLongitude - the longitude of origin
     * @param destLatitude - the latitude of destination
     * @param destLongitude - the longitude of destination
     * @return
     */
    public ResponseResult predictPrice(String depLatitude, String depLongitude,
                                       String destLatitude, String destLongitude) {

        // assemble all the parameter into a single object
        PredictPriceDTO priceDTO = new PredictPriceDTO();
        priceDTO.setDepLatitude(depLatitude);
        priceDTO.setDepLongitude(depLongitude);
        priceDTO.setDestLatitude(destLatitude);
        priceDTO.setDestLongitude(destLongitude);

        // call map function to get distance and time
        ResponseResult<DirectionResponse> directionResponse = client.direction(priceDTO);
        Integer distance = directionResponse.getData().getDistance();
        Integer duration = directionResponse.getData().getDuration();

        // load calculation rules
        Map<String, Object> map = new HashMap<>();
        map.put("city_code", "110000");
        map.put("vehicle_type", "1");
        List<PriceRule> priceRules = mapper.selectByMap(map);
        if (priceRules.isEmpty()) {
            return ResponseResult.fail(CommonStatus.PRICE_RULE_NOT_EXISTS.getCode(),
                                        CommonStatus.PRICE_RULE_NOT_EXISTS.getMessage());
        }
        PriceRule rule = priceRules.get(0);

        // calculate the predicted price
        double price = calculatePrice(distance, duration, rule);
        PredictPriceResponse predictPriceResponse = new PredictPriceResponse();
        predictPriceResponse.setPrice(price);

        return ResponseResult.success(predictPriceResponse);
    }


    /**
     * Calculate the predicted price according to input parameters.
     * @param distance - distance of the ride
     * @param duration - the time spent on the ride
     * @param rule - the rules stipulating how to calculate the price
     * @return
     */
   private double calculatePrice(Integer distance, Integer duration, PriceRule rule) {
       double result = 0.0;

       // get the starting fare
       double startFare = rule.getStartFare();
       result = BigDecimalUtils.add(result, startFare);

       // get the miles fare
       // total miles in km
       double distanceMiles = BigDecimalUtils.divide(distance, 1000);
       double startMile = rule.getStartMile();
       // the miles need to be charged additionally
       double mileDiff = BigDecimalUtils.subtract(distanceMiles, startMile);
       // charge the extra fee only if needed
       double tollMiles = mileDiff < 0 ? 0 : mileDiff;
       double unitPricePerMile = rule.getUnitPricePerMile();
       double mileFare = BigDecimalUtils.multiply(tollMiles, unitPricePerMile);
       // add the extra mile charges to the total cost
       result = BigDecimalUtils.add(result, mileFare);


       // get duration fare
       double timeInMinutes = BigDecimalUtils.divide(duration, 60);
       double unitPricePerMinute = rule.getUnitPricePerMinute();
       double timeFare = BigDecimalUtils.multiply(timeInMinutes, unitPricePerMinute);
       result = BigDecimalUtils.add(timeFare, result);
       BigDecimal res = new BigDecimal(result);
       res = res.setScale(2, BigDecimal.ROUND_HALF_UP);

       return res.doubleValue();
   }

}
