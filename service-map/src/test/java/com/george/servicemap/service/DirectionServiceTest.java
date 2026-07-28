package com.george.servicemap.service;

import com.george.internalCommon.dto.ResponseResult;
import com.george.servicemap.remote.MapDirectionException;
import com.george.servicemap.remote.MapServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectionServiceTest {

    @Mock
    private MapServiceClient mapServiceClient;

    @InjectMocks
    private DirectionService directionService;

    @Test
    @DisplayName("Map adapter failure is converted to stable map direction failure")
    void shouldReturnMapDirectionError_whenMapClientThrowsDirectionException() {
        when(mapServiceClient.direction("-36.8485", "174.7633", "-36.8519", "174.7762"))
                .thenThrow(new MapDirectionException("invalid map direction response",
                        new IllegalArgumentException("key=synthetic-secret")));

        ResponseResult result = directionService.driving(
                "-36.8485",
                "174.7633",
                "-36.8519",
                "174.7762");

        assertEquals(1401, result.getCode());
        assertEquals("Map direction request failed", result.getMessage());
        assertFalse(result.getMessage().contains("key=synthetic-secret"));
    }
}
