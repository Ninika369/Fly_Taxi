package com.george.serviceorder.remote;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.OrderDriverResponse;
import feign.Feign;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ServiceDriverUserClientContractTest {

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("ServiceDriverUserClient sends GET path variable and decodes driver response")
    void shouldSendGetPathVariableAndDecodeResponse_whenGettingAvailableDriver() {
        wireMockServer.stubFor(get(urlEqualTo("/get-available-driver/300"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"code\":1,\"message\":\"success\","
                                + "\"data\":{\"driverId\":200,\"driverPhone\":\"15500000000\","
                                + "\"carId\":300,\"licenseId\":\"NZL-001\","
                                + "\"vehicleNo\":\"ABC123\",\"vehicleType\":\"SUV\"}}")));

        ServiceDriverUserClient serviceDriverUserClient = feignClient();

        ResponseResult<OrderDriverResponse> response = serviceDriverUserClient.getAvailableDriver(300L);

        assertNotNull(response);
        assertEquals(1, response.getCode());
        assertEquals("success", response.getMessage());
        assertNotNull(response.getData());
        assertEquals(Long.valueOf(200L), response.getData().getDriverId());
        assertEquals("15500000000", response.getData().getDriverPhone());
        assertEquals(Long.valueOf(300L), response.getData().getCarId());
        assertEquals("NZL-001", response.getData().getLicenseId());
        assertEquals("ABC123", response.getData().getVehicleNo());
        assertEquals("SUV", response.getData().getVehicleType());
        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/get-available-driver/300")));
    }

    private ServiceDriverUserClient feignClient() {
        ObjectFactory<HttpMessageConverters> messageConverters =
                () -> new HttpMessageConverters(new MappingJackson2HttpMessageConverter());

        return Feign.builder()
                .contract(new SpringMvcContract())
                .encoder(new SpringEncoder(messageConverters))
                .decoder(new ResponseEntityDecoder(new SpringDecoder(messageConverters)))
                .target(ServiceDriverUserClient.class, wireMockServer.baseUrl());
    }
}
