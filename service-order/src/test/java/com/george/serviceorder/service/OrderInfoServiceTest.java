package com.george.serviceorder.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.constant.OrderConstant;
import com.george.internalCommon.constant.UserIdentity;
import com.george.internalCommon.dto.Car;
import com.george.internalCommon.dto.OrderInfo;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.request.OrderRequest;
import com.george.internalCommon.response.OrderDriverResponse;
import com.george.internalCommon.response.TerminalResponse;
import com.george.internalCommon.response.TrsearchResponse;
import com.george.serviceorder.mapper.OrderInfoMapper;
import com.george.serviceorder.remote.ServiceDriverUserClient;
import com.george.serviceorder.remote.ServiceMapClient;
import com.george.serviceorder.remote.ServicePriceClient;
import com.george.serviceorder.remote.ServiceSsePushClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

// ---- Mockito imports ----
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;    // Tells Mockito: "create this object and inject mocks into it"
import org.mockito.Mock;            // Tells Mockito: "create a fake version of this"
import org.mockito.junit.jupiter.MockitoExtension;  // Activates Mockito for JUnit 5
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;   // when(), verify(), any(), etc.

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link OrderInfoService#cancel(Long, String)}.
 *
 * cancel() depends on the database (orderInfoMapper), so we use Mockito
 * to create a fake mapper that returns whatever data we prepare.
 *
 * KEY CONCEPT — the 3 Mockito annotations:
 *   @Mock           = "fake this dependency"
 *   @InjectMocks    = "create the real service, but plug in the fakes"
 *   @ExtendWith     = "turn on Mockito before each test"
 */
@ExtendWith(MockitoExtension.class)
class OrderInfoServiceTest {

    // The fake mapper — when our code calls orderInfoMapper.selectById(),
    // it won't hit a real database. Instead, it returns whatever we tell it to.
    @Mock
    OrderInfoMapper orderInfoMapper;

    @Mock
    ServiceMapClient serviceMapClient;

    @Mock
    ServiceDriverUserClient serviceDriverUserClient;

    @Mock
    ServiceSsePushClient serviceSsePushClient;

    @Mock
    ServicePriceClient servicePriceClient;

    @Mock
    StringRedisTemplate stringRedisTemplate;

    @Mock
    ValueOperations<String, String> valueOperations;

    @Mock
    RedissonClient redissonClient;

    @Mock
    RLock lock;

    // The REAL service — but with the fake mapper injected into it.
    // Mockito sees the @Autowired OrderInfoMapper field inside OrderInfoService
    // and plugs in our @Mock automatically.
    @InjectMocks
    OrderInfoService orderInfoService;

    // Reusable test data
    private static final Long ORDER_ID = 100L;
    private static final String PASSENGER = UserIdentity.PASSENGER.getIdentity(); // "1"
    private static final String DRIVER = UserIdentity.DRIVER.getIdentity();       // "2"
    private static final Instant FIXED_TRACE_END = Instant.parse("2026-07-28T01:02:03Z");
    private static final ZoneId TEST_ZONE = ZoneId.of("Pacific/Auckland");

    private OrderInfo orderInfo;

    @BeforeEach
    void setUp() {
        orderInfo = new OrderInfo();
        orderInfo.setId(ORDER_ID);
    }

    // ---- Helper: tell the fake mapper to return our prepared orderInfo ----
    private void givenOrderWithStatus(int status) {
        orderInfo.setOrderStatus(status);
        // This is the core Mockito syntax:
        // "when someone calls selectById(ORDER_ID), return our orderInfo"
        when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(orderInfo);
    }

    private void givenOrderAcceptedMinutesAgo(int minutesAgo) {
        orderInfo.setOrderStatus(OrderConstant.DRIVER_RECEIVE_ORDER);
        orderInfo.setReceiveOrderTime(LocalDateTime.now().minusMinutes(minutesAgo));
        when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(orderInfo);
    }

    private OrderInfo dispatchOrder() {
        OrderInfo dispatchOrder = new OrderInfo();
        dispatchOrder.setId(ORDER_ID);
        dispatchOrder.setPassengerId(10L);
        dispatchOrder.setPassengerPhone("13300000000");
        dispatchOrder.setDeparture("A");
        dispatchOrder.setDepLongitude("174.7633");
        dispatchOrder.setDepLatitude("-36.8485");
        dispatchOrder.setDestination("B");
        dispatchOrder.setDestLongitude("174.7762");
        dispatchOrder.setDestLatitude("-36.8519");
        dispatchOrder.setVehicleType("SUV");
        return dispatchOrder;
    }

    private TerminalResponse terminal(Long carId) {
        TerminalResponse terminalResponse = new TerminalResponse();
        terminalResponse.setCarId(carId);
        terminalResponse.setLongitude("174.7700");
        terminalResponse.setLatitude("-36.8500");
        return terminalResponse;
    }

    private OrderDriverResponse driver(Long driverId, String vehicleType) {
        OrderDriverResponse driverResponse = new OrderDriverResponse();
        driverResponse.setDriverId(driverId);
        driverResponse.setDriverPhone("15500000000");
        driverResponse.setLicenseId("NZL-001");
        driverResponse.setVehicleNo("ABC123");
        driverResponse.setVehicleType(vehicleType);
        return driverResponse;
    }

    private ResponseResult<List<TerminalResponse>> terminalSearchResult(List<TerminalResponse> terminals) {
        return ResponseResult.success(terminals);
    }

    private OrderRequest newOrderRequest() {
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setAddress("110000");
        orderRequest.setFareType("110000$1");
        orderRequest.setFareVersion(3);
        orderRequest.setPassengerId(10L);
        orderRequest.setDeviceCode("device-10");
        return orderRequest;
    }

    private OrderRequest getoffRequest() {
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setOrderId(ORDER_ID);
        orderRequest.setPassengerGetoffLongitude("174.7762");
        orderRequest.setPassengerGetoffLatitude("-36.8519");
        return orderRequest;
    }

    private void givenFinalizableOrder(Long carId) {
        orderInfo.setOrderStatus(OrderConstant.PICK_UP_PASSENGER);
        orderInfo.setCarId(carId);
        orderInfo.setPickUpPassengerTime(LocalDateTime.of(2026, 7, 28, 12, 0));
        orderInfo.setAddress("110000");
        orderInfo.setVehicleType("1");
        when(orderInfoMapper.selectOne(any())).thenReturn(orderInfo);
    }

    private void givenPendingFinalizationOrder(Long carId, int attempts, LocalDateTime nextRetryAt) {
        givenFinalizableOrder(carId);
        orderInfo.setOrderStatus(OrderConstant.FINALIZATION_PENDING);
        orderInfo.setFinalizationAttempts(attempts);
        orderInfo.setFinalizationNextRetryAt(nextRetryAt);
        orderInfo.setFinalizationTraceEndEpochMs(FIXED_TRACE_END.toEpochMilli());
    }

    private void givenFinalizationClock() {
        ReflectionTestUtils.setField(orderInfoService, "clock", Clock.fixed(FIXED_TRACE_END, TEST_ZONE));
    }

    private Clock fixedClock(String instant) {
        return Clock.fixed(Instant.parse(instant), TEST_ZONE);
    }

    private LocalDateTime localTime(Clock clock) {
        return LocalDateTime.now(clock);
    }

    private void setFinalizationClock(Clock clock) {
        ReflectionTestUtils.setField(orderInfoService, "clock", clock);
    }

    private void givenFinalizationClaimSucceeds() {
        when(orderInfoMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
    }

    private void givenFinalizationClaimIsLost() {
        when(orderInfoMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);
    }

    private void givenCarLookupSucceeds(Long carId, String tid) {
        Car car = new Car();
        car.setTid(tid);
        when(serviceDriverUserClient.getCarById(carId)).thenReturn(ResponseResult.success(car));
    }

    private void givenTrackLookupSucceeds(String tid, long driveMile, long driveDurationSeconds) {
        TrsearchResponse trsearchResponse = new TrsearchResponse();
        trsearchResponse.setDriveMile(driveMile);
        trsearchResponse.setDriveTime(driveDurationSeconds);
        when(serviceMapClient.trsearch(eq(tid), anyLong(), anyLong()))
                .thenReturn(ResponseResult.success(trsearchResponse));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<UpdateWrapper<OrderInfo>> captureFinalizationUpdates(int expectedCalls) {
        ArgumentCaptor<UpdateWrapper> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(orderInfoMapper, times(expectedCalls)).update(isNull(), captor.capture());
        return (List) captor.getAllValues();
    }

    private UpdateWrapper<OrderInfo> captureTerminalFinalizationUpdate() {
        return captureFinalizationUpdates(2).get(1);
    }

    private void assertTerminalCas(UpdateWrapper<OrderInfo> updateWrapper, int attempt) {
        String where = updateWrapper.getSqlSegment();
        assertTrue(where.contains("id"), where);
        assertTrue(where.contains("order_status"), where);
        assertTrue(where.contains("finalization_attempts"), where);
        assertTrue(updateWrapper.getParamNameValuePairs().containsValue(ORDER_ID));
        assertTrue(updateWrapper.getParamNameValuePairs().containsValue(OrderConstant.FINALIZATION_PENDING));
        assertTrue(updateWrapper.getParamNameValuePairs().containsValue(attempt));
    }

    private void assertSqlSetContains(UpdateWrapper<OrderInfo> updateWrapper, String columnName) {
        String sqlSet = updateWrapper.getSqlSet();
        assertTrue(sqlSet.contains(columnName), sqlSet);
    }

    private void assertSqlSetDoesNotContain(UpdateWrapper<OrderInfo> updateWrapper, String columnName) {
        String sqlSet = updateWrapper.getSqlSet();
        assertFalse(sqlSet.contains(columnName), sqlSet);
    }

    private void assertWrapperContainsValue(UpdateWrapper<OrderInfo> updateWrapper, Object expectedValue) {
        assertTrue(updateWrapper.getParamNameValuePairs().containsValue(expectedValue),
                "Expected wrapper to contain value " + expectedValue
                        + " but found " + updateWrapper.getParamNameValuePairs());
    }

    // ======================== Order creation preflight failures ========================

    @Test
    @DisplayName("Order creation preserves price-rule existence failure before insert")
    void shouldPreservePriceRuleExistenceFailure_beforeCreatingOrder() {
        OrderRequest orderRequest = newOrderRequest();
        when(servicePriceClient.ifPriceExists(any()))
                .thenReturn(ResponseResult.fail(1881, "price-rule lookup failed"));

        ResponseResult result = orderInfoService.add(orderRequest);

        assertEquals(1881, result.getCode());
        assertEquals("price-rule lookup failed", result.getMessage());
        verify(orderInfoMapper, never()).insert(any(OrderInfo.class));
        verify(serviceDriverUserClient, never()).isAvailableDriver(anyString());
        verify(servicePriceClient, never()).isLatest(any());
    }

    @Test
    @DisplayName("Order creation preserves driver availability failure before insert")
    void shouldPreserveDriverAvailabilityFailure_beforeCreatingOrder() {
        OrderRequest orderRequest = newOrderRequest();
        when(servicePriceClient.ifPriceExists(any())).thenReturn(ResponseResult.success(true));
        when(serviceDriverUserClient.isAvailableDriver("110000"))
                .thenReturn(ResponseResult.fail(1882, "driver availability failed"));

        ResponseResult result = orderInfoService.add(orderRequest);

        assertEquals(1882, result.getCode());
        assertEquals("driver availability failed", result.getMessage());
        verify(orderInfoMapper, never()).insert(any(OrderInfo.class));
        verify(servicePriceClient, never()).isLatest(any());
    }

    @Test
    @DisplayName("Order creation preserves price-rule version failure before insert")
    void shouldPreservePriceRuleVersionFailure_beforeCreatingOrder() {
        OrderRequest orderRequest = newOrderRequest();
        when(servicePriceClient.ifPriceExists(any())).thenReturn(ResponseResult.success(true));
        when(serviceDriverUserClient.isAvailableDriver("110000")).thenReturn(ResponseResult.success(true));
        when(servicePriceClient.isLatest(any()))
                .thenReturn(ResponseResult.fail(1883, "price-rule version check failed"));

        ResponseResult result = orderInfoService.add(orderRequest);

        assertEquals(1883, result.getCode());
        assertEquals("price-rule version check failed", result.getMessage());
        verify(orderInfoMapper, never()).insert(any(OrderInfo.class));
    }

    // ======================== Passenger get-off pricing ========================

    @Test
    @DisplayName("Passenger get-off forwards ride duration in seconds to pricing")
    void shouldForwardDriveDurationInSeconds_whenPassengerGetsOff() {
        Long carId = 300L;
        OrderRequest orderRequest = getoffRequest();
        givenFinalizationClock();
        givenFinalizableOrder(carId);
        givenFinalizationClaimSucceeds();
        givenCarLookupSucceeds(carId, "tid-300");
        givenTrackLookupSucceeds("tid-300", 5000L, 600L);
        when(servicePriceClient.calculatePrice(5000, 600, "110000", "1"))
                .thenReturn(ResponseResult.success(19.00));

        ResponseResult result = orderInfoService.passengerGetoff(orderRequest);

        assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
        verify(servicePriceClient, times(1)).calculatePrice(5000, 600, "110000", "1");
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        UpdateWrapper<OrderInfo> successUpdate = captureTerminalFinalizationUpdate();
        assertTerminalCas(successUpdate, 1);
        assertSqlSetContains(successUpdate, "order_status");
        assertSqlSetContains(successUpdate, "drive_mile");
        assertSqlSetContains(successUpdate, "drive_time");
        assertSqlSetContains(successUpdate, "price");
        assertSqlSetContains(successUpdate, "finalization_next_retry_at");
        assertSqlSetContains(successUpdate, "finalization_last_error");
        assertSqlSetContains(successUpdate, "gmt_modified");
        assertSqlSetDoesNotContain(successUpdate, "finalization_attempts");
        assertTrue(successUpdate.getParamNameValuePairs().containsValue(OrderConstant.PASSENGER_GETOFF));
        assertTrue(successUpdate.getParamNameValuePairs().containsValue(600L));
        assertTrue(successUpdate.getParamNameValuePairs().containsValue(19.00));
    }

    @Test
    @DisplayName("Passenger get-off uses JVM default zone for pickup and current UTC instant for trace end")
    void shouldBuildTraceSearchWindowFromSystemDefaultZone_whenPassengerGetsOff() {
        TimeZone originalTimeZone = TimeZone.getDefault();
        ZoneId testZone = ZoneId.of("Pacific/Auckland");
        TimeZone.setDefault(TimeZone.getTimeZone(testZone));
        try {
            Long carId = 300L;
            LocalDateTime pickUpLocalTime = LocalDateTime.of(2026, 7, 15, 10, 0);
            long expectedStartTime = pickUpLocalTime
                    .atZone(testZone)
                    .toInstant()
                    .toEpochMilli();
            OrderRequest orderRequest = getoffRequest();
            givenFinalizationClock();
            givenFinalizableOrder(carId);
            orderInfo.setPickUpPassengerTime(pickUpLocalTime);
            givenFinalizationClaimSucceeds();
            givenCarLookupSucceeds(carId, "tid-300");
            givenTrackLookupSucceeds("tid-300", 5000L, 600L);
            when(servicePriceClient.calculatePrice(5000, 600, "110000", "1"))
                    .thenReturn(ResponseResult.success(19.00));

            orderInfoService.passengerGetoff(orderRequest);

            ArgumentCaptor<Long> starttimeCaptor = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<Long> endtimeCaptor = ArgumentCaptor.forClass(Long.class);
            verify(serviceMapClient, times(1)).trsearch(eq("tid-300"), starttimeCaptor.capture(), endtimeCaptor.capture());
            Long actualStartTime = starttimeCaptor.getValue();
            Long actualEndTime = endtimeCaptor.getValue();

            assertAll(
                    () -> assertEquals(expectedStartTime, actualStartTime,
                            "starttime should use the JVM default zone for the stored pickup wall time"),
                    () -> assertEquals(FIXED_TRACE_END.toEpochMilli(), actualEndTime,
                            "endtime should be fixed at the first accepted get-off instant"),
                    () -> assertTrue(actualEndTime > actualStartTime,
                            "endtime should be after starttime; starttime=" + actualStartTime
                                    + ", endtime=" + actualEndTime)
            );
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    @DisplayName("Passenger get-off preserves empty-track failure without pricing")
    void shouldPreserveTrackEmptyFailure_whenPassengerGetsOff() {
        Long carId = 300L;
        OrderRequest orderRequest = getoffRequest();
        givenFinalizationClock();
        givenFinalizableOrder(carId);
        givenFinalizationClaimSucceeds();
        givenCarLookupSucceeds(carId, "tid-300");
        when(serviceMapClient.trsearch(eq("tid-300"), anyLong(), anyLong()))
                .thenReturn(ResponseResult.fail(1402, "No track data is available for the requested interval"));

        ResponseResult result = orderInfoService.passengerGetoff(orderRequest);

        assertEquals(1402, result.getCode());
        assertEquals("No track data is available for the requested interval", result.getMessage());
        verify(servicePriceClient, never()).calculatePrice(anyInt(), anyInt(), anyString(), anyString());
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        UpdateWrapper<OrderInfo> pendingUpdate = captureTerminalFinalizationUpdate();
        assertTerminalCas(pendingUpdate, 1);
        assertSqlSetContains(pendingUpdate, "order_status");
        assertSqlSetContains(pendingUpdate, "finalization_next_retry_at");
        assertSqlSetContains(pendingUpdate, "finalization_last_error");
        assertSqlSetContains(pendingUpdate, "gmt_modified");
        assertSqlSetDoesNotContain(pendingUpdate, "price");
        assertSqlSetDoesNotContain(pendingUpdate, "finalization_attempts");
        assertTrue(pendingUpdate.getParamNameValuePairs().containsValue(OrderConstant.FINALIZATION_PENDING));
        assertTrue(pendingUpdate.getParamNameValuePairs().containsValue(
                "1402:No track data is available for the requested interval"));
    }

    @Test
    @DisplayName("Passenger get-off rejects successful track search without data")
    void shouldReturnDownstreamResponseError_whenTrackSearchSucceedsWithoutData() {
        Long carId = 300L;
        OrderRequest orderRequest = getoffRequest();
        givenFinalizationClock();
        givenFinalizableOrder(carId);
        givenFinalizationClaimSucceeds();
        givenCarLookupSucceeds(carId, "tid-300");
        when(serviceMapClient.trsearch(eq("tid-300"), anyLong(), anyLong()))
                .thenReturn(ResponseResult.success(null));

        ResponseResult result = orderInfoService.passengerGetoff(orderRequest);

        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(), result.getCode());
        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage(), result.getMessage());
        verify(servicePriceClient, never()).calculatePrice(anyInt(), anyInt(), anyString(), anyString());
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        UpdateWrapper<OrderInfo> pendingUpdate = captureTerminalFinalizationUpdate();
        assertTerminalCas(pendingUpdate, 1);
        assertSqlSetContains(pendingUpdate, "finalization_next_retry_at");
        assertSqlSetContains(pendingUpdate, "finalization_last_error");
        assertSqlSetDoesNotContain(pendingUpdate, "price");
        assertTrue(pendingUpdate.getParamNameValuePairs().containsValue(
                "1700:Downstream service returned an invalid response"));
    }

    @Test
    @DisplayName("Passenger get-off returns order-not-found without side effects")
    void shouldReturnOrderNotFound_whenFinalizingMissingOrder() {
        when(orderInfoMapper.selectOne(any())).thenReturn(null);

        ResponseResult result = orderInfoService.passengerGetoff(getoffRequest());

        assertEquals(CommonStatus.ORDER_NOT_FOUND.getCode(), result.getCode());
        assertEquals(CommonStatus.ORDER_NOT_FOUND.getMessage(), result.getMessage());
        verify(orderInfoMapper, never()).update(isNull(), any(UpdateWrapper.class));
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        verifyNoInteractions(serviceDriverUserClient, serviceMapClient, servicePriceClient);
    }

    @Test
    @DisplayName("Passenger get-off is idempotent after finalization")
    void shouldReturnIdempotentSuccess_whenOrderIsAlreadyFinalized() {
        int[] finalizedStatuses = {
                OrderConstant.PASSENGER_GETOFF,
                OrderConstant.TO_START_PAY,
                OrderConstant.SUCCESS_PAY
        };

        for (int finalizedStatus : finalizedStatuses) {
            reset(orderInfoMapper, serviceDriverUserClient, serviceMapClient, servicePriceClient);
            OrderInfo finalizedOrder = new OrderInfo();
            finalizedOrder.setId(ORDER_ID);
            finalizedOrder.setOrderStatus(finalizedStatus);
            when(orderInfoMapper.selectOne(any())).thenReturn(finalizedOrder);

            ResponseResult result = orderInfoService.passengerGetoff(getoffRequest());

            assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
            verify(orderInfoMapper, never()).update(isNull(), any(UpdateWrapper.class));
            verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
            verifyNoInteractions(serviceDriverUserClient, serviceMapClient, servicePriceClient);
        }
    }

    @Test
    @DisplayName("Passenger get-off rejects orders in states that cannot be finalized")
    void shouldRejectFinalization_whenOrderStateIsNotEligible() {
        orderInfo.setOrderStatus(OrderConstant.DRIVER_RECEIVE_ORDER);
        when(orderInfoMapper.selectOne(any())).thenReturn(orderInfo);

        ResponseResult result = orderInfoService.passengerGetoff(getoffRequest());

        assertEquals(CommonStatus.ORDER_FINALIZATION_NOT_ALLOWED.getCode(), result.getCode());
        assertEquals(CommonStatus.ORDER_FINALIZATION_NOT_ALLOWED.getMessage(), result.getMessage());
        verify(orderInfoMapper, never()).update(isNull(), any(UpdateWrapper.class));
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        verifyNoInteractions(serviceDriverUserClient, serviceMapClient, servicePriceClient);
    }

    @Test
    @DisplayName("Passenger get-off persists pending finalization when car lookup fails")
    void shouldPersistPendingFinalization_whenCarLookupFails() {
        Long carId = 300L;
        givenFinalizationClock();
        givenFinalizableOrder(carId);
        givenFinalizationClaimSucceeds();
        when(serviceDriverUserClient.getCarById(carId))
                .thenReturn(ResponseResult.fail(1501, "Driver does not exist"));

        ResponseResult result = orderInfoService.passengerGetoff(getoffRequest());

        assertEquals(1501, result.getCode());
        assertEquals("Driver does not exist", result.getMessage());
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        UpdateWrapper<OrderInfo> pendingUpdate = captureTerminalFinalizationUpdate();
        assertTerminalCas(pendingUpdate, 1);
        assertSqlSetContains(pendingUpdate, "finalization_next_retry_at");
        assertSqlSetContains(pendingUpdate, "finalization_last_error");
        assertSqlSetDoesNotContain(pendingUpdate, "finalization_attempts");
        assertTrue(pendingUpdate.getParamNameValuePairs().containsValue(OrderConstant.FINALIZATION_PENDING));
        assertTrue(pendingUpdate.getParamNameValuePairs().containsValue("1501:Driver does not exist"));
        verify(serviceMapClient, never()).trsearch(anyString(), anyLong(), anyLong());
        verify(servicePriceClient, never()).calculatePrice(anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("Passenger get-off persists pending finalization when price calculation fails")
    void shouldPersistPendingFinalization_whenPriceCalculationFails() {
        Long carId = 300L;
        givenFinalizationClock();
        givenFinalizableOrder(carId);
        givenFinalizationClaimSucceeds();
        givenCarLookupSucceeds(carId, "tid-300");
        givenTrackLookupSucceeds("tid-300", 5000L, 600L);
        when(servicePriceClient.calculatePrice(5000, 600, "110000", "1"))
                .thenReturn(ResponseResult.fail(1700,
                        "Downstream service returned an invalid response: key=synthetic-secret"));

        ResponseResult result = orderInfoService.passengerGetoff(getoffRequest());

        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(), result.getCode());
        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage(), result.getMessage());
        assertFalse(result.getMessage().contains("synthetic-secret"));
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        UpdateWrapper<OrderInfo> pendingUpdate = captureTerminalFinalizationUpdate();
        assertTerminalCas(pendingUpdate, 1);
        assertSqlSetContains(pendingUpdate, "finalization_next_retry_at");
        assertSqlSetContains(pendingUpdate, "finalization_last_error");
        assertSqlSetDoesNotContain(pendingUpdate, "price");
        assertSqlSetDoesNotContain(pendingUpdate, "finalization_attempts");
        assertTrue(pendingUpdate.getParamNameValuePairs().containsValue(OrderConstant.FINALIZATION_PENDING));
        assertTrue(pendingUpdate.getParamNameValuePairs().containsValue(
                "1700:Downstream service returned an invalid response"));
        assertFalse(pendingUpdate.getParamNameValuePairs().containsValue(
                "Downstream service returned an invalid response: key=synthetic-secret"));
    }

    @Test
    @DisplayName("Passenger get-off schedules first retry thirty seconds after failure completes")
    void shouldScheduleFirstRetryThirtySecondsAfterFailureCompletes() {
        Long carId = 300L;
        Clock attemptClock = fixedClock("2026-07-28T01:02:03Z");
        Clock failureClock = fixedClock("2026-07-28T01:02:13Z");
        setFinalizationClock(attemptClock);
        givenFinalizableOrder(carId);
        givenFinalizationClaimSucceeds();
        when(serviceDriverUserClient.getCarById(carId)).thenAnswer(invocation -> {
            setFinalizationClock(failureClock);
            return ResponseResult.fail(1501, "Driver does not exist");
        });

        ResponseResult result = orderInfoService.passengerGetoff(getoffRequest());

        assertEquals(CommonStatus.DRIVER_NOT_EXISTS.getCode(), result.getCode());
        assertEquals(CommonStatus.DRIVER_NOT_EXISTS.getMessage(), result.getMessage());
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        UpdateWrapper<OrderInfo> pendingUpdate = captureTerminalFinalizationUpdate();
        LocalDateTime attemptTime = localTime(attemptClock);
        LocalDateTime failureTime = localTime(failureClock);
        assertWrapperContainsValue(pendingUpdate, failureTime.plusSeconds(30));
        assertWrapperContainsValue(pendingUpdate, failureTime);
        assertFalse(pendingUpdate.getParamNameValuePairs().containsValue(attemptTime.plusSeconds(30)));
    }

    @Test
    @DisplayName("Passenger get-off schedules second retry sixty seconds after failure completes")
    void shouldScheduleSecondRetrySixtySecondsAfterFailureCompletes() {
        Long carId = 300L;
        Clock attemptClock = fixedClock("2026-07-28T01:02:03Z");
        Clock failureClock = fixedClock("2026-07-28T01:02:23Z");
        setFinalizationClock(attemptClock);
        givenPendingFinalizationOrder(carId, 1, localTime(attemptClock).minusSeconds(1));
        givenFinalizationClaimSucceeds();
        when(serviceDriverUserClient.getCarById(carId)).thenAnswer(invocation -> {
            setFinalizationClock(failureClock);
            return ResponseResult.fail(1501, "Driver does not exist");
        });

        ResponseResult result = orderInfoService.passengerGetoff(getoffRequest());

        assertEquals(CommonStatus.DRIVER_NOT_EXISTS.getCode(), result.getCode());
        assertEquals(CommonStatus.DRIVER_NOT_EXISTS.getMessage(), result.getMessage());
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        UpdateWrapper<OrderInfo> pendingUpdate = captureTerminalFinalizationUpdate();
        LocalDateTime attemptTime = localTime(attemptClock);
        LocalDateTime failureTime = localTime(failureClock);
        assertTerminalCas(pendingUpdate, 2);
        assertWrapperContainsValue(pendingUpdate, failureTime.plusSeconds(60));
        assertWrapperContainsValue(pendingUpdate, failureTime);
        assertFalse(pendingUpdate.getParamNameValuePairs().containsValue(attemptTime.plusSeconds(60)));
    }

    @Test
    @DisplayName("Passenger get-off skips pending finalization that is not due")
    void shouldReturnRetryScheduled_whenPendingRetryIsNotDue() {
        givenFinalizationClock();
        givenPendingFinalizationOrder(300L, 1, LocalDateTime.now(Clock.fixed(FIXED_TRACE_END, TEST_ZONE)).plusMinutes(1));

        ResponseResult result = orderInfoService.passengerGetoff(getoffRequest());

        assertEquals(CommonStatus.FINALIZATION_RETRY_SCHEDULED.getCode(), result.getCode());
        assertEquals(CommonStatus.FINALIZATION_RETRY_SCHEDULED.getMessage(), result.getMessage());
        verify(orderInfoMapper, never()).update(isNull(), any(UpdateWrapper.class));
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        verifyNoInteractions(serviceDriverUserClient, serviceMapClient, servicePriceClient);
    }

    @Test
    @DisplayName("Passenger get-off moves finalization to failed at maximum attempts")
    void shouldMoveToFinalizationFailed_whenMaximumAttemptsAreReached() {
        Long carId = 300L;
        givenFinalizationClock();
        givenPendingFinalizationOrder(carId, 2, LocalDateTime.now(Clock.fixed(FIXED_TRACE_END, TEST_ZONE)).minusSeconds(1));
        givenFinalizationClaimSucceeds();
        when(serviceDriverUserClient.getCarById(carId))
                .thenReturn(ResponseResult.fail(1700, "Downstream service returned an invalid response"));

        ResponseResult result = orderInfoService.passengerGetoff(getoffRequest());

        assertEquals(CommonStatus.FINALIZATION_FAILED.getCode(), result.getCode());
        assertEquals(CommonStatus.FINALIZATION_FAILED.getMessage(), result.getMessage());
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        UpdateWrapper<OrderInfo> failedUpdate = captureTerminalFinalizationUpdate();
        assertTerminalCas(failedUpdate, 3);
        assertSqlSetContains(failedUpdate, "order_status");
        assertSqlSetContains(failedUpdate, "finalization_next_retry_at");
        assertSqlSetContains(failedUpdate, "finalization_last_error");
        assertSqlSetContains(failedUpdate, "gmt_modified");
        assertSqlSetDoesNotContain(failedUpdate, "finalization_attempts");
        assertTrue(failedUpdate.getParamNameValuePairs().containsValue(OrderConstant.FINALIZATION_FAILED));
        assertTrue(failedUpdate.getParamNameValuePairs().containsValue(
                "1700:Downstream service returned an invalid response"));
    }

    @Test
    @DisplayName("Passenger get-off skips remote calls when finalization claim is lost")
    void shouldSkipRemoteCalls_whenFinalizationClaimIsLost() {
        Long carId = 300L;
        givenFinalizationClock();
        givenFinalizableOrder(carId);
        givenFinalizationClaimIsLost();

        ResponseResult result = orderInfoService.passengerGetoff(getoffRequest());

        assertEquals(CommonStatus.FINALIZATION_RETRY_SCHEDULED.getCode(), result.getCode());
        assertEquals(CommonStatus.FINALIZATION_RETRY_SCHEDULED.getMessage(), result.getMessage());
        verify(orderInfoMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        verifyNoInteractions(serviceDriverUserClient, serviceMapClient, servicePriceClient);
    }

    @Test
    @DisplayName("Passenger get-off retries pending finalization with original trace end")
    void shouldRetryPendingFinalizationWithOriginalTraceEnd_whenRetryBecomesDue() {
        Long carId = 300L;
        givenFinalizationClock();
        givenPendingFinalizationOrder(carId, 1, LocalDateTime.now(Clock.fixed(FIXED_TRACE_END, TEST_ZONE)).minusSeconds(1));
        orderInfo.setPassengerGetoffLongitude("174.7000");
        orderInfo.setPassengerGetoffLatitude("-36.8000");
        givenFinalizationClaimSucceeds();
        givenCarLookupSucceeds(carId, "tid-300");
        givenTrackLookupSucceeds("tid-300", 5000L, 600L);
        when(servicePriceClient.calculatePrice(5000, 600, "110000", "1"))
                .thenReturn(ResponseResult.success(19.00));

        ResponseResult result = orderInfoService.passengerGetoff(getoffRequest());

        assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
        ArgumentCaptor<Long> endtimeCaptor = ArgumentCaptor.forClass(Long.class);
        verify(serviceMapClient).trsearch(eq("tid-300"), anyLong(), endtimeCaptor.capture());
        assertEquals(FIXED_TRACE_END.toEpochMilli(), endtimeCaptor.getValue());
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        UpdateWrapper<OrderInfo> successUpdate = captureTerminalFinalizationUpdate();
        assertTerminalCas(successUpdate, 2);
        assertSqlSetContains(successUpdate, "finalization_next_retry_at");
        assertSqlSetContains(successUpdate, "finalization_last_error");
        assertSqlSetDoesNotContain(successUpdate, "passenger_getoff_longitude");
        assertSqlSetDoesNotContain(successUpdate, "passenger_getoff_latitude");
        assertSqlSetDoesNotContain(successUpdate, "finalization_attempts");
    }

    @Test
    @DisplayName("Passenger get-off fails expired third attempt without remote calls")
    void shouldMoveExpiredThirdAttemptLeaseToFailed_withoutRemoteCalls() {
        givenFinalizationClock();
        givenPendingFinalizationOrder(300L, 3,
                LocalDateTime.now(Clock.fixed(FIXED_TRACE_END, TEST_ZONE)).minusSeconds(1));
        when(orderInfoMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        ResponseResult result = orderInfoService.passengerGetoff(getoffRequest());

        assertEquals(CommonStatus.FINALIZATION_FAILED.getCode(), result.getCode());
        assertEquals(CommonStatus.FINALIZATION_FAILED.getMessage(), result.getMessage());
        verifyNoInteractions(serviceDriverUserClient, serviceMapClient, servicePriceClient);
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        UpdateWrapper<OrderInfo> failedUpdate = captureFinalizationUpdates(1).get(0);
        assertTerminalCas(failedUpdate, 3);
        assertTrue(failedUpdate.getSqlSegment().contains("finalization_next_retry_at"));
        assertSqlSetContains(failedUpdate, "order_status");
        assertSqlSetContains(failedUpdate, "finalization_next_retry_at");
        assertSqlSetContains(failedUpdate, "finalization_last_error");
        assertSqlSetContains(failedUpdate, "gmt_modified");
        assertSqlSetDoesNotContain(failedUpdate, "finalization_attempts");
        assertTrue(failedUpdate.getParamNameValuePairs().containsValue(OrderConstant.FINALIZATION_FAILED));
        assertTrue(failedUpdate.getParamNameValuePairs().containsValue(
                "1605:Order finalization failed after maximum attempts"));
    }

    @Test
    @DisplayName("Passenger get-off does not overwrite state when terminal CAS is lost")
    void shouldReturnRetryScheduledAndNotOverwriteAttempt_whenTerminalCasIsLost() {
        Long carId = 300L;
        givenFinalizationClock();
        givenFinalizableOrder(carId);
        OrderInfo latestOrder = new OrderInfo();
        latestOrder.setId(ORDER_ID);
        latestOrder.setOrderStatus(OrderConstant.FINALIZATION_PENDING);
        latestOrder.setFinalizationAttempts(2);
        when(orderInfoMapper.selectOne(any())).thenReturn(orderInfo).thenReturn(latestOrder);
        when(orderInfoMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1).thenReturn(0);
        givenCarLookupSucceeds(carId, "tid-300");
        givenTrackLookupSucceeds("tid-300", 5000L, 600L);
        when(servicePriceClient.calculatePrice(5000, 600, "110000", "1"))
                .thenReturn(ResponseResult.success(19.00));

        ResponseResult result = orderInfoService.passengerGetoff(getoffRequest());

        assertEquals(CommonStatus.FINALIZATION_RETRY_SCHEDULED.getCode(), result.getCode());
        assertEquals(CommonStatus.FINALIZATION_RETRY_SCHEDULED.getMessage(), result.getMessage());
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        UpdateWrapper<OrderInfo> terminalUpdate = captureTerminalFinalizationUpdate();
        assertTerminalCas(terminalUpdate, 1);
        assertSqlSetContains(terminalUpdate, "order_status");
        assertSqlSetContains(terminalUpdate, "drive_mile");
        assertSqlSetContains(terminalUpdate, "drive_time");
        assertSqlSetContains(terminalUpdate, "price");
        assertSqlSetContains(terminalUpdate, "finalization_next_retry_at");
        assertSqlSetContains(terminalUpdate, "finalization_last_error");
        assertSqlSetDoesNotContain(terminalUpdate, "finalization_attempts");
    }

    @Test
    @DisplayName("Passenger get-off turns car lookup exception into safe pending finalization")
    void shouldPersistPendingFinalization_whenCarLookupThrowsRuntimeException() {
        Long carId = 300L;
        givenFinalizationClock();
        givenFinalizableOrder(carId);
        givenFinalizationClaimSucceeds();
        when(serviceDriverUserClient.getCarById(carId))
                .thenThrow(new RuntimeException("key=synthetic-secret"));

        ResponseResult result = orderInfoService.passengerGetoff(getoffRequest());

        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(), result.getCode());
        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage(), result.getMessage());
        assertFalse(result.getMessage().contains("synthetic-secret"));
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        verify(serviceMapClient, never()).trsearch(anyString(), anyLong(), anyLong());
        verify(servicePriceClient, never()).calculatePrice(anyInt(), anyInt(), anyString(), anyString());
        UpdateWrapper<OrderInfo> pendingUpdate = captureTerminalFinalizationUpdate();
        assertTerminalCas(pendingUpdate, 1);
        assertSqlSetContains(pendingUpdate, "finalization_next_retry_at");
        assertSqlSetContains(pendingUpdate, "finalization_last_error");
        assertTrue(pendingUpdate.getParamNameValuePairs().containsValue(
                "1700:Downstream service returned an invalid response"));
        assertFalse(pendingUpdate.getParamNameValuePairs().containsValue("key=synthetic-secret"));
    }

    @Test
    @DisplayName("Passenger get-off turns trace-search exception into safe pending finalization")
    void shouldPersistPendingFinalization_whenTraceSearchThrowsRuntimeException() {
        Long carId = 300L;
        givenFinalizationClock();
        givenFinalizableOrder(carId);
        givenFinalizationClaimSucceeds();
        givenCarLookupSucceeds(carId, "tid-300");
        when(serviceMapClient.trsearch(eq("tid-300"), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("query=synthetic-secret"));

        ResponseResult result = orderInfoService.passengerGetoff(getoffRequest());

        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(), result.getCode());
        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage(), result.getMessage());
        assertFalse(result.getMessage().contains("synthetic-secret"));
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        verify(servicePriceClient, never()).calculatePrice(anyInt(), anyInt(), anyString(), anyString());
        UpdateWrapper<OrderInfo> pendingUpdate = captureTerminalFinalizationUpdate();
        assertTerminalCas(pendingUpdate, 1);
        assertTrue(pendingUpdate.getParamNameValuePairs().containsValue(
                "1700:Downstream service returned an invalid response"));
        assertFalse(pendingUpdate.getParamNameValuePairs().containsValue("query=synthetic-secret"));
    }

    @Test
    @DisplayName("Passenger get-off turns price exception into safe pending finalization")
    void shouldPersistPendingFinalization_whenPriceCalculationThrowsRuntimeException() {
        Long carId = 300L;
        givenFinalizationClock();
        givenFinalizableOrder(carId);
        givenFinalizationClaimSucceeds();
        givenCarLookupSucceeds(carId, "tid-300");
        givenTrackLookupSucceeds("tid-300", 5000L, 600L);
        when(servicePriceClient.calculatePrice(5000, 600, "110000", "1"))
                .thenThrow(new RuntimeException("raw response key=synthetic-secret"));

        ResponseResult result = orderInfoService.passengerGetoff(getoffRequest());

        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(), result.getCode());
        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage(), result.getMessage());
        assertFalse(result.getMessage().contains("synthetic-secret"));
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        UpdateWrapper<OrderInfo> pendingUpdate = captureTerminalFinalizationUpdate();
        assertTerminalCas(pendingUpdate, 1);
        assertTrue(pendingUpdate.getParamNameValuePairs().containsValue(
                "1700:Downstream service returned an invalid response"));
        assertFalse(pendingUpdate.getParamNameValuePairs().containsValue("raw response key=synthetic-secret"));
    }

    // ======================== Passenger cancellation ========================

    @Test
    @DisplayName("Passenger cancels at status 1 (created): free cancellation")
    void should_cancelFree_when_passengerCancelsAtOrderStart() {
        givenOrderWithStatus(OrderConstant.ORDER_START);

        ResponseResult result = orderInfoService.cancel(ORDER_ID, PASSENGER);

        assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
        assertEquals(OrderConstant.CANCEL_PASSENGER_BEFORE, orderInfo.getCancelTypeCode());
        assertEquals(OrderConstant.ORDER_CANCEL, orderInfo.getOrderStatus());
    }

    @Test
    @DisplayName("Passenger cancels at status 2 within 1 minute: free cancellation")
    void should_cancelFree_when_passengerCancelsQuicklyAfterDriverAccepts() {
        givenOrderAcceptedMinutesAgo(1);  // just accepted, 1 minutes ago

        ResponseResult result = orderInfoService.cancel(ORDER_ID, PASSENGER);

        assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
        assertEquals(OrderConstant.CANCEL_PASSENGER_BEFORE, orderInfo.getCancelTypeCode());
    }

    @Test
    @DisplayName("Passenger cancels at status 2 after more than 1 full minute: penalty")
    void should_penalize_when_passengerCancelsLateAfterDriverAccepts() {
        givenOrderAcceptedMinutesAgo(3);  // 3 full minutes ago

        ResponseResult result = orderInfoService.cancel(ORDER_ID, PASSENGER);

        assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
        assertEquals(OrderConstant.CANCEL_PASSENGER_ILLEGAL, orderInfo.getCancelTypeCode());
    }

    @Test
    @DisplayName("Passenger cancels at status 3 (driver en route): always penalty")
    void should_penalize_when_passengerCancelsWhileDriverEnRoute() {
        givenOrderWithStatus(OrderConstant.DRIVER_TO_PICK_UP_PASSENGER);

        ResponseResult result = orderInfoService.cancel(ORDER_ID, PASSENGER);

        assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
        assertEquals(OrderConstant.CANCEL_PASSENGER_ILLEGAL, orderInfo.getCancelTypeCode());
    }

    @Test
    @DisplayName("Passenger cancels at status 4 (driver arrived): always penalty")
    void should_penalize_when_passengerCancelsAfterDriverArrived() {
        givenOrderWithStatus(OrderConstant.DRIVER_ARRIVED_DEPARTURE);

        ResponseResult result = orderInfoService.cancel(ORDER_ID, PASSENGER);

        assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
        assertEquals(OrderConstant.CANCEL_PASSENGER_ILLEGAL, orderInfo.getCancelTypeCode());
    }

    @Test
    @DisplayName("Passenger cancels at status 5 (riding): should fail")
    void should_fail_when_passengerCancelsDuringRide() {
        givenOrderWithStatus(OrderConstant.PICK_UP_PASSENGER);

        ResponseResult result = orderInfoService.cancel(ORDER_ID, PASSENGER);

        assertEquals(CommonStatus.ORDER_CANCEL_ERROR.getCode(), result.getCode());
    }

    @Test
    @DisplayName("Passenger cancels at status 8 (already paid): should fail")
    void should_fail_when_passengerCancelsAfterPayment() {
        givenOrderWithStatus(OrderConstant.SUCCESS_PAY);

        ResponseResult result = orderInfoService.cancel(ORDER_ID, PASSENGER);

        assertEquals(CommonStatus.ORDER_CANCEL_ERROR.getCode(), result.getCode());
    }

    // ======================== Driver cancellation ========================

    @Test
    @DisplayName("Driver cancels at status 2 within 1 minute: free cancellation")
    void should_cancelFree_when_driverCancelsQuicklyAfterAccepting() {
        givenOrderAcceptedMinutesAgo(0);

        ResponseResult result = orderInfoService.cancel(ORDER_ID, DRIVER);

        assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
        assertEquals(OrderConstant.CANCEL_DRIVER_BEFORE, orderInfo.getCancelTypeCode());
    }

    @Test
    @DisplayName("Driver cancels at status 2 after more than 1 full minute: penalty")
    void should_penalize_when_driverCancelsLateAfterAccepting() {
        givenOrderAcceptedMinutesAgo(3);

        ResponseResult result = orderInfoService.cancel(ORDER_ID, DRIVER);

        assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
        assertEquals(OrderConstant.CANCEL_DRIVER_ILLEGAL, orderInfo.getCancelTypeCode());
    }

    @Test
    @DisplayName("Driver cancels at status 3 (en route) after 3 minutes: penalty")
    void should_penalize_when_driverCancelsWhileEnRouteAfterTime() {
        orderInfo.setOrderStatus(OrderConstant.DRIVER_TO_PICK_UP_PASSENGER);
        orderInfo.setReceiveOrderTime(LocalDateTime.now().minusMinutes(3));
        when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(orderInfo);

        ResponseResult result = orderInfoService.cancel(ORDER_ID, DRIVER);

        assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
        assertEquals(OrderConstant.CANCEL_DRIVER_ILLEGAL, orderInfo.getCancelTypeCode());
    }

    @Test
    @DisplayName("Driver cancels at status 1 (before accepting): should fail")
    void should_fail_when_driverCancelsBeforeAccepting() {
        givenOrderWithStatus(OrderConstant.ORDER_START);

        ResponseResult result = orderInfoService.cancel(ORDER_ID, DRIVER);

        assertEquals(CommonStatus.ORDER_CANCEL_ERROR.getCode(), result.getCode());
    }

    @Test
    @DisplayName("Driver cancels at status 5 (passenger riding): should fail")
    void should_fail_when_driverCancelsDuringRide() {
        givenOrderWithStatus(OrderConstant.PICK_UP_PASSENGER);

        ResponseResult result = orderInfoService.cancel(ORDER_ID, DRIVER);

        assertEquals(CommonStatus.ORDER_CANCEL_ERROR.getCode(), result.getCode());
    }

    // ======================== Time boundary (ChronoUnit.MINUTES) ========================
    // ChronoUnit.MINUTES.between() truncates to whole minutes.
    // The code checks: between > 1
    // So "1 minute 59 seconds" -> between=1, NOT > 1 -> free cancellation
    // And "2 minutes 0 seconds" -> between=2, IS > 1 -> penalty
    // This means the effective threshold is ~2 full minutes, not 1.

    @Test
    @DisplayName("Time boundary: 1min 59sec after acceptance -> between=1 -> free cancellation")
    void should_cancelFree_when_cancelledAt1Minute59Seconds() {
        orderInfo.setOrderStatus(OrderConstant.DRIVER_RECEIVE_ORDER);
        // 1 min 59 sec ago — ChronoUnit.MINUTES.between will return 1
        orderInfo.setReceiveOrderTime(LocalDateTime.now().minusMinutes(1).minusSeconds(59));
        when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(orderInfo);

        ResponseResult result = orderInfoService.cancel(ORDER_ID, PASSENGER);

        assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
        // CHARACTERIZATION: between=1, code checks >1, so this is FREE (not penalty)
        // This means passengers effectively have ~2 minutes, not ~1 minute
        assertEquals(OrderConstant.CANCEL_PASSENGER_BEFORE, orderInfo.getCancelTypeCode());
    }

    @Test
    @DisplayName("Time boundary: exactly 2 minutes after acceptance -> between=2 -> penalty")
    void should_penalize_when_cancelledAtExactly2Minutes() {
        orderInfo.setOrderStatus(OrderConstant.DRIVER_RECEIVE_ORDER);
        orderInfo.setReceiveOrderTime(LocalDateTime.now().minusMinutes(2));
        when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(orderInfo);

        ResponseResult result = orderInfoService.cancel(ORDER_ID, PASSENGER);

        assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
        assertEquals(OrderConstant.CANCEL_PASSENGER_ILLEGAL, orderInfo.getCancelTypeCode());
    }

    // ======================== Verify database interaction ========================

    @Test
    @DisplayName("Successful cancellation persists changes to database")
    void should_callUpdateById_when_cancellationSucceeds() {
        givenOrderWithStatus(OrderConstant.ORDER_START);

        orderInfoService.cancel(ORDER_ID, PASSENGER);

        // verify() is another Mockito tool:
        // "confirm that updateById was called exactly once with our orderInfo"
        // This ensures the code actually saves the cancellation to the database.
        verify(orderInfoMapper, times(1)).updateById(orderInfo);
    }

    @Test
    @DisplayName("Failed cancellation does NOT update database")
    void should_notCallUpdateById_when_cancellationFails() {
        givenOrderWithStatus(OrderConstant.PICK_UP_PASSENGER);

        orderInfoService.cancel(ORDER_ID, PASSENGER);

        // verify with never(): "updateById should NOT have been called"
        verify(orderInfoMapper, never()).updateById(any());
    }

    // ======================== Dispatch lock safety ========================

    @Test
    @DisplayName("Dispatch releases driver lock when driver already has ongoing order")
    void should_unlock_when_driverAlreadyHasOngoingOrder() {
        OrderInfo dispatchOrder = dispatchOrder();
        Long carId = 300L;
        Long driverId = 200L;

        when(serviceMapClient.terminalAroundSearch(anyString(), anyInt()))
                .thenReturn(terminalSearchResult(Collections.singletonList(terminal(carId))))
                .thenReturn(terminalSearchResult(Collections.emptyList()))
                .thenReturn(terminalSearchResult(Collections.emptyList()));
        when(serviceDriverUserClient.getAvailableDriver(carId)).thenReturn(ResponseResult.success(driver(driverId, "SUV")));
        when(redissonClient.getLock("driver:assignment:" + driverId)).thenReturn(lock);
        when(orderInfoMapper.selectCount(any())).thenReturn(1);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        int result = orderInfoService.dispatchRealTimeOrder(dispatchOrder);

        assertEquals(0, result);
        verify(lock).lock();
        verify(lock).unlock();
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        verify(serviceSsePushClient, never()).push(any());
    }

    @Test
    @DisplayName("Dispatch releases driver lock when downstream call throws after lock acquisition")
    void should_unlock_when_downstreamCallThrowsAfterLockAcquired() {
        OrderInfo dispatchOrder = dispatchOrder();
        Long carId = 300L;
        Long driverId = 200L;

        when(serviceMapClient.terminalAroundSearch(anyString(), anyInt()))
                .thenReturn(terminalSearchResult(Collections.singletonList(terminal(carId))));
        when(serviceDriverUserClient.getAvailableDriver(carId)).thenReturn(ResponseResult.success(driver(driverId, "SUV")));
        when(redissonClient.getLock("driver:assignment:" + driverId)).thenReturn(lock);
        when(orderInfoMapper.selectCount(any())).thenReturn(0);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(serviceSsePushClient.push(any())).thenReturn("ok");
        when(serviceDriverUserClient.getCarById(carId)).thenThrow(new RuntimeException("car lookup failed"));

        assertThrows(RuntimeException.class, () -> orderInfoService.dispatchRealTimeOrder(dispatchOrder));

        verify(lock).lock();
        verify(lock).unlock();
        verify(orderInfoMapper).updateById(dispatchOrder);
    }

    @Test
    @DisplayName("Dispatch skips invalid terminal search responses")
    void shouldSkipInvalidTerminalSearchResponses_whenDispatching() {
        OrderInfo dispatchOrder = dispatchOrder();
        when(serviceMapClient.terminalAroundSearch(anyString(), anyInt()))
                .thenReturn(null)
                .thenReturn(ResponseResult.fail(1401, "map search failed"))
                .thenReturn(ResponseResult.success(null));

        int result = orderInfoService.dispatchRealTimeOrder(dispatchOrder);

        assertEquals(0, result);
        verify(serviceMapClient, times(3)).terminalAroundSearch(anyString(), anyInt());
        verify(serviceDriverUserClient, never()).getAvailableDriver(anyLong());
        verify(redissonClient, never()).getLock(anyString());
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        verify(serviceSsePushClient, never()).push(any());
    }

    @Test
    @DisplayName("Dispatch skips invalid driver candidate responses")
    void shouldSkipInvalidDriverCandidateResponses_whenDispatching() {
        OrderInfo dispatchOrder = dispatchOrder();
        Long carIdWithFailure = 300L;
        Long carIdWithNullData = 301L;
        when(serviceMapClient.terminalAroundSearch(anyString(), anyInt()))
                .thenReturn(terminalSearchResult(Arrays.asList(terminal(carIdWithFailure), terminal(carIdWithNullData))))
                .thenReturn(terminalSearchResult(Collections.emptyList()))
                .thenReturn(terminalSearchResult(Collections.emptyList()));
        when(serviceDriverUserClient.getAvailableDriver(carIdWithFailure))
                .thenReturn(ResponseResult.fail(1500, "binding does not exist"));
        when(serviceDriverUserClient.getAvailableDriver(carIdWithNullData))
                .thenReturn(ResponseResult.success(null));

        int result = orderInfoService.dispatchRealTimeOrder(dispatchOrder);

        assertEquals(0, result);
        verify(serviceDriverUserClient, times(2)).getAvailableDriver(anyLong());
        verify(redissonClient, never()).getLock(anyString());
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        verify(serviceSsePushClient, never()).push(any());
    }

    @Test
    @DisplayName("Order creation returns dispatch failure when all attempts are exhausted")
    void shouldReturnDispatchFailure_whenAllAttemptsAreExhausted() {
        OrderRequest orderRequest = newOrderRequest();
        OrderInfoService spyService = spy(orderInfoService);
        ReflectionTestUtils.setField(spyService, "dispatchRetryDelayMs", 0L);

        when(servicePriceClient.ifPriceExists(any())).thenReturn(ResponseResult.success(true));
        when(serviceDriverUserClient.isAvailableDriver("110000")).thenReturn(ResponseResult.success(true));
        when(servicePriceClient.isLatest(any())).thenReturn(ResponseResult.success(true));
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(orderInfoMapper.selectCount(any())).thenReturn(0);
        doReturn(0).when(spyService).dispatchRealTimeOrder(any(OrderInfo.class));

        ResponseResult result = spyService.add(orderRequest);

        assertEquals(1604, result.getCode());
        assertEquals("No driver could be assigned to the order", result.getMessage());
        verify(spyService, times(6)).dispatchRealTimeOrder(any(OrderInfo.class));
        verify(orderInfoMapper, times(1)).insert(any(OrderInfo.class));
        ArgumentCaptor<OrderInfo> invalidOrderCaptor = ArgumentCaptor.forClass(OrderInfo.class);
        verify(orderInfoMapper, times(1)).updateById(invalidOrderCaptor.capture());
        assertEquals(OrderConstant.ORDER_INVALID, invalidOrderCaptor.getValue().getOrderStatus());
    }
}
