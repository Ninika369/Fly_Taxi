package com.george.serviceprice.controller;

import com.george.internalCommon.dto.PriceRule;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.PriceRuleIsNewRequest;
import com.george.serviceprice.service.PriceRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * This is used to deal with matters relating to pricing rules
 */
@RestController
@RequestMapping("/price-rule")
@Tag(name = "Price Rules", description = "Endpoints for managing pricing rules by city and vehicle type")
public class PriceRuleController {

    @Autowired
    PriceRuleService priceRuleService;

    /**
     * This method is used to add pricing rules
     * @param priceRule
     * @return
     */
    @Operation(summary = "Add a new pricing rule",
            description = "Creates a new pricing rule with version 1 for a given city and vehicle type")
    @PostMapping("/add")
    public ResponseResult add(@RequestBody PriceRule priceRule){

        return priceRuleService.add(priceRule);
    }

    /**
     * This method is used to edit pricing rules
     * @param priceRule
     * @return
     */
    @Operation(summary = "Edit an existing pricing rule",
            description = "Creates a new version of the pricing rule if any field has changed; rejects if identical to current version")
    @PostMapping("/edit")
    public ResponseResult edit(@RequestBody PriceRule priceRule){

        return priceRuleService.edit(priceRule);
    }

    /**
     * This method is used to get the rule with the latest version
     * @param fareType
     * @return
     */
    @Operation(summary = "Get latest pricing rule version",
            description = "Returns the most recent version of the pricing rule for a given fareType (cityCode$vehicleType)")
    @GetMapping("/get-latest-version")
    public ResponseResult<PriceRule> getLatestVersion(@RequestParam String fareType){
        return priceRuleService.getLatestVersion(fareType);
    }

    /**
     * This method is used to determine whether the rule is the latest version
     * @param priceRuleIsNewRequest
     * @return
     */
    @Operation(summary = "Check if a pricing rule version is newest",
            description = "Returns true if the given fareType and fareVersion match the latest version in the database")
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
    @Operation(summary = "Check if a pricing rule exists",
            description = "Returns true if a pricing rule exists for the given city code and vehicle type combination")
    @PostMapping("/if-exists")
    public ResponseResult<Boolean> ifExists(@RequestBody PriceRule priceRule){
        return priceRuleService.ifExists(priceRule);
    }
}