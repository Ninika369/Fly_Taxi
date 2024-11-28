package com.george.serviceprice.controller;



import com.george.internalCommon.dto.PriceRule;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.PriceRuleIsNewRequest;
import com.george.serviceprice.service.PriceRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * This is used to deal with matters relating to pricing rules
 */
@RestController
@RequestMapping("/price-rule")
public class PriceRuleController {

    @Autowired
    PriceRuleService priceRuleService;

    /**
     * This method is used to add pricing rules
     * @param priceRule
     * @return
     */
    @PostMapping("/add")
    public ResponseResult add(@RequestBody PriceRule priceRule){

        return priceRuleService.add(priceRule);
    }

    /**
     * This method is used to edit pricing rules
     * @param priceRule
     * @return
     */
    @PostMapping("/edit")
    public ResponseResult edit(@RequestBody PriceRule priceRule){

        return priceRuleService.edit(priceRule);
    }

    /**
     * This method is used to get the rule with the latest version
     * @param fareType
     * @return
     */
    @GetMapping("/get-latest-version")
    public ResponseResult<PriceRule> getLatestVersion(@RequestParam String fareType){
        return priceRuleService.getLatestVersion(fareType);
    }

    /**
     * This method is used to determine whether the rule is the latest version
     * @param priceRuleIsNewRequest
     * @return
     */
    @PostMapping("/is-latest")
    public ResponseResult<Boolean> isLatest(@RequestBody PriceRuleIsNewRequest priceRuleIsNewRequest){

        return priceRuleService.isLatest(priceRuleIsNewRequest.getFareType(),priceRuleIsNewRequest.getFareVersion());

    }

    /**
     * This method is used to determine whether the pricing rules of the city and
     * the corresponding vehicle type exist
     * @param priceRule
     * @return
     */
    @PostMapping("/if-exists")
    public ResponseResult<Boolean> ifExists(@RequestBody PriceRule priceRule){
        return priceRuleService.ifExists(priceRule);
    }
}