package com.george.servicemap.remote;

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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MapServiceClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private MapServiceClient mapServiceClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mapServiceClient, "mapKey", "test-key");
    }

    @Test
    @DisplayName("Invalid Amap direction JSON is surfaced as an exception")
    void shouldThrowRuntimeException_whenDirectionResponseCannotBeParsed() {
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{invalid-json"));

        assertThrows(RuntimeException.class, () -> mapServiceClient.direction(
                "-36.8485",
                "174.7633",
                "-36.8519",
                "174.7762"));
    }
}
