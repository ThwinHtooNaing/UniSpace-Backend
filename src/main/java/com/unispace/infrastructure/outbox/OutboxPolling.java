package com.unispace.infrastructure.outbox;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Profile("!test")
class OutboxPolling {
    private static final int MAX_EVENTS_PER_POLL = 20;
    private final OutboxWorker worker;

    OutboxPolling(OutboxWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelay = 5_000, initialDelay = 5_000)
    void poll() {
        for (int processed = 0; processed < MAX_EVENTS_PER_POLL; processed++) {
            if (!worker.processNext()) return;
        }
    }
}
