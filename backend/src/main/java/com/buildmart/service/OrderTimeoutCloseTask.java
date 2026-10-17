package com.buildmart.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 超时未支付订单自动关单。支付超时时长可通过系统设置 `order.auto_cancel_minutes`
 * 调整（默认 30 分钟），扫描间隔默认 60 秒。
 */
@Component
public class OrderTimeoutCloseTask {
    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutCloseTask.class);
    private static final int BATCH_SIZE = 200;

    private final MiniappOrderService orderService;
    private final SettingService settingService;

    public OrderTimeoutCloseTask(MiniappOrderService orderService, SettingService settingService) {
        this.orderService = orderService;
        this.settingService = settingService;
    }

    @Scheduled(fixedDelayString = "${app.order.close-scan-interval-ms:60000}", initialDelay = 60_000)
    public void closeTimeoutOrders() {
        try {
            int timeoutMinutes = Integer.parseInt(settingService.get("order.auto_cancel_minutes", "30"));
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1, timeoutMinutes));
            int closed = orderService.closeTimeoutOrders(cutoff, BATCH_SIZE);
            if (closed > 0) {
                log.info("超时关单任务完成，关闭 {} 笔订单，超时阈值 {} 分钟", closed, timeoutMinutes);
            }
        } catch (Exception e) {
            log.error("超时关单任务执行失败", e);
        }
    }
}
