package com.george.serviceorder.job;

import com.george.serviceorder.service.OrderInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class OrderFinalizationRetryJob {

    @Autowired
    private OrderInfoService orderInfoService;

    @Scheduled(fixedDelayString = "${order.finalization.retry-scan-delay-ms:30000}")
    public void retryDueFinalizations() {
        try {
            orderInfoService.retryDueFinalizations(LocalDateTime.now());
        } catch (RuntimeException e) {
            log.warn("Order finalization retry scan failed; exceptionType={}",
                    e.getClass().getSimpleName());
        }
    }
}
