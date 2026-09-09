package org.example.diagnosis;

import java.util.ArrayList;
import java.util.List;

public class Hypothesis {
    private final String name;
    private final String rootCause;
    private double confidenceScore; // 0.0 to 1.0
    private final List<String> supportingEvidences = new ArrayList<>();
    private final List<String> suggestedMitigations = new ArrayList<>();

    public Hypothesis(String name, String rootCause, double confidenceScore) {
        this.name = name;
        this.rootCause = rootCause;
        this.confidenceScore = confidenceScore;
    }

    public void addEvidence(String evidence) {
        this.supportingEvidences.add(evidence);
    }

    public void addMitigation(String mitigation) {
        this.suggestedMitigations.add(mitigation);
    }

    public void adjustConfidence(double delta) {
        this.confidenceScore = Math.max(0.0, Math.min(1.0, this.confidenceScore + delta));
    }

    public String getName() {
        return name;
    }

    public String getRootCause() {
        return rootCause;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public List<String> getSupportingEvidences() {
        return supportingEvidences;
    }

    public List<String> getSuggestedMitigations() {
        return suggestedMitigations;
    }

    @Override
    public String toString() {
        return String.format("%s (Confidence: %.0f%%) - %s", name, confidenceScore * 100, rootCause);
    }
}
