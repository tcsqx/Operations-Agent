package org.example.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertDeduplicatorTest {

    private AlertDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        deduplicator = new AlertDeduplicator();
    }

    @Test
    @DisplayName("Should detect and suppress duplicate alerts within window")
    void testDeduplicateWithinWindow() {
        String fp = "NodeHighCpu_node-1";

        // First occurrence is novel
        assertFalse(deduplicator.isDuplicate(fp));

        // Subsequent occurrences within 5 minutes are duplicates
        assertTrue(deduplicator.isDuplicate(fp));
        assertTrue(deduplicator.isDuplicate(fp));

        // Different alert is novel
        assertFalse(deduplicator.isDuplicate("NodeHighMemory_node-1"));
    }
}
