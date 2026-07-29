package com.george.internalCommon.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.george.internalCommon.constant.OrderConstant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderInfoSerializationTest {

    @Test
    @DisplayName("OrderInfo JSON excludes internal finalization recovery metadata")
    void shouldExcludeFinalizationMetadata_whenSerializingOrderInfo() throws Exception {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(100L);
        orderInfo.setOrderStatus(OrderConstant.FINALIZATION_PENDING);
        orderInfo.setFinalizationAttempts(2);
        orderInfo.setFinalizationNextRetryAt(LocalDateTime.of(2026, 7, 29, 12, 0));
        orderInfo.setFinalizationLastError("1700:Downstream service returned an invalid response");
        orderInfo.setFinalizationTraceEndEpochMs(1785312000000L);

        String json = new ObjectMapper().writeValueAsString(orderInfo);

        assertTrue(json.contains("\"id\":100"));
        assertTrue(json.contains("\"orderStatus\":10"));
        assertFalse(json.contains("finalizationAttempts"));
        assertFalse(json.contains("finalizationNextRetryAt"));
        assertFalse(json.contains("finalizationLastError"));
        assertFalse(json.contains("finalizationTraceEndEpochMs"));
    }
}
