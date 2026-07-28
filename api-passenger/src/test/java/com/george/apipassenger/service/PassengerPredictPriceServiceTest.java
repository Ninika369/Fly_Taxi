package com.george.apipassenger.service;

import com.george.apipassenger.remote.ServicePriceClient;
import com.george.internalCommon.dto.ResponseResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassengerPredictPriceServiceTest {

    @Mock
    private ServicePriceClient servicePriceClient;

    @InjectMocks
    private PredictPriceService predictPriceService;

    @Test
    @DisplayName("Passenger API preserves downstream pricing failure")
    void shouldPreserveDownstreamFailure_whenPriceServiceRejectsPrediction() {
        when(servicePriceClient.predictPrice(any()))
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
        assertNotEquals(1, result.getCode());
    }
}
