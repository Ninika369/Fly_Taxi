package com.george.serviceorder.job;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.george.internalCommon.constant.OrderConstant;
import com.george.internalCommon.dto.Car;
import com.george.internalCommon.dto.OrderInfo;
import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.response.TrsearchResponse;
import com.george.serviceorder.mapper.OrderInfoMapper;
import com.george.serviceorder.remote.ServiceDriverUserClient;
import com.george.serviceorder.remote.ServiceMapClient;
import com.george.serviceorder.remote.ServicePriceClient;
import com.george.serviceorder.remote.ServiceSsePushClient;
import com.george.serviceorder.service.OrderInfoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderFinalizationRetryJobTest {

    private static final Instant FIXED_TRACE_END = Instant.parse("2026-07-28T01:02:03Z");
    private static final ZoneId TEST_ZONE = ZoneId.of("Pacific/Auckland");

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
    RedissonClient redissonClient;

    @InjectMocks
    OrderInfoService orderInfoService;

    @Test
    @DisplayName("Finalization retry scan continues after one unexpected failure")
    void shouldRetryDueOrdersAndContinueAfterOneUnexpectedFailure() {
        ReflectionTestUtils.setField(orderInfoService, "clock", Clock.fixed(FIXED_TRACE_END, TEST_ZONE));
        LocalDateTime now = LocalDateTime.now(Clock.fixed(FIXED_TRACE_END, TEST_ZONE));
        OrderInfo firstDueOrder = pendingOrder(100L, 301L);
        OrderInfo secondDueOrder = pendingOrder(101L, 302L);
        when(orderInfoMapper.selectList(any())).thenReturn(Arrays.asList(firstDueOrder, secondDueOrder));
        when(orderInfoMapper.selectOne(any())).thenReturn(firstDueOrder).thenReturn(secondDueOrder);
        when(orderInfoMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1).thenReturn(1);
        when(serviceDriverUserClient.getCarById(301L)).thenThrow(new IllegalStateException("synthetic failure"));

        Car car = new Car();
        car.setTid("tid-302");
        when(serviceDriverUserClient.getCarById(302L)).thenReturn(ResponseResult.success(car));
        TrsearchResponse trsearchResponse = new TrsearchResponse();
        trsearchResponse.setDriveMile(5000L);
        trsearchResponse.setDriveTime(600L);
        when(serviceMapClient.trsearch(eq("tid-302"), anyLong(), anyLong()))
                .thenReturn(ResponseResult.success(trsearchResponse));
        when(servicePriceClient.calculatePrice(5000, 600, "110000", "1"))
                .thenReturn(ResponseResult.success(19.00));

        int processed = orderInfoService.retryDueFinalizations(now, 50);

        assertEquals(1, processed);
        verify(orderInfoMapper, times(2)).selectOne(any());
        verify(serviceDriverUserClient, times(2)).getCarById(anyLong());
        verify(servicePriceClient, times(1)).calculatePrice(anyInt(), anyInt(), anyString(), anyString());
        verify(orderInfoMapper, times(1)).updateById(secondDueOrder);
    }

    private OrderInfo pendingOrder(Long orderId, Long carId) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(orderId);
        orderInfo.setOrderStatus(OrderConstant.FINALIZATION_PENDING);
        orderInfo.setFinalizationAttempts(1);
        orderInfo.setFinalizationNextRetryAt(LocalDateTime.now(Clock.fixed(FIXED_TRACE_END, TEST_ZONE)).minusSeconds(1));
        orderInfo.setFinalizationTraceEndEpochMs(FIXED_TRACE_END.toEpochMilli());
        orderInfo.setPassengerGetoffTime(LocalDateTime.now(Clock.fixed(FIXED_TRACE_END, TEST_ZONE)).minusMinutes(5));
        orderInfo.setPickUpPassengerTime(LocalDateTime.now(Clock.fixed(FIXED_TRACE_END, TEST_ZONE)).minusMinutes(15));
        orderInfo.setCarId(carId);
        orderInfo.setAddress("110000");
        orderInfo.setVehicleType("1");
        return orderInfo;
    }
}
