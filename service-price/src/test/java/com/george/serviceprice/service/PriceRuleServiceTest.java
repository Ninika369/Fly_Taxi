package com.george.serviceprice.service;

import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.dto.PriceRule;
import com.george.internalCommon.dto.ResponseResult;
import com.george.serviceprice.mapper.PriceRuleMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link PriceRuleService#edit(PriceRule)}.
 *
 * edit() manages pricing rule versioning:
 *   - If no existing rule: insert as version 1
 *   - If existing rule with identical fields: reject ("no change")
 *   - If existing rule with at least one field changed: insert as version N+1
 *
 * Together with PredictPriceServiceTest, these form a complete "pricing
 * subsystem" test narrative: how rules are versioned + how prices are calculated.
 */
@ExtendWith(MockitoExtension.class)
class PriceRuleServiceTest {

    @Mock
    PriceRuleMapper priceRuleMapper;

    @InjectMocks
    PriceRuleService priceRuleService;

    // The new rule being submitted for editing
    private PriceRule newRule;

    @BeforeEach
    void setUp() {
        newRule = new PriceRule();
        newRule.setCityCode("AKL");
        newRule.setVehicleType("SUV");
        newRule.setStartFare(12.0);
        newRule.setStartMile(3);
        newRule.setUnitPricePerMile(2.5);
        newRule.setUnitPricePerMinute(0.6);
    }

    /**
     * Helper: create an existing rule in the "database" with given version.
     * Returns the existing rule so tests can customize its fields if needed.
     */
    private PriceRule givenExistingRule(int version, double startFare, int startMile,
                                        double pricePerMile, double pricePerMinute) {
        PriceRule existing = new PriceRule();
        existing.setCityCode("AKL");
        existing.setVehicleType("SUV");
        existing.setFareType("AKL$SUV");
        existing.setFareVersion(version);
        existing.setStartFare(startFare);
        existing.setStartMile(startMile);
        existing.setUnitPricePerMile(pricePerMile);
        existing.setUnitPricePerMinute(pricePerMinute);

        List<PriceRule> existingRules = new ArrayList<>();
        existingRules.add(existing);
        // any() matches any QueryWrapper — we don't care about the exact query,
        // just what the database "returns"
        when(priceRuleMapper.selectList(any())).thenReturn(existingRules);

        return existing;
    }

    private void givenNoExistingRules() {
        when(priceRuleMapper.selectList(any())).thenReturn(Collections.emptyList());
    }

    // ======================== No existing rule ========================

    @Test
    @DisplayName("No existing rule: inserts as version 1")
    void should_insertAsVersion1_when_noExistingRule() {
        givenNoExistingRules();

        ResponseResult result = priceRuleService.edit(newRule);

        assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
        assertEquals(1, newRule.getFareVersion());
        assertEquals("AKL$SUV", newRule.getFareType());
        verify(priceRuleMapper, times(1)).insert(newRule);
    }

    // ======================== Existing rule, fields changed ========================

    @Test
    @DisplayName("Existing rule with different startFare: inserts as next version")
    void should_incrementVersion_when_startFareChanged() {
        // Old rule: startFare=10.0, new rule: startFare=12.0
        givenExistingRule(3, 10.0, 3, 2.5, 0.6);

        ResponseResult result = priceRuleService.edit(newRule);

        assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
        assertEquals(4, newRule.getFareVersion());  // was 3, now 4
        verify(priceRuleMapper, times(1)).insert(newRule);
    }

    @Test
    @DisplayName("Existing rule with different unitPricePerMile: inserts as next version")
    void should_incrementVersion_when_mileagePriceChanged() {
        // Old: pricePerMile=2.0, new: pricePerMile=2.5
        givenExistingRule(1, 12.0, 3, 2.0, 0.6);

        ResponseResult result = priceRuleService.edit(newRule);

        assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
        assertEquals(2, newRule.getFareVersion());
        verify(priceRuleMapper, times(1)).insert(newRule);
    }

    @Test
    @DisplayName("Existing rule with different unitPricePerMinute: inserts as next version")
    void should_incrementVersion_when_timePriceChanged() {
        // Old: pricePerMinute=0.5, new: pricePerMinute=0.6
        givenExistingRule(5, 12.0, 3, 2.5, 0.5);

        ResponseResult result = priceRuleService.edit(newRule);

        assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
        assertEquals(6, newRule.getFareVersion());
        verify(priceRuleMapper, times(1)).insert(newRule);
    }

    @Test
    @DisplayName("Existing rule with different startMile: inserts as next version")
    void should_incrementVersion_when_startMileChanged() {
        // Old: startMile=5, new: startMile=3
        givenExistingRule(2, 12.0, 5, 2.5, 0.6);

        ResponseResult result = priceRuleService.edit(newRule);

        assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
        assertEquals(3, newRule.getFareVersion());
        verify(priceRuleMapper, times(1)).insert(newRule);
    }

    // ======================== Existing rule, nothing changed ========================

    @Test
    @DisplayName("Existing rule with identical fields: rejects with PRICE_RULE_NOT_EDIT")
    void should_reject_when_allFieldsIdentical() {
        // Old rule has exact same values as newRule
        givenExistingRule(2, 12.0, 3, 2.5, 0.6);

        ResponseResult result = priceRuleService.edit(newRule);

        assertEquals(CommonStatus.PRICE_RULE_NOT_EDIT.getCode(), result.getCode());
        // Must NOT insert anything — the rule hasn't changed
        verify(priceRuleMapper, never()).insert(any());
    }

    // ======================== fareType construction ========================

    @Test
    @DisplayName("fareType is correctly constructed as cityCode$vehicleType")
    void should_constructFareType_when_editing() {
        givenNoExistingRules();

        priceRuleService.edit(newRule);

        assertEquals("AKL$SUV", newRule.getFareType());
    }

    // ======================== Version incrementing correctness ========================

    @Test
    @DisplayName("Version increments from existing latest, not from 0")
    void should_incrementFromLatestVersion_when_multipleVersionsExist() {
        // Existing rule is version 7 — new rule should be version 8
        givenExistingRule(7, 10.0, 3, 2.5, 0.6);

        priceRuleService.edit(newRule);

        assertEquals(8, newRule.getFareVersion());
    }
}
