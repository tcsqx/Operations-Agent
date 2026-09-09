package org.example.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AlertDeduplicator {

    private static final Logger log = LoggerFactory.getLogger(AlertDeduplicator.class);
    private static final long DEDUP_WINDOW_MS = 5 * 60 * 1000; // 5 minutes

    private final Map<String, Long> recentAlerts = new ConcurrentHashMap<>();

    /**
     * Checks if an alert is a duplicate within the deduplication window.
     * Returns true if it is a duplicate (should be suppressed).
     * Returns false if it is novel (should be processed).
     */
    public boolean isDuplicate(String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) {
            return false;
        }

        long now = System.currentTimeMillis();

        // Clean up expired entries occasionally
        if (recentAlerts.size() > 1000) {
            recentAlerts.entrySet().removeIf(entry -> now - entry.getValue() > DEDUP_WINDOW_MS);
        }

        Long lastSeen = recentAlerts.get(fingerprint);
        if (lastSeen != null && (now - lastSeen) < DEDUP_WINDOW_MS) {
            log.info("[AlertDeduplicator] Suppressed duplicate alert with fingerprint: {}", fingerprint);
            return true;
        }

        recentAlerts.put(fingerprint, now);
        return false;
    }
}
