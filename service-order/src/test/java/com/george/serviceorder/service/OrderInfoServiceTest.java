package com.george.serviceorder.service;

import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.constant.OrderConstant;
import com.george.internalCommon.constant.UserIdentity;
import com.george.internalCommon.dto.OrderInfo;
import com.george.internalCommon.dto.ResponseResult;
import com.george.serviceorder.mapper.OrderInfoMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

// ---- Mockito imports ----
import org.mockito.InjectMocks;    // Tells Mockito: "create this object and inject mocks into it"
import org.mockito.Mock;            // Tells Mockito: "create a fake version of this"
import org.mockito.junit.jupiter.MockitoExtension;  // Activates Mockito for JUnit 5

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;   // when(), verify(), any(), etc.

import java.time.LocalDateTime;

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

    // The REAL service — but with the fake mapper injected into it.
    // Mockito sees the @Autowired OrderInfoMapper field inside OrderInfoService
    // and plugs in our @Mock automatically.
    @InjectMocks
    OrderInfoService orderInfoService;

    // Reusable test data
    private static final Long ORDER_ID = 100L;
    private static final String PASSENGER = UserIdentity.PASSENGER.getIdentity(); // "1"
    private static final String DRIVER = UserIdentity.DRIVER.getIdentity();       // "2"

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
}
