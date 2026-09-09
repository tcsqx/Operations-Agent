package org.example.security;

import org.example.model.enums.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RiskClassifierTest {

    private RiskClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new RiskClassifier();
    }

    @Test
    @DisplayName("Should classify read-only inspection tools as LOW risk")
    void testLowRiskTools() {
        assertEquals(RiskLevel.LOW, classifier.classify("server_info", null));
        assertEquals(RiskLevel.LOW, classifier.classify("cpu_inspector", null));
        assertEquals(RiskLevel.LOW, classifier.classify("memory_inspector", null));
        assertEquals(RiskLevel.LOW, classifier.classify("disk_usage", null));
        assertEquals(RiskLevel.LOW, classifier.classify("port_check", null));
        assertEquals(RiskLevel.LOW, classifier.classify("system_log_search", null));
    }

    @Test
    @DisplayName("Should classify mutating operations as HIGH risk")
    void testHighRiskTools() {
        assertEquals(RiskLevel.HIGH, classifier.classify("service_restart", null));
        assertEquals(RiskLevel.HIGH, classifier.classify("docker_restart", null));
        assertEquals(RiskLevel.HIGH, classifier.classify("process_kill", null));
    }

    @Test
    @DisplayName("Should dynamically classify shell commands by content")
    void testDynamicShellRisk() {
        assertEquals(RiskLevel.LOW, classifier.classify("safe_shell", "ps aux | grep java"));
        assertEquals(RiskLevel.HIGH, classifier.classify("safe_shell", "systemctl restart docker"));
        assertEquals(RiskLevel.CRITICAL, classifier.classify("safe_shell", "rm -rf /var/log/*"));
    }
}
