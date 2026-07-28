package com.george.serviceorder.remote;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.george.internalCommon.dto.Car;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.TrsearchResponse;
import feign.Feign;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.FeignClientFactoryBean;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalizationFeignClientContractTest {

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
    @DisplayName("Finalization clients keep shared service names and unique context IDs")
    void shouldDeclareSharedServiceNamesAndDedicatedContextIds() {
        assertFeignClient(FinalizationDriverUserClient.class,
                "service-driver-user", "finalizationDriverUserClient");
        assertFeignClient(FinalizationMapClient.class,
                "service-map", "finalizationMapClient");
        assertFeignClient(FinalizationPriceClient.class,
                "service-price", "finalizationPriceClient");
    }

    @Test
    @DisplayName("Finalization client methods match shared client contracts")
    void shouldCopyMethodAnnotationsAndSignaturesFromSharedClients() throws Exception {
        assertMethodContractMatches(
                method(ServiceDriverUserClient.class, "getCarById", Long.class),
                method(FinalizationDriverUserClient.class, "getCarById", Long.class));
        assertMethodContractMatches(
                method(ServiceMapClient.class, "trsearch", String.class, Long.class, Long.class),
                method(FinalizationMapClient.class, "trsearch", String.class, Long.class, Long.class));
        assertMethodContractMatches(
                method(ServicePriceClient.class, "calculatePrice",
                        Integer.class, Integer.class, String.class, String.class),
                method(FinalizationPriceClient.class, "calculatePrice",
                        Integer.class, Integer.class, String.class, String.class));
    }

    @Test
    @DisplayName("FinalizationDriverUserClient sends GET query parameter and decodes car response")
    void shouldSendGetQueryParameterAndDecodeResponse_whenGettingFinalizationCar() {
        wireMockServer.stubFor(get(urlPathEqualTo("/car"))
                .withQueryParam("carId", equalTo("300"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"code\":1,\"message\":\"success\","
                                + "\"data\":{\"id\":300,\"brand\":\"Toyota\","
                                + "\"model\":\"Prius\",\"vehicleColor\":\"White\","
                                + "\"tid\":\"tid-300\"}}")));

        FinalizationDriverUserClient client = feignClient(FinalizationDriverUserClient.class);

        ResponseResult<Car> response = client.getCarById(300L);

        assertNotNull(response);
        assertEquals(1, response.getCode());
        assertEquals("success", response.getMessage());
        assertNotNull(response.getData());
        assertEquals(Long.valueOf(300L), response.getData().getId());
        assertEquals("tid-300", response.getData().getTid());
        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/car"))
                .withQueryParam("carId", equalTo("300")));
    }

    @Test
    @DisplayName("FinalizationMapClient sends POST query parameters and decodes trace response")
    void shouldSendPostQueryParametersAndDecodeResponse_whenSearchingFinalizationTrace() {
        wireMockServer.stubFor(post(urlPathEqualTo("/terminal/trsearch"))
                .withQueryParam("tid", equalTo("tid-300"))
                .withQueryParam("starttime", equalTo("1784066400000"))
                .withQueryParam("endtime", equalTo("1784067000000"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"code\":1,\"message\":\"success\","
                                + "\"data\":{\"driveMile\":5000,\"driveTime\":600}}")));

        FinalizationMapClient client = feignClient(FinalizationMapClient.class);

        ResponseResult<TrsearchResponse> response =
                client.trsearch("tid-300", 1784066400000L, 1784067000000L);

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

    @Test
    @DisplayName("FinalizationPriceClient sends POST query parameters and decodes price response")
    void shouldSendPostQueryParametersAndDecodeResponse_whenCalculatingFinalizationPrice() {
        wireMockServer.stubFor(post(urlPathEqualTo("/calculate-price"))
                .withQueryParam("distance", equalTo("5000"))
                .withQueryParam("duration", equalTo("600"))
                .withQueryParam("cityCode", equalTo("110000"))
                .withQueryParam("vehicleType", equalTo("1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"code\":1,\"message\":\"success\",\"data\":19.0}")));

        FinalizationPriceClient client = feignClient(FinalizationPriceClient.class);

        ResponseResult<Double> response = client.calculatePrice(5000, 600, "110000", "1");

        assertNotNull(response);
        assertEquals(1, response.getCode());
        assertEquals("success", response.getMessage());
        assertEquals(19.0, response.getData());
        wireMockServer.verify(1, postRequestedFor(urlPathEqualTo("/calculate-price"))
                .withQueryParam("distance", equalTo("5000"))
                .withQueryParam("duration", equalTo("600"))
                .withQueryParam("cityCode", equalTo("110000"))
                .withQueryParam("vehicleType", equalTo("1")));
    }

    @Test
    @DisplayName("Finalization timeout configuration uses dedicated context IDs only")
    void shouldConfigureTimeoutsOnlyForDedicatedFinalizationContextIds() {
        Properties properties = applicationProperties();

        assertEquals("${ORDER_FINALIZATION_DRIVER_CONNECT_TIMEOUT_MS:2000}",
                properties.getProperty("feign.client.config.finalizationDriverUserClient.connectTimeout"));
        assertEquals("${ORDER_FINALIZATION_DRIVER_READ_TIMEOUT_MS:10000}",
                properties.getProperty("feign.client.config.finalizationDriverUserClient.readTimeout"));
        assertEquals("${ORDER_FINALIZATION_MAP_CONNECT_TIMEOUT_MS:2000}",
                properties.getProperty("feign.client.config.finalizationMapClient.connectTimeout"));
        assertEquals("${ORDER_FINALIZATION_MAP_READ_TIMEOUT_MS:30000}",
                properties.getProperty("feign.client.config.finalizationMapClient.readTimeout"));
        assertEquals("${ORDER_FINALIZATION_PRICE_CONNECT_TIMEOUT_MS:2000}",
                properties.getProperty("feign.client.config.finalizationPriceClient.connectTimeout"));
        assertEquals("${ORDER_FINALIZATION_PRICE_READ_TIMEOUT_MS:10000}",
                properties.getProperty("feign.client.config.finalizationPriceClient.readTimeout"));

        assertFalse(properties.containsKey("feign.client.config.service-driver-user.connectTimeout"));
        assertFalse(properties.containsKey("feign.client.config.service-driver-user.readTimeout"));
        assertFalse(properties.containsKey("feign.client.config.service-map.connectTimeout"));
        assertFalse(properties.containsKey("feign.client.config.service-map.readTimeout"));
        assertFalse(properties.containsKey("feign.client.config.service-price.connectTimeout"));
        assertFalse(properties.containsKey("feign.client.config.service-price.readTimeout"));
    }

    @Test
    @DisplayName("Spring Cloud OpenFeign property model exposes context-id keyed configuration shape")
    void shouldExposeContextIdKeyedFeignConfigurationShape() throws Exception {
        assertNotNull(FeignClientFactoryBean.class.getDeclaredField("contextId"));
        FeignClientProperties properties = new FeignClientProperties();
        FeignClientProperties.FeignClientConfiguration driver =
                new FeignClientProperties.FeignClientConfiguration();
        driver.setConnectTimeout(2000);
        driver.setReadTimeout(10000);
        FeignClientProperties.FeignClientConfiguration map =
                new FeignClientProperties.FeignClientConfiguration();
        map.setConnectTimeout(2000);
        map.setReadTimeout(30000);
        FeignClientProperties.FeignClientConfiguration price =
                new FeignClientProperties.FeignClientConfiguration();
        price.setConnectTimeout(2000);
        price.setReadTimeout(10000);
        properties.setConfig(new java.util.HashMap<>());
        properties.getConfig().put("finalizationDriverUserClient", driver);
        properties.getConfig().put("finalizationMapClient", map);
        properties.getConfig().put("finalizationPriceClient", price);

        Map<String, FeignClientProperties.FeignClientConfiguration> config = properties.getConfig();

        assertEquals(Integer.valueOf(2000), config.get("finalizationDriverUserClient").getConnectTimeout());
        assertEquals(Integer.valueOf(10000), config.get("finalizationDriverUserClient").getReadTimeout());
        assertEquals(Integer.valueOf(30000), config.get("finalizationMapClient").getReadTimeout());
        assertEquals(Integer.valueOf(10000), config.get("finalizationPriceClient").getReadTimeout());
        assertFalse(config.containsKey("service-driver-user"));
        assertFalse(config.containsKey("service-map"));
        assertFalse(config.containsKey("service-price"));
    }

    private void assertFeignClient(Class<?> type, String expectedName, String expectedContextId) {
        FeignClient feignClient = type.getAnnotation(FeignClient.class);
        assertNotNull(feignClient);
        assertEquals(expectedName, clientName(feignClient));
        assertEquals(expectedContextId, feignClient.contextId());
    }

    private String clientName(FeignClient feignClient) {
        if (!feignClient.name().isEmpty()) {
            return feignClient.name();
        }
        return feignClient.value();
    }

    private void assertMethodContractMatches(Method sharedMethod, Method finalizationMethod) {
        assertEquals(sharedMethod.getGenericReturnType(), finalizationMethod.getGenericReturnType());
        assertEquals(Arrays.asList(sharedMethod.getParameterTypes()),
                Arrays.asList(finalizationMethod.getParameterTypes()));
        assertMappingAnnotationsMatch(sharedMethod, finalizationMethod);
        assertEquals(sharedMethod.getParameterAnnotations().length,
                finalizationMethod.getParameterAnnotations().length);
        for (int i = 0; i < sharedMethod.getParameterAnnotations().length; i++) {
            assertParameterAnnotationsMatch(sharedMethod.getParameterAnnotations()[i],
                    finalizationMethod.getParameterAnnotations()[i]);
        }
    }

    private void assertMappingAnnotationsMatch(Method sharedMethod, Method finalizationMethod) {
        GetMapping sharedGet = sharedMethod.getAnnotation(GetMapping.class);
        GetMapping finalizationGet = finalizationMethod.getAnnotation(GetMapping.class);
        if (sharedGet != null || finalizationGet != null) {
            assertNotNull(sharedGet);
            assertNotNull(finalizationGet);
            assertEquals(sharedGet.name(), finalizationGet.name());
            assertArrayEquals(sharedGet.value(), finalizationGet.value());
            assertArrayEquals(sharedGet.path(), finalizationGet.path());
            assertArrayEquals(sharedGet.params(), finalizationGet.params());
            assertArrayEquals(sharedGet.headers(), finalizationGet.headers());
            assertArrayEquals(sharedGet.consumes(), finalizationGet.consumes());
            assertArrayEquals(sharedGet.produces(), finalizationGet.produces());
            return;
        }

        RequestMapping sharedRequest = sharedMethod.getAnnotation(RequestMapping.class);
        RequestMapping finalizationRequest = finalizationMethod.getAnnotation(RequestMapping.class);
        assertNotNull(sharedRequest);
        assertNotNull(finalizationRequest);
        assertEquals(sharedRequest.name(), finalizationRequest.name());
        assertArrayEquals(sharedRequest.value(), finalizationRequest.value());
        assertArrayEquals(sharedRequest.path(), finalizationRequest.path());
        assertArrayEquals(sharedRequest.method(), finalizationRequest.method());
        assertArrayEquals(sharedRequest.params(), finalizationRequest.params());
        assertArrayEquals(sharedRequest.headers(), finalizationRequest.headers());
        assertArrayEquals(sharedRequest.consumes(), finalizationRequest.consumes());
        assertArrayEquals(sharedRequest.produces(), finalizationRequest.produces());
    }

    private void assertParameterAnnotationsMatch(Annotation[] sharedAnnotations,
                                                Annotation[] finalizationAnnotations) {
        assertEquals(annotationTypes(sharedAnnotations), annotationTypes(finalizationAnnotations));
        assertRequestParamMatches(
                annotation(sharedAnnotations, RequestParam.class),
                annotation(finalizationAnnotations, RequestParam.class));
        assertRequestBodyMatches(
                annotation(sharedAnnotations, RequestBody.class),
                annotation(finalizationAnnotations, RequestBody.class));
        assertPathVariableMatches(
                annotation(sharedAnnotations, PathVariable.class),
                annotation(finalizationAnnotations, PathVariable.class));
    }

    private void assertRequestParamMatches(RequestParam shared, RequestParam finalization) {
        if (shared == null && finalization == null) {
            return;
        }
        assertNotNull(shared);
        assertNotNull(finalization);
        assertEquals(shared.value(), finalization.value());
        assertEquals(shared.name(), finalization.name());
        assertEquals(shared.required(), finalization.required());
        assertEquals(shared.defaultValue(), finalization.defaultValue());
    }

    private void assertRequestBodyMatches(RequestBody shared, RequestBody finalization) {
        if (shared == null && finalization == null) {
            return;
        }
        assertNotNull(shared);
        assertNotNull(finalization);
        assertEquals(shared.required(), finalization.required());
    }

    private void assertPathVariableMatches(PathVariable shared, PathVariable finalization) {
        if (shared == null && finalization == null) {
            return;
        }
        assertNotNull(shared);
        assertNotNull(finalization);
        assertEquals(shared.value(), finalization.value());
        assertEquals(shared.name(), finalization.name());
        assertEquals(shared.required(), finalization.required());
    }

    private java.util.List<Class<? extends Annotation>> annotationTypes(Annotation[] annotations) {
        java.util.List<Class<? extends Annotation>> types = new java.util.ArrayList<>();
        for (Annotation annotation : annotations) {
            types.add(annotation.annotationType());
        }
        return types;
    }

    private <T extends Annotation> T annotation(Annotation[] annotations, Class<T> type) {
        for (Annotation annotation : annotations) {
            if (type.isInstance(annotation)) {
                return type.cast(annotation);
            }
        }
        return null;
    }

    private Method method(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return type.getDeclaredMethod(name, parameterTypes);
    }

    private <T> T feignClient(Class<T> type) {
        ObjectFactory<HttpMessageConverters> messageConverters =
                () -> new HttpMessageConverters(new MappingJackson2HttpMessageConverter());

        return Feign.builder()
                .contract(new SpringMvcContract())
                .encoder(new SpringEncoder(messageConverters))
                .decoder(new ResponseEntityDecoder(new SpringDecoder(messageConverters)))
                .target(type, wireMockServer.baseUrl());
    }

    private Properties applicationProperties() {
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(new FileSystemResource("src/main/resources/application.yml"));
        Properties properties = factoryBean.getObject();
        assertNotNull(properties);
        return properties;
    }
}
