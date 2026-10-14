package com.pramaan.backend.insight;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Warms the evidence-completeness scoring in the background once the application is ready, so the first dashboard view
 * after a (re)start does not pay the cold cost of verifying every stored version. Never blocks startup and never fails
 * it: an error is only logged. Disable with {@code pramaan.insight.completeness-warmup=false}.
 */
@Component
public class CompletenessWarmup {

    private static final Logger log = LoggerFactory.getLogger(CompletenessWarmup.class);

    private final EvidenceCompletenessService completeness;
    private final boolean enabled;

    public CompletenessWarmup(EvidenceCompletenessService completeness,
                              @Value("${pramaan.insight.completeness-warmup:true}") boolean enabled) {
        this.completeness = completeness;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!enabled) {
            return;
        }
        Thread t = new Thread(this::warm, "completeness-warmup");
        t.setDaemon(true);
        t.start();
    }

    /** Runs through the service proxy, so it gets its own read-only transaction/session. */
    void warm() {
        long started = System.nanoTime();
        try {
            int items = completeness.forScope(null, null).totalItems();
            completeness.frameworkRollup(null);
            log.info("completeness warm-up: {} evidence items scored in {} ms", items,
                    (System.nanoTime() - started) / 1_000_000);
        } catch (RuntimeException e) {
            log.warn("completeness warm-up failed after {} ms: {}", (System.nanoTime() - started) / 1_000_000,
                    e.getMessage());
        }
    }
}
