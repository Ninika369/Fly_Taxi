package com.george.serviceorder.remote;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.george.internalCommon.dto.PriceRule;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.PriceRuleIsNewRequest;
import feign.Feign;
import feign.MethodMetadata;
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
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicePriceClientContractTest {

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
    @DisplayName("ServicePriceClient sends POST JSON and decodes the response")
    void shouldSendPostJsonAndDecodeResponse_whenCheckingLatestPriceRule() {
        wireMockServer.stubFor(post(urlEqualTo("/price-rule/is-latest"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"code\":1,\"message\":\"success\",\"data\":true}")));

        ServicePriceClient servicePriceClient = feignClient();
        PriceRuleIsNewRequest request = new PriceRuleIsNewRequest();
        request.setFareType("110000$1");
        request.setFareVersion(3);

        ResponseResult<Boolean> response = servicePriceClient.isLatest(request);

        assertNotNull(response);
        assertEquals(1, response.getCode());
        assertEquals("success", response.getMessage());
        assertTrue(response.getData());
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/price-rule/is-latest"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.APPLICATION_JSON_VALUE))
                .withRequestBody(equalToJson("{\"fareType\":\"110000$1\",\"fareVersion\":3}")));
    }

    @Test
    @DisplayName("ServicePriceClient sends POST JSON and decodes price rule existence response")
    void shouldSendPostJsonAndDecodeResponse_whenCheckingPriceRuleExists() {
        wireMockServer.stubFor(post(urlEqualTo("/price-rule/if-exists"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"code\":1,\"message\":\"success\",\"data\":true}")));

        ServicePriceClient servicePriceClient = feignClient();
        PriceRule priceRule = new PriceRule();
        priceRule.setCityCode("110000");
        priceRule.setVehicleType("1");
        priceRule.setStartFare(10.0);
        priceRule.setStartMile(3);
        priceRule.setUnitPricePerMile(2.0);
        priceRule.setUnitPricePerMinute(0.5);
        priceRule.setFareType("110000$1");
        priceRule.setFareVersion(3);

        ResponseResult<Boolean> response = servicePriceClient.ifPriceExists(priceRule);

        assertNotNull(response);
        assertEquals(1, response.getCode());
        assertEquals("success", response.getMessage());
        assertTrue(response.getData());
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/price-rule/if-exists"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing(MediaType.APPLICATION_JSON_VALUE))
                .withRequestBody(equalToJson("{\"cityCode\":\"110000\",\"vehicleType\":\"1\","
                        + "\"startFare\":10.0,\"startMile\":3,\"unitPricePerMile\":2.0,"
                        + "\"unitPricePerMinute\":0.5,\"fareType\":\"110000$1\",\"fareVersion\":3}")));
    }

    @Test
    @DisplayName("ServicePriceClient declares POST for price rule existence checks")
    void shouldDeclarePostMethod_whenCheckingPriceRuleExists() {
        MethodMetadata metadata = new SpringMvcContract()
                .parseAndValidateMetadata(ServicePriceClient.class)
                .stream()
                .filter(methodMetadata -> methodMetadata.configKey().contains("ifPriceExists"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ifPriceExists metadata not found"));

        assertEquals("POST", metadata.template().method());
        assertEquals("/price-rule/if-exists", metadata.template().url());
    }

    private ServicePriceClient feignClient() {
        ObjectFactory<HttpMessageConverters> messageConverters =
                () -> new HttpMessageConverters(new MappingJackson2HttpMessageConverter());

        return Feign.builder()
                .contract(new SpringMvcContract())
                .encoder(new SpringEncoder(messageConverters))
                .decoder(new ResponseEntityDecoder(new SpringDecoder(messageConverters)))
                .target(ServicePriceClient.class, wireMockServer.baseUrl());
    }
}
