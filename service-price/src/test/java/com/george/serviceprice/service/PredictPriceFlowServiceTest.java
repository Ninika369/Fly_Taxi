package com.george.serviceprice.service;

import com.george.internalCommon.dto.ResponseResult;
import com.george.serviceprice.mapper.PriceRuleMapper;
import com.george.serviceprice.remote.ServiceMapClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PredictPriceFlowServiceTest {

    @Mock
    private ServiceMapClient serviceMapClient;

    @Mock
    private PriceRuleMapper priceRuleMapper;

    @InjectMocks
    private PredictPriceService predictPriceService;

    @Test
    @DisplayName("Map service failure is preserved before pricing rule lookup")
    void shouldPreserveMapFailureAndSkipPriceRuleLookup_whenDirectionFails() {
        when(serviceMapClient.direction(any()))
                .thenReturn(ResponseResult.fail(1401, "Map direction request failed"));

        ResponseResult result = predictPriceService.predictPrice(
                "-36.8485",
                "174.7633",
                "-36.8519",
                "174.7762",
                "110000",
                "1");

        assertEquals(1401, result.getCode());
        assertEquals("Map direction request failed", result.getMessage());
        verify(priceRuleMapper, never()).selectList(any());
    }
}
