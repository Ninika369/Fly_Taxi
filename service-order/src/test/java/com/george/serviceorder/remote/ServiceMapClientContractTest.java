package com.george.serviceorder.remote;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.TrsearchResponse;
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
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ServiceMapClientContractTest {

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
    @DisplayName("ServiceMapClient sends POST query parameters and decodes trace response")
    void shouldSendPostQueryParametersAndDecodeResponse_whenSearchingTrack() {
        wireMockServer.stubFor(post(urlPathEqualTo("/terminal/trsearch"))
                .withQueryParam("tid", equalTo("tid-300"))
                .withQueryParam("starttime", equalTo("1784066400000"))
                .withQueryParam("endtime", equalTo("1784067000000"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"code\":1,\"message\":\"success\","
                                + "\"data\":{\"driveMile\":5000,\"driveTime\":600}}")));

        ServiceMapClient serviceMapClient = feignClient();

        ResponseResult<TrsearchResponse> response =
                serviceMapClient.trsearch("tid-300", 1784066400000L, 1784067000000L);

        assertNotNull(response);
        assertEquals(1, response.getCode());
        assertEquals("success", response.getMessage());
        assertNotNull(response.getData());
        assertEquals(Long.valueOf(5000L), response.getData().getDriveMile());
        assertEquals(Long.valueOf(600L), response.getData().getDriveTime());
        wireMockServer.verify(1, postRequestedFor(urlPathEqualTo("/terminal/trsearch"))
                .withQueryParam("tid", equalTo("tid-300"))
                .withQueryParam("starttime", equalTo("1784066400000"))
                .withQueryParam("endtime", equalTo("1784067000000")));
    }

    private ServiceMapClient feignClient() {
        ObjectFactory<HttpMessageConverters> messageConverters =
                () -> new HttpMessageConverters(new MappingJackson2HttpMessageConverter());

        return Feign.builder()
                .contract(new SpringMvcContract())
                .encoder(new SpringEncoder(messageConverters))
                .decoder(new ResponseEntityDecoder(new SpringDecoder(messageConverters)))
                .target(ServiceMapClient.class, wireMockServer.baseUrl());
    }
}
