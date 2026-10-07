package org.example.tools;

import org.example.model.enums.RiskLevel;
import org.example.security.DataMasker;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component("system_log_search")
public class SystemLogSearchTool implements BaseTool {

    private final DataMasker dataMasker;

    public SystemLogSearchTool(DataMasker dataMasker) {
        this.dataMasker = dataMasker;
    }

    @Override
    public String getName() {
        return "system_log_search";
    }

    @Override
    public String getDescription() {
        return "Search recent lines of a log file for specific error keywords or patterns. Parameters: 'file_path' (required), 'keyword' (optional, e.g. 'ERROR'), 'max_lines' (optional, default 50).";
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.LOW;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        long start = System.currentTimeMillis();
        if (params == null || !params.containsKey("file_path")) {
            return ToolResult.failure("Missing required parameter 'file_path'", 0);
        }

        String filePath = params.get("file_path").toString().trim();
        String keyword = params.containsKey("keyword") ? params.get("keyword").toString().toLowerCase() : "";
        int maxLines = 50;
        if (params.containsKey("max_lines")) {
            try {
                maxLines = Math.min(200, Integer.parseInt(params.get("max_lines").toString()));
            } catch (Exception ignored) {
            }
        }

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            return ToolResult.failure("Log file does not exist or is not a regular file: " + filePath, System.currentTimeMillis() - start);
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long length = raf.length();
            long maxScanBytes = 2 * 1024 * 1024; // Scan at most 2MB backwards
            long startPos = Math.max(0, length - maxScanBytes);
            int bytesToRead = (int) (length - startPos);

            byte[] buffer = new byte[bytesToRead];
            raf.seek(startPos);
            raf.readFully(buffer);

            String tailText = new String(buffer, StandardCharsets.UTF_8);
            String[] lines = tailText.split("\r?\n");
            int minIndex = (startPos > 0 && lines.length > 1) ? 1 : 0;

            List<String> matchedLines = new ArrayList<>();
            for (int i = lines.length - 1; i >= minIndex && matchedLines.size() < maxLines; i--) {
                String line = lines[i].trim();
                if (!line.isEmpty()) {
                    if (keyword.isEmpty() || line.toLowerCase().contains(keyword)) {
                        matchedLines.add(line);
                    }
                }
            }

            StringBuilder output = new StringBuilder("Found " + matchedLines.size() + " matching log entries in " + file.getName() + ":\n");
            // Reverse to restore chronological order
            for (int i = matchedLines.size() - 1; i >= 0; i--) {
                output.append(dataMasker.mask(matchedLines.get(i))).append("\n");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("file", file.getName());
            data.put("matches_count", matchedLines.size());

            return ToolResult.success(output.toString().trim(), System.currentTimeMillis() - start, data);
        } catch (Exception e) {
            return ToolResult.failure("Error reading log file: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
