package com.george.serviceorder.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.george.serviceorder.remote.FinalizationDriverUserClient;
import com.george.serviceorder.remote.FinalizationMapClient;
import com.george.serviceorder.remote.FinalizationPriceClient;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

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
    FinalizationMapClient finalizationMapClient;

    @Mock
    FinalizationDriverUserClient finalizationDriverUserClient;

    @Mock
    FinalizationPriceClient finalizationPriceClient;

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
    private static final Instant CANCELLATION_NOW = Instant.parse("2026-07-28T01:02:03Z");
    private static final ZoneId TEST_ZONE = ZoneId.of("Pacific/Auckland");
    private static final int FINALIZATION_IN_PROGRESS_CODE = 1611;
    private static final String FINALIZATION_IN_PROGRESS_MESSAGE =
            "Order is being finalized and cannot be modified";

    private OrderInfo orderInfo;

    private interface LegacyTransitionInvocation {
        ResponseResult invoke(OrderRequest orderRequest);
    }

    private static class LegacyTransitionCase {
        private final String name;
        private final int predecessorStatus;
        private final int targetStatus;
        private final LegacyTransitionInvocation invocation;

        private LegacyTransitionCase(String name, int predecessorStatus, int targetStatus,
                                     LegacyTransitionInvocation invocation) {
            this.name = name;
            this.predecessorStatus = predecessorStatus;
            this.targetStatus = targetStatus;
            this.invocation = invocation;
        }
    }

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

    private void givenAcceptedOrderSecondsAgo(long elapsedSeconds) {
        Clock fixedClock = Clock.fixed(CANCELLATION_NOW, TEST_ZONE);
        ReflectionTestUtils.setField(orderInfoService, "clock", fixedClock);
        orderInfo.setOrderStatus(OrderConstant.DRIVER_RECEIVE_ORDER);
        orderInfo.setReceiveOrderTime(LocalDateTime.now(fixedClock).minusSeconds(elapsedSeconds));
        when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(orderInfo);
    }

    private LocalDateTime cancellationNow() {
        return LocalDateTime.ofInstant(CANCELLATION_NOW, TEST_ZONE);
    }

    private void assertSuccessfulCancellation(ResponseResult result, int expectedCancelTypeCode, String identity) {
        assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
        assertEquals(expectedCancelTypeCode, orderInfo.getCancelTypeCode());
        assertEquals(cancellationNow(), orderInfo.getCancelTime());
        assertEquals(Integer.valueOf(identity), orderInfo.getCancelOperator());
        assertEquals(OrderConstant.ORDER_CANCEL, orderInfo.getOrderStatus());
        verify(orderInfoMapper, times(1)).updateById(orderInfo);
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

    private OrderRequest legacyTransitionRequest() {
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setOrderId(ORDER_ID);
        orderRequest.setToPickUpPassengerLongitude("174.7700");
        orderRequest.setToPickUpPassengerLatitude("-36.8500");
        orderRequest.setToPickUpPassengerAddress("Pickup point");
        orderRequest.setPickUpPassengerLongitude("174.7710");
        orderRequest.setPickUpPassengerLatitude("-36.8510");
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
        when(finalizationDriverUserClient.getCarById(carId)).thenReturn(ResponseResult.success(car));
    }

    private void givenTrackLookupSucceeds(String tid, long driveMile, long driveDurationSeconds) {
        TrsearchResponse trsearchResponse = new TrsearchResponse();
        trsearchResponse.setDriveMile(driveMile);
        trsearchResponse.setDriveTime(driveDurationSeconds);
        when(finalizationMapClient.trsearch(eq(tid), anyLong(), anyLong()))
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

    private void assertSqlSetContainsSelfAssignment(UpdateWrapper<OrderInfo> updateWrapper, String columnName) {
        String normalizedSqlSet = normalizedSqlSegment(updateWrapper.getSqlSet());
        String normalizedColumn = columnName.toUpperCase(Locale.ROOT);
        assertTrue(Pattern.compile("(^|,\\s*)" + normalizedColumn + "\\s*=\\s*"
                        + normalizedColumn + "(\\s*,|$)")
                .matcher(normalizedSqlSet).find(), normalizedSqlSet);
    }

    private void assertSqlSetDoesNotContainSelfAssignment(UpdateWrapper<OrderInfo> updateWrapper,
                                                          String columnName) {
        String normalizedSqlSet = normalizedSqlSegment(updateWrapper.getSqlSet());
        String normalizedColumn = columnName.toUpperCase(Locale.ROOT);
        assertFalse(Pattern.compile("(^|,\\s*)" + normalizedColumn + "\\s*=\\s*"
                        + normalizedColumn + "(\\s*,|$)")
                .matcher(normalizedSqlSet).find(), normalizedSqlSet);
    }

    private void assertFinalizationWrapperPreservesCreationTime(UpdateWrapper<OrderInfo> updateWrapper) {
        assertSqlSetContainsSelfAssignment(updateWrapper, "gmt_create");
        assertSqlSetContains(updateWrapper, "gmt_modified");
        assertSqlSetDoesNotContainSelfAssignment(updateWrapper, "gmt_modified");
    }

    private String normalizedSqlSegment(String sqlSegment) {
        return sqlSegment
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }

    private void assertNullRetryTimeDuePredicate(String sqlSegment) {
        String normalizedSql = normalizedSqlSegment(sqlSegment);
        assertTrue(normalizedSql.contains("FINALIZATION_NEXT_RETRY_AT"), normalizedSql);
        assertTrue(normalizedSql.contains("IS NULL"), normalizedSql);
        assertTrue(normalizedSql.contains("<="), normalizedSql);
        assertTrue(Pattern.compile(
                "FINALIZATION_NEXT_RETRY_AT\\s+IS\\s+NULL\\s+OR\\s+"
                        + "FINALIZATION_NEXT_RETRY_AT\\s*<="
        ).matcher(normalizedSql).find(), normalizedSql);
    }

    private void assertWrapperContainsValue(UpdateWrapper<OrderInfo> updateWrapper, Object expectedValue) {
        assertTrue(updateWrapper.getParamNameValuePairs().containsValue(expectedValue),
                "Expected wrapper to contain value " + expectedValue
                        + " but found " + updateWrapper.getParamNameValuePairs());
    }

    private List<LegacyTransitionCase> legacyTransitionCases() {
        return Arrays.asList(
                new LegacyTransitionCase(
                        "toPickUpPassenger",
                        OrderConstant.DRIVER_RECEIVE_ORDER,
                        OrderConstant.DRIVER_TO_PICK_UP_PASSENGER,
                        orderInfoService::toPickUpPassenger),
                new LegacyTransitionCase(
                        "arrivedDeparture",
                        OrderConstant.DRIVER_TO_PICK_UP_PASSENGER,
                        OrderConstant.DRIVER_ARRIVED_DEPARTURE,
                        orderInfoService::arrivedDeparture),
                new LegacyTransitionCase(
                        "pickUpPassenger",
                        OrderConstant.DRIVER_ARRIVED_DEPARTURE,
                        OrderConstant.PICK_UP_PASSENGER,
                        orderInfoService::pickUpPassenger),
                new LegacyTransitionCase(
                        "pushPayInfo",
                        OrderConstant.PASSENGER_GETOFF,
                        OrderConstant.TO_START_PAY,
                        orderInfoService::pushPayInfo),
                new LegacyTransitionCase(
                        "pay",
                        OrderConstant.TO_START_PAY,
                        OrderConstant.SUCCESS_PAY,
                        orderInfoService::pay)
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private UpdateWrapper<OrderInfo> captureSingleLegacyUpdate() {
        ArgumentCaptor<UpdateWrapper> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(orderInfoMapper, times(1)).update(isNull(), captor.capture());
        return captor.getValue();
    }

    private OrderInfo orderWithStatus(int status) {
        OrderInfo order = new OrderInfo();
        order.setId(ORDER_ID);
        order.setOrderStatus(status);
        order.setReceiveOrderTime(LocalDateTime.now().minusMinutes(3));
        return order;
    }

    private void assertNoLegacyTransitionSideEffects() {
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        verify(orderInfoMapper, never()).update(isNull(), any(UpdateWrapper.class));
        verifyNoInteractions(serviceDriverUserClient, serviceMapClient, servicePriceClient,
                serviceSsePushClient, finalizationDriverUserClient, finalizationMapClient,
                finalizationPriceClient);
    }

    private String readRepositoryFile(String path) throws Exception {
        Path repositoryPath = Paths.get(path);
        if (!Files.exists(repositoryPath)) {
            repositoryPath = Paths.get("..", path);
        }
        return new String(Files.readAllBytes(repositoryPath), StandardCharsets.UTF_8);
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
    @DisplayName("Legacy lifecycle and payment transitions reject finalization-owned states")
    void shouldRejectLegacyTransitions_whenOrderIsBeingFinalized() {
        for (LegacyTransitionCase transitionCase : legacyTransitionCases()) {
            for (int finalizationStatus : Arrays.asList(
                    OrderConstant.FINALIZATION_PENDING,
                    OrderConstant.FINALIZATION_FAILED)) {
                reset(orderInfoMapper, serviceDriverUserClient, serviceMapClient, servicePriceClient,
                        serviceSsePushClient, finalizationDriverUserClient, finalizationMapClient,
                        finalizationPriceClient);
                OrderInfo current = orderWithStatus(finalizationStatus);
                when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(current);

                ResponseResult result = transitionCase.invocation.invoke(legacyTransitionRequest());

                assertEquals(FINALIZATION_IN_PROGRESS_CODE, result.getCode(),
                        transitionCase.name + " should reject status " + finalizationStatus);
                assertEquals(FINALIZATION_IN_PROGRESS_MESSAGE, result.getMessage(),
                        transitionCase.name + " should return the stable finalization fence message");
                assertNoLegacyTransitionSideEffects();
            }
        }
    }

    @Test
    @DisplayName("Cancel rejects finalization-owned states without writing")
    void shouldRejectCancellation_whenOrderIsBeingFinalized() {
        for (int finalizationStatus : Arrays.asList(
                OrderConstant.FINALIZATION_PENDING,
                OrderConstant.FINALIZATION_FAILED)) {
            reset(orderInfoMapper, serviceDriverUserClient, serviceMapClient, servicePriceClient,
                    serviceSsePushClient, finalizationDriverUserClient, finalizationMapClient,
                    finalizationPriceClient);
            OrderInfo current = orderWithStatus(finalizationStatus);
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(current);

            ResponseResult result = orderInfoService.cancel(ORDER_ID, PASSENGER);

            assertEquals(FINALIZATION_IN_PROGRESS_CODE, result.getCode());
            assertEquals(FINALIZATION_IN_PROGRESS_MESSAGE, result.getMessage());
            assertNoLegacyTransitionSideEffects();
        }
    }

    @Test
    @DisplayName("Legacy lifecycle and payment transitions use predecessor-state CAS")
    void shouldUsePredecessorCas_whenLegacyTransitionSucceeds() {
        for (LegacyTransitionCase transitionCase : legacyTransitionCases()) {
            reset(orderInfoMapper);
            when(orderInfoMapper.selectById(ORDER_ID)).thenReturn(orderWithStatus(transitionCase.predecessorStatus));
            when(orderInfoMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

            ResponseResult result = transitionCase.invocation.invoke(legacyTransitionRequest());

            assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode(), transitionCase.name);
            verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
            UpdateWrapper<OrderInfo> updateWrapper = captureSingleLegacyUpdate();
            assertTrue(updateWrapper.getSqlSegment().contains("id"), updateWrapper.getSqlSegment());
            assertTrue(updateWrapper.getSqlSegment().contains("order_status"), updateWrapper.getSqlSegment());
            assertWrapperContainsValue(updateWrapper, ORDER_ID);
            assertWrapperContainsValue(updateWrapper, transitionCase.predecessorStatus);
            assertSqlSetContains(updateWrapper, "order_status");
            assertWrapperContainsValue(updateWrapper, transitionCase.targetStatus);
            assertSqlSetContainsSelfAssignment(updateWrapper, "gmt_create");
            assertSqlSetContainsSelfAssignment(updateWrapper, "gmt_modified");
        }
    }

    @Test
    @DisplayName("Legacy transition CAS miss is resolved from the reread state")
    void shouldResolveLegacyTransitionCasMiss_fromRereadState() {
        for (LegacyTransitionCase transitionCase : legacyTransitionCases()) {
            assertLegacyCasMissResult(transitionCase, orderWithStatus(transitionCase.targetStatus),
                    CommonStatus.SUCCESS.getCode(), CommonStatus.SUCCESS.getMessage());
            assertLegacyCasMissResult(transitionCase, orderWithStatus(OrderConstant.FINALIZATION_PENDING),
                    FINALIZATION_IN_PROGRESS_CODE, FINALIZATION_IN_PROGRESS_MESSAGE);
            assertLegacyCasMissResult(transitionCase, orderWithStatus(OrderConstant.FINALIZATION_FAILED),
                    FINALIZATION_IN_PROGRESS_CODE, FINALIZATION_IN_PROGRESS_MESSAGE);
            assertLegacyCasMissResult(transitionCase, null,
                    CommonStatus.ORDER_NOT_FOUND.getCode(), CommonStatus.ORDER_NOT_FOUND.getMessage());
            assertLegacyCasMissResult(transitionCase, orderWithStatus(OrderConstant.ORDER_START),
                    1610, "Order state transition is not allowed");
        }
    }

    private void assertLegacyCasMissResult(LegacyTransitionCase transitionCase, OrderInfo rereadOrder,
                                           int expectedCode, String expectedMessage) {
        reset(orderInfoMapper);
        when(orderInfoMapper.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(transitionCase.predecessorStatus))
                .thenReturn(rereadOrder);
        when(orderInfoMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);

        ResponseResult result = transitionCase.invocation.invoke(legacyTransitionRequest());

        assertEquals(expectedCode, result.getCode(), transitionCase.name);
        assertEquals(expectedMessage, result.getMessage(), transitionCase.name);
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        verify(orderInfoMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
    }

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
        when(finalizationPriceClient.calculatePrice(5000, 600, "110000", "1"))
                .thenReturn(ResponseResult.success(19.00));

        ResponseResult result = orderInfoService.passengerGetoff(orderRequest);

        assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
        verify(finalizationPriceClient, times(1)).calculatePrice(5000, 600, "110000", "1");
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        List<UpdateWrapper<OrderInfo>> updates = captureFinalizationUpdates(2);
        UpdateWrapper<OrderInfo> claimUpdate = updates.get(0);
        UpdateWrapper<OrderInfo> successUpdate = updates.get(1);
        assertFinalizationWrapperPreservesCreationTime(claimUpdate);
        assertFinalizationWrapperPreservesCreationTime(successUpdate);
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
            when(finalizationPriceClient.calculatePrice(5000, 600, "110000", "1"))
                    .thenReturn(ResponseResult.success(19.00));

            orderInfoService.passengerGetoff(orderRequest);

            ArgumentCaptor<Long> starttimeCaptor = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<Long> endtimeCaptor = ArgumentCaptor.forClass(Long.class);
            verify(finalizationMapClient, times(1)).trsearch(eq("tid-300"), starttimeCaptor.capture(), endtimeCaptor.capture());
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
        when(finalizationMapClient.trsearch(eq("tid-300"), anyLong(), anyLong()))
                .thenReturn(ResponseResult.fail(1402, "No track data is available for the requested interval"));

        ResponseResult result = orderInfoService.passengerGetoff(orderRequest);

        assertEquals(1402, result.getCode());
        assertEquals("No track data is available for the requested interval", result.getMessage());
        verify(finalizationPriceClient, never()).calculatePrice(anyInt(), anyInt(), anyString(), anyString());
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        UpdateWrapper<OrderInfo> pendingUpdate = captureTerminalFinalizationUpdate();
        assertTerminalCas(pendingUpdate, 1);
        assertFinalizationWrapperPreservesCreationTime(pendingUpdate);
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
        when(finalizationMapClient.trsearch(eq("tid-300"), anyLong(), anyLong()))
                .thenReturn(ResponseResult.success(null));

        ResponseResult result = orderInfoService.passengerGetoff(orderRequest);

        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(), result.getCode());
        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage(), result.getMessage());
        verify(finalizationPriceClient, never()).calculatePrice(anyInt(), anyInt(), anyString(), anyString());
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
    @DisplayName("Passenger get-off rejects negative track distance without pricing")
    void shouldPersistDownstreamResponseError_whenTrackDistanceIsNegative() {
        Long carId = 300L;
        OrderRequest orderRequest = getoffRequest();
        givenFinalizationClock();
        givenFinalizableOrder(carId);
        givenFinalizationClaimSucceeds();
        givenCarLookupSucceeds(carId, "tid-300");
        givenTrackLookupSucceeds("tid-300", -1L, 600L);

        ResponseResult result = orderInfoService.passengerGetoff(orderRequest);

        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(), result.getCode());
        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage(), result.getMessage());
        verify(finalizationPriceClient, never()).calculatePrice(anyInt(), anyInt(), anyString(), anyString());
        UpdateWrapper<OrderInfo> pendingUpdate = captureTerminalFinalizationUpdate();
        assertWrapperContainsValue(pendingUpdate, "1700:Downstream service returned an invalid response");
    }

    @Test
    @DisplayName("Passenger get-off rejects negative track duration without pricing")
    void shouldPersistDownstreamResponseError_whenTrackDurationIsNegative() {
        Long carId = 300L;
        OrderRequest orderRequest = getoffRequest();
        givenFinalizationClock();
        givenFinalizableOrder(carId);
        givenFinalizationClaimSucceeds();
        givenCarLookupSucceeds(carId, "tid-300");
        givenTrackLookupSucceeds("tid-300", 5000L, -1L);

        ResponseResult result = orderInfoService.passengerGetoff(orderRequest);

        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(), result.getCode());
        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage(), result.getMessage());
        verify(finalizationPriceClient, never()).calculatePrice(anyInt(), anyInt(), anyString(), anyString());
        UpdateWrapper<OrderInfo> pendingUpdate = captureTerminalFinalizationUpdate();
        assertWrapperContainsValue(pendingUpdate, "1700:Downstream service returned an invalid response");
    }

    @Test
    @DisplayName("Passenger get-off rejects track values outside pricing integer range")
    void shouldPersistDownstreamResponseError_whenTrackValuesExceedIntegerRange() {
        Long carId = 300L;
        OrderRequest orderRequest = getoffRequest();
        givenFinalizationClock();
        givenFinalizableOrder(carId);
        givenFinalizationClaimSucceeds();
        givenCarLookupSucceeds(carId, "tid-300");
        givenTrackLookupSucceeds("tid-300", (long) Integer.MAX_VALUE + 1L, 600L);

        ResponseResult result = orderInfoService.passengerGetoff(orderRequest);

        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(), result.getCode());
        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage(), result.getMessage());
        verify(finalizationPriceClient, never()).calculatePrice(anyInt(), anyInt(), anyString(), anyString());
        UpdateWrapper<OrderInfo> pendingUpdate = captureTerminalFinalizationUpdate();
        assertWrapperContainsValue(pendingUpdate, "1700:Downstream service returned an invalid response");
    }

    @Test
    @DisplayName("Passenger get-off rejects track duration outside pricing integer range")
    void shouldPersistDownstreamResponseError_whenTrackDurationExceedsIntegerRange() {
        Long carId = 300L;
        OrderRequest orderRequest = getoffRequest();
        givenFinalizationClock();
        givenFinalizableOrder(carId);
        givenFinalizationClaimSucceeds();
        givenCarLookupSucceeds(carId, "tid-300");
        givenTrackLookupSucceeds("tid-300", 5000L, (long) Integer.MAX_VALUE + 1L);

        ResponseResult result = orderInfoService.passengerGetoff(orderRequest);

        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(), result.getCode());
        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage(), result.getMessage());
        verify(finalizationPriceClient, never()).calculatePrice(anyInt(), anyInt(), anyString(), anyString());
        UpdateWrapper<OrderInfo> pendingUpdate = captureTerminalFinalizationUpdate();
        assertTerminalCas(pendingUpdate, 1);
        assertFinalizationWrapperPreservesCreationTime(pendingUpdate);
        assertSqlSetDoesNotContain(pendingUpdate, "drive_mile");
        assertSqlSetDoesNotContain(pendingUpdate, "drive_time");
        assertSqlSetDoesNotContain(pendingUpdate, "price");
        assertWrapperContainsValue(pendingUpdate, "1700:Downstream service returned an invalid response");
    }

    @Test
    @DisplayName("Passenger get-off treats zero track distance and duration as empty track")
    void shouldPersistTrackEmptyFailure_whenTrackDistanceAndDurationAreZero() {
        Long carId = 300L;
        OrderRequest orderRequest = getoffRequest();
        givenFinalizationClock();
        givenFinalizableOrder(carId);
        givenFinalizationClaimSucceeds();
        givenCarLookupSucceeds(carId, "tid-300");
        givenTrackLookupSucceeds("tid-300", 0L, 0L);

        ResponseResult result = orderInfoService.passengerGetoff(orderRequest);

        assertEquals(CommonStatus.MAP_TRACK_EMPTY.getCode(), result.getCode());
        assertEquals(CommonStatus.MAP_TRACK_EMPTY.getMessage(), result.getMessage());
        verify(finalizationPriceClient, never()).calculatePrice(anyInt(), anyInt(), anyString(), anyString());
        UpdateWrapper<OrderInfo> pendingUpdate = captureTerminalFinalizationUpdate();
        assertWrapperContainsValue(pendingUpdate, "1402:No track data is available for the requested interval");
    }

    @Test
    @DisplayName("Passenger get-off rejects negative calculated price")
    void shouldPersistDownstreamResponseError_whenCalculatedPriceIsNegative() {
        Long carId = 300L;
        OrderRequest orderRequest = getoffRequest();
        givenFinalizationClock();
        givenFinalizableOrder(carId);
        givenFinalizationClaimSucceeds();
        givenCarLookupSucceeds(carId, "tid-300");
        givenTrackLookupSucceeds("tid-300", 5000L, 600L);
        when(finalizationPriceClient.calculatePrice(5000, 600, "110000", "1"))
                .thenReturn(ResponseResult.success(-0.01));

        ResponseResult result = orderInfoService.passengerGetoff(orderRequest);

        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(), result.getCode());
        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage(), result.getMessage());
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        UpdateWrapper<OrderInfo> pendingUpdate = captureTerminalFinalizationUpdate();
        assertSqlSetDoesNotContain(pendingUpdate, "price");
        assertWrapperContainsValue(pendingUpdate, "1700:Downstream service returned an invalid response");
    }

    @Test
    @DisplayName("Passenger get-off safely passes maximum integer track values to pricing")
    void shouldPassMaximumIntegerTrackValuesToPricing_whenTrackValuesAreInRange() {
        Long carId = 300L;
        OrderRequest orderRequest = getoffRequest();
        givenFinalizationClock();
        givenFinalizableOrder(carId);
        givenFinalizationClaimSucceeds();
        givenCarLookupSucceeds(carId, "tid-300");
        givenTrackLookupSucceeds("tid-300", (long) Integer.MAX_VALUE, (long) Integer.MAX_VALUE);
        when(finalizationPriceClient.calculatePrice(Integer.MAX_VALUE, Integer.MAX_VALUE, "110000", "1"))
                .thenReturn(ResponseResult.success(0.00));

        ResponseResult result = orderInfoService.passengerGetoff(orderRequest);

        assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
        verify(finalizationPriceClient, times(1))
                .calculatePrice(Integer.MAX_VALUE, Integer.MAX_VALUE, "110000", "1");
        UpdateWrapper<OrderInfo> successUpdate = captureTerminalFinalizationUpdate();
        assertWrapperContainsValue(successUpdate, (long) Integer.MAX_VALUE);
        assertWrapperContainsValue(successUpdate, 0.00);
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
        verifyNoInteractions(finalizationDriverUserClient, finalizationMapClient, finalizationPriceClient);
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
            verifyNoInteractions(finalizationDriverUserClient, finalizationMapClient, finalizationPriceClient);
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
        verifyNoInteractions(finalizationDriverUserClient, finalizationMapClient, finalizationPriceClient);
    }

    @Test
    @DisplayName("Passenger get-off persists pending finalization when car lookup fails")
    void shouldPersistPendingFinalization_whenCarLookupFails() {
        Long carId = 300L;
        givenFinalizationClock();
        givenFinalizableOrder(carId);
        givenFinalizationClaimSucceeds();
        when(finalizationDriverUserClient.getCarById(carId))
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
        verify(finalizationMapClient, never()).trsearch(anyString(), anyLong(), anyLong());
        verify(finalizationPriceClient, never()).calculatePrice(anyInt(), anyInt(), anyString(), anyString());
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
        when(finalizationPriceClient.calculatePrice(5000, 600, "110000", "1"))
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
        when(finalizationDriverUserClient.getCarById(carId)).thenAnswer(invocation -> {
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
        when(finalizationDriverUserClient.getCarById(carId)).thenAnswer(invocation -> {
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
        verifyNoInteractions(finalizationDriverUserClient, finalizationMapClient, finalizationPriceClient);
    }

    @Test
    @DisplayName("Due finalization scan treats null retry time as due")
    void shouldIncludeNullRetryTimeInDueScan_whenSchedulingFinalizationRetries() {
        LocalDateTime now = LocalDateTime.now(Clock.fixed(FIXED_TRACE_END, TEST_ZONE));
        when(orderInfoMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        int processed = orderInfoService.retryDueFinalizations(now, 50);

        assertEquals(0, processed);
        ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orderInfoMapper, times(1)).selectList(captor.capture());
        assertNullRetryTimeDuePredicate(captor.getValue().getSqlSegment());
        verifyNoInteractions(finalizationDriverUserClient, finalizationMapClient, finalizationPriceClient);
    }

    @Test
    @DisplayName("Passenger get-off moves finalization to failed at maximum attempts")
    void shouldMoveToFinalizationFailed_whenMaximumAttemptsAreReached() {
        Long carId = 300L;
        givenFinalizationClock();
        givenPendingFinalizationOrder(carId, 2, LocalDateTime.now(Clock.fixed(FIXED_TRACE_END, TEST_ZONE)).minusSeconds(1));
        givenFinalizationClaimSucceeds();
        when(finalizationDriverUserClient.getCarById(carId))
                .thenReturn(ResponseResult.fail(1700, "Downstream service returned an invalid response"));

        ResponseResult result = orderInfoService.passengerGetoff(getoffRequest());

        assertEquals(CommonStatus.FINALIZATION_FAILED.getCode(), result.getCode());
        assertEquals(CommonStatus.FINALIZATION_FAILED.getMessage(), result.getMessage());
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        UpdateWrapper<OrderInfo> failedUpdate = captureTerminalFinalizationUpdate();
        assertTerminalCas(failedUpdate, 3);
        assertFinalizationWrapperPreservesCreationTime(failedUpdate);
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
    @DisplayName("Passenger get-off moves null retry-time max-attempt finalization to failed")
    void shouldMoveNullRetryTimeExpiredAttemptsToFailed_whenClientTouchesOrder() {
        givenFinalizationClock();
        givenPendingFinalizationOrder(300L, 3, null);
        when(orderInfoMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        ResponseResult result = orderInfoService.passengerGetoff(getoffRequest());

        assertEquals(CommonStatus.FINALIZATION_FAILED.getCode(), result.getCode());
        assertEquals(CommonStatus.FINALIZATION_FAILED.getMessage(), result.getMessage());
        verify(orderInfoMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        verifyNoInteractions(finalizationDriverUserClient, finalizationMapClient, finalizationPriceClient);
        UpdateWrapper<OrderInfo> failedUpdate = captureFinalizationUpdates(1).get(0);
        assertTerminalCas(failedUpdate, 3);
        assertFinalizationWrapperPreservesCreationTime(failedUpdate);
        assertNullRetryTimeDuePredicate(failedUpdate.getSqlSegment());
        assertSqlSetContains(failedUpdate, "order_status");
        assertSqlSetContains(failedUpdate, "finalization_next_retry_at");
        assertSqlSetContains(failedUpdate, "finalization_last_error");
    }

    @Test
    @DisplayName("Failed finalization recovery schedules a controlled retry without remote calls")
    void shouldScheduleRecoveryForFailedFinalization_withoutRemoteCalls() {
        givenFinalizationClock();
        orderInfo.setOrderStatus(OrderConstant.FINALIZATION_FAILED);
        orderInfo.setFinalizationAttempts(3);
        orderInfo.setFinalizationTraceEndEpochMs(1784067000000L);
        orderInfo.setDriveMile(5000L);
        orderInfo.setDriveTime(600L);
        orderInfo.setPrice(19.00);
        when(orderInfoMapper.selectOne(any())).thenReturn(orderInfo);
        when(orderInfoMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        ResponseResult result = orderInfoService.scheduleFailedFinalizationRecovery(ORDER_ID);

        assertEquals(CommonStatus.FINALIZATION_RECOVERY_SCHEDULED.getCode(), result.getCode());
        assertEquals(CommonStatus.FINALIZATION_RECOVERY_SCHEDULED.getMessage(), result.getMessage());
        verifyNoInteractions(finalizationDriverUserClient, finalizationMapClient, finalizationPriceClient);
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        UpdateWrapper<OrderInfo> recoveryUpdate = captureFinalizationUpdates(1).get(0);
        String where = recoveryUpdate.getSqlSegment();
        assertTrue(where.contains("id"), where);
        assertTrue(where.contains("order_status"), where);
        assertTrue(where.contains("finalization_attempts"), where);
        assertWrapperContainsValue(recoveryUpdate, ORDER_ID);
        assertWrapperContainsValue(recoveryUpdate, OrderConstant.FINALIZATION_FAILED);
        assertWrapperContainsValue(recoveryUpdate, 3);
        assertSqlSetContains(recoveryUpdate, "order_status");
        assertSqlSetContains(recoveryUpdate, "finalization_attempts");
        assertSqlSetContains(recoveryUpdate, "finalization_next_retry_at");
        assertSqlSetContains(recoveryUpdate, "finalization_last_error");
        assertSqlSetContains(recoveryUpdate, "gmt_modified");
        assertFinalizationWrapperPreservesCreationTime(recoveryUpdate);
        assertSqlSetDoesNotContain(recoveryUpdate, "finalization_trace_end_epoch_ms");
        assertSqlSetDoesNotContain(recoveryUpdate, "drive_mile");
        assertSqlSetDoesNotContain(recoveryUpdate, "drive_time");
        assertSqlSetDoesNotContain(recoveryUpdate, "price");
        assertWrapperContainsValue(recoveryUpdate, OrderConstant.FINALIZATION_PENDING);
        assertWrapperContainsValue(recoveryUpdate, 0);
        assertWrapperContainsValue(recoveryUpdate,
                "1609:Failed order finalization recovery is scheduled");
    }

    @Test
    @DisplayName("Failed finalization recovery uses persisted attempts instead of current configured maximum")
    void shouldScheduleLegacyFailedFinalization_whenConfiguredMaximumHasIncreased() {
        givenFinalizationClock();
        ReflectionTestUtils.setField(orderInfoService, "maxFinalizationAttempts", 5);
        orderInfo.setOrderStatus(OrderConstant.FINALIZATION_FAILED);
        orderInfo.setFinalizationAttempts(3);
        when(orderInfoMapper.selectOne(any())).thenReturn(orderInfo);
        when(orderInfoMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        ResponseResult result = orderInfoService.scheduleFailedFinalizationRecovery(ORDER_ID);

        assertEquals(CommonStatus.FINALIZATION_RECOVERY_SCHEDULED.getCode(), result.getCode());
        assertEquals(CommonStatus.FINALIZATION_RECOVERY_SCHEDULED.getMessage(), result.getMessage());
        verifyNoInteractions(
                serviceDriverUserClient,
                serviceMapClient,
                servicePriceClient,
                serviceSsePushClient,
                finalizationDriverUserClient,
                finalizationMapClient,
                finalizationPriceClient);
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        UpdateWrapper<OrderInfo> recoveryUpdate = captureFinalizationUpdates(1).get(0);
        String normalizedSql = normalizedSqlSegment(recoveryUpdate.getSqlSegment());
        assertTrue(Pattern.compile("FINALIZATION_ATTEMPTS\\s*=").matcher(normalizedSql).find(), normalizedSql);
        assertFalse(Pattern.compile("FINALIZATION_ATTEMPTS\\s*>=").matcher(normalizedSql).find(), normalizedSql);
        assertWrapperContainsValue(recoveryUpdate, 3);
        assertFalse(recoveryUpdate.getParamNameValuePairs().containsValue(5),
                "Recovery CAS must use the persisted attempts value, not the configured maximum: "
                        + recoveryUpdate.getParamNameValuePairs());
        assertWrapperContainsValue(recoveryUpdate, 0);
    }

    @Test
    @DisplayName("Failed finalization recovery returns stable results when the CAS is lost")
    void shouldReturnStableResult_whenFailedRecoveryCasIsLost() {
        givenFinalizationClock();
        OrderInfo failedOrder = new OrderInfo();
        failedOrder.setId(ORDER_ID);
        failedOrder.setOrderStatus(OrderConstant.FINALIZATION_FAILED);
        failedOrder.setFinalizationAttempts(3);
        OrderInfo stillFailed = new OrderInfo();
        stillFailed.setId(ORDER_ID);
        stillFailed.setOrderStatus(OrderConstant.FINALIZATION_FAILED);
        stillFailed.setFinalizationAttempts(3);
        when(orderInfoMapper.selectOne(any())).thenReturn(failedOrder).thenReturn(stillFailed);
        when(orderInfoMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);

        ResponseResult stillFailedResult = orderInfoService.scheduleFailedFinalizationRecovery(ORDER_ID);

        assertEquals(CommonStatus.FINALIZATION_FAILED.getCode(), stillFailedResult.getCode());
        assertEquals(CommonStatus.FINALIZATION_FAILED.getMessage(), stillFailedResult.getMessage());

        reset(orderInfoMapper, serviceDriverUserClient, serviceMapClient, servicePriceClient);
        OrderInfo pendingAfterCasLoss = new OrderInfo();
        pendingAfterCasLoss.setId(ORDER_ID);
        pendingAfterCasLoss.setOrderStatus(OrderConstant.FINALIZATION_PENDING);
        pendingAfterCasLoss.setFinalizationAttempts(0);
        when(orderInfoMapper.selectOne(any())).thenReturn(failedOrder).thenReturn(pendingAfterCasLoss);
        when(orderInfoMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);

        ResponseResult scheduledResult = orderInfoService.scheduleFailedFinalizationRecovery(ORDER_ID);

        assertEquals(CommonStatus.FINALIZATION_RECOVERY_SCHEDULED.getCode(), scheduledResult.getCode());
        assertEquals(CommonStatus.FINALIZATION_RECOVERY_SCHEDULED.getMessage(), scheduledResult.getMessage());
        verifyNoInteractions(finalizationDriverUserClient, finalizationMapClient, finalizationPriceClient);
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
    }

    @Test
    @DisplayName("Direct retry remains rejected for failed finalization")
    void shouldKeepDirectRetryRejectedForFailedFinalization() {
        orderInfo.setOrderStatus(OrderConstant.FINALIZATION_FAILED);
        when(orderInfoMapper.selectOne(any())).thenReturn(orderInfo);

        ResponseResult result = orderInfoService.retryFinalization(ORDER_ID);

        assertEquals(CommonStatus.FINALIZATION_FAILED.getCode(), result.getCode());
        assertEquals(CommonStatus.FINALIZATION_FAILED.getMessage(), result.getMessage());
        verify(orderInfoMapper, never()).update(isNull(), any(UpdateWrapper.class));
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        verifyNoInteractions(finalizationDriverUserClient, finalizationMapClient, finalizationPriceClient);
    }

    @Test
    @DisplayName("Default finalization policy accepts lease greater than remote deadline budget")
    void shouldAcceptDefaultFinalizationPolicy_whenLeaseExceedsRemoteDeadlineBudget() {
        assertDoesNotThrow(() -> orderInfoService.validateFinalizationPolicy(
                3,
                30L,
                900L,
                120L,
                30000L,
                2000,
                10000,
                2000,
                30000,
                2000,
                10000));
    }

    @Test
    @DisplayName("Finalization policy rejects lease that does not exceed remote deadline budget")
    void shouldRejectFinalizationPolicy_whenLeaseDoesNotExceedRemoteDeadlineBudget() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> orderInfoService.validateFinalizationPolicy(
                        3,
                        30L,
                        900L,
                        80L,
                        30000L,
                        2000,
                        10000,
                        2000,
                        30000,
                        2000,
                        10000));

        assertFalse(exception.getMessage().contains("synthetic-secret"));
    }

    @Test
    @DisplayName("ADR documents exact persisted attempts for failed-finalization recovery CAS")
    void shouldDocumentExactPersistedAttemptsForFailedRecoveryCas() throws Exception {
        String adr = readRepositoryFile("docs/adr/0004-finalization-recovery-policy-and-deadline.md");
        String order04Verification = readRepositoryFile(
                "database/verification/ORDER-04__add_order_finalization_metadata.sql");

        assertTrue(adr.contains("Status 11 itself determines recovery eligibility"), adr);
        assertTrue(adr.contains("finalization_attempts = persisted currentAttempts"), adr);
        assertTrue(adr.contains("Raising or lowering the configured maximum attempt count"), adr);
        assertFalse(adr.contains("finalization_attempts >= " + "configured max attempts"), adr);

        assertTrue(order04Verification.contains("@order_finalization_max_attempts"), order04Verification);
        assertTrue(order04Verification.contains("COALESCE(@order_finalization_max_attempts, 3)"),
                order04Verification);
        assertTrue(order04Verification.contains("finalization_attempts = 0 is legal"), order04Verification);
        assertTrue(order04Verification.contains("finalization_attempts < 0"), order04Verification);
        assertFalse(order04Verification.contains("finalization_attempts <" + "= 0"), order04Verification);
        assertFalse(Pattern.compile("finalization_attempts\\s*(?:>=|>)\\s*3")
                .matcher(order04Verification).find(), order04Verification);
        assertTrue(order04Verification.contains("WHERE order_status = 10"), order04Verification);
        assertTrue(order04Verification.contains("finalization_attempts > COALESCE(@order_finalization_max_attempts, 3)"),
                order04Verification);
        assertTrue(order04Verification.contains("finalization_next_retry_at IS NULL"), order04Verification);
        assertTrue(order04Verification.contains("OR finalization_next_retry_at <= NOW()"), order04Verification);
    }

    @Test
    @DisplayName("READ-02 migration rejects zero candidates without explicit acknowledgement")
    void shouldGateRead02Migration_whenZeroCandidatesAreNotAcknowledged() throws Exception {
        String migration = readRepositoryFile("database/migrations/READ-02__normalize_order_drive_time_seconds.sql");
        String verification = readRepositoryFile("database/verification/READ-02__normalize_order_drive_time_seconds.sql");

        assertTrue(migration.contains("@read02_allow_zero_candidates"), migration);
        assertTrue(migration.contains("candidate_count = 0"), migration);
        assertTrue(migration.contains("COALESCE(@read02_allow_zero_candidates, 0) <> 1"), migration);
        assertTrue(migration.contains(
                "READ-02 found zero candidate rows without explicit operator acknowledgement"), migration);
        assertTrue(migration.contains("@read02_max_candidate_rows"), migration);
        assertTrue(migration.contains("candidate count exceeds the reviewed @read02_max_candidate_rows ceiling"),
                migration);
        assertTrue(migration.indexOf("@read02_max_candidate_rows")
                < migration.indexOf("CREATE TABLE IF NOT EXISTS read02_drive_time_seconds_audit"), migration);
        assertTrue(verification.contains("read02_candidate_ceiling_status"), verification);
        assertTrue(verification.contains("EXCEEDS_REVIEWED_MAX"), verification);
    }

    @Test
    @DisplayName("READ-02 migration gates cutover validity and existing audit cutover consistency")
    void shouldGateRead02Migration_onCutoverValidityAndConsistency() throws Exception {
        String migration = readRepositoryFile("database/migrations/READ-02__normalize_order_drive_time_seconds.sql");

        assertTrue(migration.contains("@read02_seconds_cutover > NOW()"), migration);
        assertTrue(migration.contains("database wall-clock semantics"), migration);
        assertTrue(migration.contains("seconds_cutover <> @read02_seconds_cutover"), migration);
        assertTrue(migration.contains(
                "READ-02 existing audit cutover does not match current @read02_seconds_cutover"), migration);
    }

    @Test
    @DisplayName("READ-02 migration allows same-cutover rerun only after target rows match audit")
    void shouldSupportSameCutoverCompletedRerun_inRead02Migration() throws Exception {
        String migration = readRepositoryFile("database/migrations/READ-02__normalize_order_drive_time_seconds.sql");

        assertTrue(migration.contains("read02_validate_target_rows"), migration);
        assertTrue(migration.contains("missing_audit_count"), migration);
        assertTrue(migration.contains("bad_normalization_count"), migration);
        assertTrue(migration.contains("current_value_mismatch_count"), migration);
        assertTrue(migration.contains("NOT (normalized_drive_time <=> original_drive_time * 60)"), migration);
        assertTrue(migration.contains("NOT (o.drive_time <=> a.normalized_drive_time)"), migration);
        assertFalse(migration.contains("@read02_candidate_count > 0 AND "
                + "@read02_rows_updated = 0"), migration);
    }

    @Test
    @DisplayName("READ-02 migration avoids blind column DDL and business timestamp rewrites")
    void shouldAvoidBlindColumnDefinitionAndBusinessTimestampChanges_inRead02Migration() throws Exception {
        String migration = readRepositoryFile("database/migrations/READ-02__normalize_order_drive_time_seconds.sql");
        String verification = readRepositoryFile("database/verification/READ-02__normalize_order_drive_time_seconds.sql");

        assertTrue(migration.contains("information_schema.columns"), migration);
        assertTrue(migration.contains("supports only signed BIGINT order_info.drive_time"), migration);
        assertTrue(migration.indexOf("information_schema.columns")
                < migration.indexOf("CREATE TABLE IF NOT EXISTS read02_drive_time_seconds_audit"), migration);
        assertTrue(migration.contains("completed pre-cutover rows with NULL drive_time"), migration);
        assertTrue(migration.contains("completed pre-cutover rows with negative drive_time"), migration);
        assertTrue(migration.contains("column_name IN ('gmt_create', 'gmt_modified')"), migration);
        assertTrue(migration.contains("o.gmt_create = o.gmt_create"), migration);
        assertTrue(migration.contains("o.gmt_modified = o.gmt_modified"), migration);
        assertTrue(verification.contains("read02_drive_time_column_status"), verification);
        assertTrue(verification.contains("completed_pre_cutover_rows_with_null_drive_time"), verification);
        assertTrue(verification.contains("completed_pre_cutover_rows_with_negative_drive_time"), verification);
        assertTrue(verification.contains("NOT (o.drive_time <=> a.normalized_drive_time)"), verification);
        assertTrue(verification.contains("read02_zero_minute_rows"), verification);
        assertTrue(verification.contains("gmt_create"), verification);
        assertTrue(verification.contains("gmt_modified"), verification);
        assertTrue(verification.contains("extra"), verification);
        assertTrue(verification.contains("ON UPDATE CURRENT_TIMESTAMP"), verification);
        assertTrue(verification.contains("self-assignment"), verification);
        assertFalse(verification.contains("read02_possible_double_" + "converted_rows"), verification);
        assertFalse(migration.contains("MODIFY COLUMN " + "drive_time"), migration);
        assertFalse(migration.contains("o.gmt_create = " + "NOW()"), migration);
        assertFalse(migration.contains("o.gmt_modified = " + "NOW()"), migration);
        assertFalse(migration.contains("o.gmt_create = " + "CURRENT_TIMESTAMP"), migration);
        assertFalse(migration.contains("o.gmt_modified = " + "CURRENT_TIMESTAMP"), migration);
        assertFalse(verification.contains("column_comment mentions seconds"), verification);
        assertFalse(verification.contains("column_comment"), verification);
    }

    @Test
    @DisplayName("READ-02 benchmark plan records candidate, write-cost, chunking, and timing evidence")
    void shouldTrackRead02BenchmarkEvidencePlan() throws Exception {
        String benchmark = readRepositoryFile("database/benchmark/READ-02__normalize_order_drive_time_seconds.md");

        assertTrue(benchmark.contains("candidate rows"), benchmark);
        assertTrue(benchmark.contains("status distribution"), benchmark);
        assertTrue(benchmark.contains("Audit Insert Cost"), benchmark);
        assertTrue(benchmark.contains("Join Update Cost"), benchmark);
        assertTrue(benchmark.contains("replication lag"), benchmark);
        assertTrue(benchmark.contains("@read02_max_candidate_rows"), benchmark);
        assertTrue(benchmark.contains("Single Statement vs Chunking Decision"), benchmark);
        assertTrue(benchmark.contains("Maintenance Window"), benchmark);
        assertTrue(benchmark.contains("Before and After Timing"), benchmark);
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
        verifyNoInteractions(finalizationDriverUserClient, finalizationMapClient, finalizationPriceClient);
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
        when(finalizationPriceClient.calculatePrice(5000, 600, "110000", "1"))
                .thenReturn(ResponseResult.success(19.00));

        ResponseResult result = orderInfoService.passengerGetoff(getoffRequest());

        assertEquals(CommonStatus.SUCCESS.getCode(), result.getCode());
        ArgumentCaptor<Long> endtimeCaptor = ArgumentCaptor.forClass(Long.class);
        verify(finalizationMapClient).trsearch(eq("tid-300"), anyLong(), endtimeCaptor.capture());
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
        verifyNoInteractions(finalizationDriverUserClient, finalizationMapClient, finalizationPriceClient);
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
        when(finalizationPriceClient.calculatePrice(5000, 600, "110000", "1"))
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
        when(finalizationDriverUserClient.getCarById(carId))
                .thenThrow(new RuntimeException("key=synthetic-secret"));

        ResponseResult result = orderInfoService.passengerGetoff(getoffRequest());

        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(), result.getCode());
        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage(), result.getMessage());
        assertFalse(result.getMessage().contains("synthetic-secret"));
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        verify(finalizationMapClient, never()).trsearch(anyString(), anyLong(), anyLong());
        verify(finalizationPriceClient, never()).calculatePrice(anyInt(), anyInt(), anyString(), anyString());
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
        when(finalizationMapClient.trsearch(eq("tid-300"), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("query=synthetic-secret"));

        ResponseResult result = orderInfoService.passengerGetoff(getoffRequest());

        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getCode(), result.getCode());
        assertEquals(CommonStatus.DOWNSTREAM_RESPONSE_ERROR.getMessage(), result.getMessage());
        assertFalse(result.getMessage().contains("synthetic-secret"));
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        verify(finalizationPriceClient, never()).calculatePrice(anyInt(), anyInt(), anyString(), anyString());
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
        when(finalizationPriceClient.calculatePrice(5000, 600, "110000", "1"))
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
    @DisplayName("Passenger cancellation at 59 seconds remains free")
    void shouldAllowFreePassengerCancellationAt59Seconds() {
        givenAcceptedOrderSecondsAgo(59);

        ResponseResult result = orderInfoService.cancel(ORDER_ID, PASSENGER);

        assertSuccessfulCancellation(result, OrderConstant.CANCEL_PASSENGER_BEFORE, PASSENGER);
    }

    @Test
    @DisplayName("Passenger cancellation at 60 seconds remains free")
    void shouldAllowFreePassengerCancellationAt60Seconds() {
        givenAcceptedOrderSecondsAgo(60);

        ResponseResult result = orderInfoService.cancel(ORDER_ID, PASSENGER);

        assertSuccessfulCancellation(result, OrderConstant.CANCEL_PASSENGER_BEFORE, PASSENGER);
    }

    @Test
    @DisplayName("Passenger cancellation at 119 seconds remains free")
    void shouldAllowFreePassengerCancellationAt119Seconds() {
        givenAcceptedOrderSecondsAgo(119);

        ResponseResult result = orderInfoService.cancel(ORDER_ID, PASSENGER);

        assertSuccessfulCancellation(result, OrderConstant.CANCEL_PASSENGER_BEFORE, PASSENGER);
    }

    @Test
    @DisplayName("Passenger cancellation at 120 seconds is penalized")
    void shouldPenalizePassengerCancellationAt120Seconds() {
        givenAcceptedOrderSecondsAgo(120);

        ResponseResult result = orderInfoService.cancel(ORDER_ID, PASSENGER);

        assertSuccessfulCancellation(result, OrderConstant.CANCEL_PASSENGER_ILLEGAL, PASSENGER);
    }

    @Test
    @DisplayName("Driver cancellation at 59 seconds remains free")
    void shouldAllowFreeDriverCancellationAt59Seconds() {
        givenAcceptedOrderSecondsAgo(59);

        ResponseResult result = orderInfoService.cancel(ORDER_ID, DRIVER);

        assertSuccessfulCancellation(result, OrderConstant.CANCEL_DRIVER_BEFORE, DRIVER);
    }

    @Test
    @DisplayName("Driver cancellation at 60 seconds remains free")
    void shouldAllowFreeDriverCancellationAt60Seconds() {
        givenAcceptedOrderSecondsAgo(60);

        ResponseResult result = orderInfoService.cancel(ORDER_ID, DRIVER);

        assertSuccessfulCancellation(result, OrderConstant.CANCEL_DRIVER_BEFORE, DRIVER);
    }

    @Test
    @DisplayName("Driver cancellation at 119 seconds remains free")
    void shouldAllowFreeDriverCancellationAt119Seconds() {
        givenAcceptedOrderSecondsAgo(119);

        ResponseResult result = orderInfoService.cancel(ORDER_ID, DRIVER);

        assertSuccessfulCancellation(result, OrderConstant.CANCEL_DRIVER_BEFORE, DRIVER);
    }

    @Test
    @DisplayName("Driver cancellation at 120 seconds is penalized")
    void shouldPenalizeDriverCancellationAt120Seconds() {
        givenAcceptedOrderSecondsAgo(120);

        ResponseResult result = orderInfoService.cancel(ORDER_ID, DRIVER);

        assertSuccessfulCancellation(result, OrderConstant.CANCEL_DRIVER_ILLEGAL, DRIVER);
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
