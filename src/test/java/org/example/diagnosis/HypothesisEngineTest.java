package org.example.diagnosis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HypothesisEngineTest {

    private HypothesisEngine hypothesisEngine;

    @BeforeEach
    void setUp() {
        hypothesisEngine = new HypothesisEngine();
    }

    @Test
    @DisplayName("Should generate OutOfMemoryError hypothesis when memory anomaly and OOM log coexist")
    void testOOMDiagnosis() {
        List<Evidence> evidences = new ArrayList<>();
        evidences.add(new Evidence("memory_inspector", "Physical RAM: 95% used, CRITICAL: Physical Memory >90%", "detail", true));
        evidences.add(new Evidence("system_log_search", "java.lang.OutOfMemoryError: Java heap space", "stacktrace", true));

        List<Hypothesis> hypotheses = hypothesisEngine.analyze(evidences);
        assertFalse(hypotheses.isEmpty());

        Hypothesis top = hypotheses.get(0);
        assertTrue(top.getName().contains("OutOfMemoryError"));
        assertTrue(top.getConfidenceScore() >= 0.90);
        assertFalse(top.getSuggestedMitigations().isEmpty());
    }

    @Test
    @DisplayName("Should generate Disk Storage Exhaustion hypothesis when disk exceeds 90%")
    void testDiskFullDiagnosis() {
        List<Evidence> evidences = new ArrayList<>();
        evidences.add(new Evidence("disk_usage", "Mount '/': Total 100 GB, Used 96 GB (96.0%) [CRITICAL > 90%]", "detail", true));

        List<Hypothesis> hypotheses = hypothesisEngine.analyze(evidences);
        assertFalse(hypotheses.isEmpty());

        Hypothesis top = hypotheses.get(0);
        assertTrue(top.getName().contains("Disk"));
        assertTrue(top.getConfidenceScore() >= 0.85);
        assertTrue(top.getSuggestedMitigations().get(0).contains("log"));
    }

    @Test
    @DisplayName("Should conclude healthy when all probes report nominal metrics")
    void testNominalHealthyDiagnosis() {
        List<Evidence> evidences = new ArrayList<>();
        evidences.add(new Evidence("server_info", "OS: Linux, Hostname: prod-01", "nominal", false));
        evidences.add(new Evidence("cpu_inspector", "CPU Status: HEALTHY, Load: 15%", "nominal", false));
        evidences.add(new Evidence("memory_inspector", "Memory Status: HEALTHY, Usage: 45%", "nominal", false));

        List<Hypothesis> hypotheses = hypothesisEngine.analyze(evidences);
        assertFalse(hypotheses.isEmpty());

        Hypothesis top = hypotheses.get(0);
        assertTrue(top.getName().contains("Healthy"));
    }
}
