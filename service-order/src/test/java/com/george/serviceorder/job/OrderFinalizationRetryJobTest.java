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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
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
        LocalDateTime now = LocalDateTime.now(Clock.fixed(FIXED_TRACE_END, TEST_ZONE));
        OrderInfo firstDueOrder = pendingOrder(100L, 301L);
        OrderInfo secondDueOrder = pendingOrder(101L, 302L);
        when(orderInfoMapper.selectList(any())).thenReturn(Arrays.asList(firstDueOrder, secondDueOrder));
        OrderInfoService spyService = spy(orderInfoService);
        doThrow(new IllegalStateException("synthetic unexpected failure"))
                .doReturn(ResponseResult.success())
                .when(spyService).retryFinalization(anyLong());

        int processed = spyService.retryDueFinalizations(now, 50);

        assertEquals(1, processed);
        verify(spyService, times(1)).retryFinalization(100L);
        verify(spyService, times(1)).retryFinalization(101L);
        verify(orderInfoMapper, never()).update(isNull(), any(UpdateWrapper.class));
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
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
