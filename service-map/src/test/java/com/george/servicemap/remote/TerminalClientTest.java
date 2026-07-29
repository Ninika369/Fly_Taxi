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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

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
                        + "{\"distance\":1000,\"time\":30500},"
                        + "{\"distance\":2000,\"time\":45500}"
                        + "]"
                        + "}"
                        + "}"));

        ResponseResult<TrsearchResponse> result = terminalClient.trsearch("tid-1", 1000L, 2000L);
        long perSegmentTruncated =
                TimeUnit.MILLISECONDS.toSeconds(30500L)
                        + TimeUnit.MILLISECONDS.toSeconds(45500L);

        assertEquals(3000L, result.getData().getDriveMile());
        assertEquals(76L, result.getData().getDriveTime());
        assertEquals(75L, perSegmentTruncated);
        assertNotEquals(perSegmentTruncated, result.getData().getDriveTime());
    }

    @Test
    @DisplayName("Amap track search with no tracks returns a domain failure")
    void shouldReturnTrackEmptyFailure_whenAmapHasNoTracks() {
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{"
                        + "\"errcode\":10000,"
                        + "\"errmsg\":\"OK\","
                        + "\"data\":{"
                        + "\"counts\":0,"
                        + "\"tracks\":[]"
                        + "}"
                        + "}"));

        ResponseResult<TrsearchResponse> result = terminalClient.trsearch("tid-1", 1000L, 2000L);

        assertNotNull(result);
        assertEquals(1402, result.getCode());
        assertEquals("No track data is available for the requested interval", result.getMessage());
        assertEquals(null, result.getData());
    }

    @Test
    @DisplayName("Amap track search with positive count but empty tracks returns a domain failure")
    void shouldReturnTrackEmptyFailure_whenAmapReportsPositiveCountWithEmptyTracks() {
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{"
                        + "\"errcode\":10000,"
                        + "\"errmsg\":\"OK\","
                        + "\"data\":{"
                        + "\"counts\":1,"
                        + "\"tracks\":[]"
                        + "}"
                        + "}"));

        ResponseResult<TrsearchResponse> result = terminalClient.trsearch("tid-1", 1000L, 2000L);

        assertNotNull(result);
        assertEquals(1402, result.getCode());
        assertEquals("No track data is available for the requested interval", result.getMessage());
        assertEquals(null, result.getData());
    }

    @Test
    @DisplayName("Amap track search rejects negative distance")
    void shouldReturnDownstreamResponseError_whenTrackDistanceIsNegative() {
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{"
                        + "\"errcode\":10000,"
                        + "\"errmsg\":\"OK\","
                        + "\"data\":{"
                        + "\"counts\":1,"
                        + "\"tracks\":[{\"distance\":-1,\"time\":600000}]"
                        + "}"
                        + "}"));

        ResponseResult<TrsearchResponse> result = terminalClient.trsearch("tid-1", 1000L, 2000L);

        assertNotNull(result);
        assertEquals(1700, result.getCode());
        assertEquals("Downstream service returned an invalid response", result.getMessage());
        assertEquals(null, result.getData());
    }

    @Test
    @DisplayName("Amap track search rejects negative duration")
    void shouldReturnDownstreamResponseError_whenTrackTimeIsNegative() {
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{"
                        + "\"errcode\":10000,"
                        + "\"errmsg\":\"OK\","
                        + "\"data\":{"
                        + "\"counts\":1,"
                        + "\"tracks\":[{\"distance\":5000,\"time\":-1}]"
                        + "}"
                        + "}"));

        ResponseResult<TrsearchResponse> result = terminalClient.trsearch("tid-1", 1000L, 2000L);

        assertNotNull(result);
        assertEquals(1700, result.getCode());
        assertEquals("Downstream service returned an invalid response", result.getMessage());
        assertEquals(null, result.getData());
    }

    @Test
    @DisplayName("Amap track search rejects missing distance or duration")
    void shouldReturnDownstreamResponseError_whenTrackFieldsAreMissing() {
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{"
                        + "\"errcode\":10000,"
                        + "\"errmsg\":\"OK\","
                        + "\"data\":{"
                        + "\"counts\":1,"
                        + "\"tracks\":[{\"distance\":5000}]"
                        + "}"
                        + "}"));

        ResponseResult<TrsearchResponse> result = terminalClient.trsearch("tid-1", 1000L, 2000L);

        assertNotNull(result);
        assertEquals(1700, result.getCode());
        assertEquals("Downstream service returned an invalid response", result.getMessage());
        assertEquals(null, result.getData());
    }

    @Test
    @DisplayName("Amap track search rejects distance aggregation overflow")
    void shouldReturnDownstreamResponseError_whenTrackDistanceAggregationOverflows() {
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{"
                        + "\"errcode\":10000,"
                        + "\"errmsg\":\"OK\","
                        + "\"data\":{"
                        + "\"counts\":2,"
                        + "\"tracks\":["
                        + "{\"distance\":" + Long.MAX_VALUE + ",\"time\":1},"
                        + "{\"distance\":1,\"time\":1}"
                        + "]"
                        + "}"
                        + "}"));

        ResponseResult<TrsearchResponse> result = terminalClient.trsearch("tid-1", 1000L, 2000L);

        assertNotNull(result);
        assertEquals(1700, result.getCode());
        assertEquals("Downstream service returned an invalid response", result.getMessage());
        assertEquals(null, result.getData());
    }

    @Test
    @DisplayName("Amap track search rejects duration aggregation overflow")
    void shouldReturnDownstreamResponseError_whenTrackTimeAggregationOverflows() {
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{"
                        + "\"errcode\":10000,"
                        + "\"errmsg\":\"OK\","
                        + "\"data\":{"
                        + "\"counts\":2,"
                        + "\"tracks\":["
                        + "{\"distance\":1,\"time\":" + Long.MAX_VALUE + "},"
                        + "{\"distance\":1,\"time\":1}"
                        + "]"
                        + "}"
                        + "}"));

        ResponseResult<TrsearchResponse> result = terminalClient.trsearch("tid-1", 1000L, 2000L);

        assertNotNull(result);
        assertEquals(1700, result.getCode());
        assertEquals("Downstream service returned an invalid response", result.getMessage());
        assertEquals(null, result.getData());
    }
}
