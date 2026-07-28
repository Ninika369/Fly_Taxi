package com.george.servicemap.remote;

import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.TrsearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerminalClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private TerminalClient terminalClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(terminalClient, "amapKey", "test-key");
        ReflectionTestUtils.setField(terminalClient, "amapSid", "test-sid");
    }

    @Test
    @DisplayName("Amap track milliseconds are converted to seconds")
    void shouldConvertAmapTrackMillisecondsToSeconds() {
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{"
                        + "\"errcode\":10000,"
                        + "\"errmsg\":\"OK\","
                        + "\"data\":{"
                        + "\"counts\":1,"
                        + "\"tracks\":[{\"distance\":5000,\"time\":600000}]"
                        + "}"
                        + "}"));

        ResponseResult<TrsearchResponse> result = terminalClient.trsearch("tid-1", 1000L, 2000L);

        assertEquals(5000L, result.getData().getDriveMile());
        assertEquals(600L, result.getData().getDriveTime());
    }

    @Test
    @DisplayName("Amap track milliseconds are aggregated before conversion")
    void shouldAggregateTrackMillisecondsBeforeConvertingToSeconds() {
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{"
                        + "\"errcode\":10000,"
                        + "\"errmsg\":\"OK\","
                        + "\"data\":{"
                        + "\"counts\":2,"
                        + "\"tracks\":["
                        + "{\"distance\":1000,\"time\":30000},"
                        + "{\"distance\":2000,\"time\":45000}"
                        + "]"
                        + "}"
                        + "}"));

        ResponseResult<TrsearchResponse> result = terminalClient.trsearch("tid-1", 1000L, 2000L);

        assertEquals(3000L, result.getData().getDriveMile());
        assertEquals(75L, result.getData().getDriveTime());
    }
}
