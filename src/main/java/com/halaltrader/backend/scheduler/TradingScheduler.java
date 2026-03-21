package com.halaltrader.backend.scheduler;

import com.halaltrader.backend.service.TradingOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TradingScheduler {

    private static final Logger log = LoggerFactory.getLogger(TradingScheduler.class);

    private final TradingOrchestrator orchestrator;

    public TradingScheduler(TradingOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(cron = "${trading.scheduler.cron}")
    public void scheduledCycle() {
        log.info("[TradingScheduler] Cron triggered — starting trading cycle");
        orchestrator.run();
    }
}
