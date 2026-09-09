package org.example.tools;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ToolResult {
    private final boolean success;
    private final String output;
    private final String error;
    private final long costMs;
    private final Map<String, Object> data;

    public ToolResult(boolean success, String output, String error, long costMs, Map<String, Object> data) {
        this.success = success;
        this.output = output;
        this.error = error;
        this.costMs = costMs;
        this.data = data != null ? data : Collections.emptyMap();
    }

    public static ToolResult success(String output, long costMs) {
        return new ToolResult(true, output, null, costMs, new HashMap<>());
    }

    public static ToolResult success(String output, long costMs, Map<String, Object> data) {
        return new ToolResult(true, output, null, costMs, data);
    }

    public static ToolResult failure(String error, long costMs) {
        return new ToolResult(false, null, error, costMs, new HashMap<>());
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOutput() {
        return output;
    }

    public String getError() {
        return error;
    }

    public long getCostMs() {
        return costMs;
    }

    public Map<String, Object> getData() {
        return data;
    }

    @Override
    public String toString() {
        return "ToolResult{" +
            "success=" + success +
            ", output='" + (output != null && output.length() > 200 ? output.substring(0, 200) + "..." : output) + '\'' +
            ", error='" + error + '\'' +
            ", costMs=" + costMs +
            '}';
    }
}
