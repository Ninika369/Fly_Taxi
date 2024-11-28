package com.george.serviceorder.remote;


import com.george.internalCommon.dto.PriceRule;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.PriceRuleIsNewRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * This class is used to connect with the pricing system to check the version of price rule
 * or calculate the final price of a ride.
 */
@FeignClient("service-price")
public interface ServicePriceClient {

    @PostMapping("/price-rule/is-latest")
    ResponseResult<Boolean> isLatest(@RequestBody PriceRuleIsNewRequest priceRuleIsNewRequest);

    @RequestMapping(method = RequestMethod.GET,value = "/price-rule/if-exists")
    ResponseResult<Boolean> ifPriceExists(@RequestBody PriceRule priceRule);

    @RequestMapping(method = RequestMethod.POST, value = "/calculate-price")
    ResponseResult<Double> calculatePrice(@RequestParam Integer distance , @RequestParam Integer duration, @RequestParam String cityCode, @RequestParam String vehicleType);
}
