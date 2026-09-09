package org.example.diagnosis;

import java.time.LocalDateTime;

public class Evidence {
    private final String toolName;
    private final String observation;
    private final String rawDetails;
    private final boolean anomaly;
    private final LocalDateTime timestamp;

    public Evidence(String toolName, String observation, String rawDetails, boolean anomaly) {
        this.toolName = toolName;
        this.observation = observation;
        this.rawDetails = rawDetails;
        this.anomaly = anomaly;
        this.timestamp = LocalDateTime.now();
    }

    public String getToolName() {
        return toolName;
    }

    public String getObservation() {
        return observation;
    }

    public String getRawDetails() {
        return rawDetails;
    }

    public boolean isAnomaly() {
        return anomaly;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "[" + toolName + "] " + (anomaly ? "⚠️ ANOMALY: " : "ℹ️ ") + observation;
    }
}
