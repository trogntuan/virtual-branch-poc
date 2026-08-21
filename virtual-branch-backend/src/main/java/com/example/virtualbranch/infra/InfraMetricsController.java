package com.example.virtualbranch.infra;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local-only helpers for sizing LiveKit SFU + Egress (process/docker/prometheus snapshots).
 * Intended for POC measurement UI — not a production metrics stack.
 */
@RestController
@RequestMapping("/api/v1/infra")
public class InfraMetricsController {

    private static final List<String> CONTAINERS = List.of(
            "vb-egress",
            "vb-minio",
            "vb-redis",
            "vb-postgres"
    );

    private static final Pattern PROM_LINE = Pattern.compile(
            "^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\\{[^}]*})?\\s+([-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?)"
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final String livekitPrometheusUrl;

    public InfraMetricsController(
            @Value("${virtual-branch.infra.livekit-prometheus-url:http://127.0.0.1:6789/metrics}")
            String livekitPrometheusUrl
    ) {
        this.livekitPrometheusUrl = livekitPrometheusUrl;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("livekit", sampleLiveKitSfu());
        body.put("containers", sampleDockerStats());
        body.put("note", "SFU = host livekit-server process + prometheus; containers = docker stats.");
        return body;
    }

    private Map<String, Object> sampleLiveKitSfu() {
        Map<String, Object> sfu = new LinkedHashMap<>();
        sfu.put("name", "livekit-server");
        sfu.putAll(sampleLiveKitProcess());
        sfu.putAll(sampleLiveKitPrometheus());
        return sfu;
    }

    private Map<String, Object> sampleLiveKitProcess() {
        Map<String, Object> row = new LinkedHashMap<>();
        try {
            ProcessBuilder find = new ProcessBuilder("pgrep", "-x", "livekit-server");
            find.redirectErrorStream(true);
            Process findProc = find.start();
            boolean findDone = findProc.waitFor(2, TimeUnit.SECONDS);
            String pidLine;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(findProc.getInputStream(), StandardCharsets.UTF_8))) {
                pidLine = reader.readLine();
            }
            if (!findDone || pidLine == null || pidLine.isBlank()) {
                row.put("error", "livekit-server process not found");
                return row;
            }
            String pid = pidLine.trim().split("\\s+")[0];
            row.put("pid", Integer.parseInt(pid));

            ProcessBuilder ps = new ProcessBuilder(
                    "ps",
                    "-p",
                    pid,
                    "-o",
                    "%cpu=,%mem=,rss=,vsz=,etime="
            );
            ps.redirectErrorStream(true);
            Process psProc = ps.start();
            boolean psDone = psProc.waitFor(2, TimeUnit.SECONDS);
            String statsLine;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(psProc.getInputStream(), StandardCharsets.UTF_8))) {
                statsLine = reader.readLine();
            }
            if (!psDone || statsLine == null || statsLine.isBlank()) {
                row.put("error", "ps failed for livekit-server");
                return row;
            }
            String[] parts = statsLine.trim().split("\\s+");
            if (parts.length >= 5) {
                row.put("cpuPercent", Double.parseDouble(parts[0]));
                row.put("memPercent", Double.parseDouble(parts[1]));
                long rssKb = Long.parseLong(parts[2]);
                long vszKb = Long.parseLong(parts[3]);
                row.put("rssMb", round1(rssKb / 1024.0));
                row.put("vszMb", round1(vszKb / 1024.0));
                row.put("memUsage", formatMb(rssKb / 1024.0));
                row.put("elapsed", parts[4]);
            }
        } catch (Exception e) {
            row.put("error", e.getMessage());
        }
        return row;
    }

    private Map<String, Object> sampleLiveKitPrometheus() {
        Map<String, Object> prom = new LinkedHashMap<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(livekitPrometheusUrl))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                prom.put("prometheusError", "HTTP " + response.statusCode());
                return prom;
            }
            Map<String, Double> gauges = parsePrometheusGauges(response.body());
            prom.put("rooms", firstMetric(gauges,
                    "livekit_room_total",
                    "livekit_room",
                    "livekit_node_rooms"));
            prom.put("participants", firstMetric(gauges,
                    "livekit_participant_total",
                    "livekit_participant",
                    "livekit_node_participants"));
            Double packetsIn = sumMatching(gauges, "livekit_node_packet_total", "type", "in");
            Double packetsOut = sumMatching(gauges, "livekit_node_packet_total", "type", "out");
            Double packetsDropped = sumMatching(gauges, "livekit_node_packet_total", "type", "dropped");
            if (packetsIn != null || packetsOut != null) {
                prom.put("packetsIn", packetsIn);
                prom.put("packetsOut", packetsOut);
                prom.put("packetsDropped", packetsDropped);
            }
            Double bytesInDir = sumMatching(gauges, "livekit_node_packet_bytes", "direction", "in");
            Double bytesOutDir = sumMatching(gauges, "livekit_node_packet_bytes", "direction", "out");
            if (bytesInDir == null) {
                bytesInDir = sumMatching(gauges, "livekit_node_packet_bytes_total", "type", "in");
            }
            if (bytesOutDir == null) {
                bytesOutDir = sumMatching(gauges, "livekit_node_packet_bytes_total", "type", "out");
            }
            if (bytesInDir != null || bytesOutDir != null) {
                prom.put("bytesIn", bytesInDir);
                prom.put("bytesOut", bytesOutDir);
                prom.put("bytesInHuman", bytesInDir == null ? null : formatBytes(bytesInDir));
                prom.put("bytesOutHuman", bytesOutDir == null ? null : formatBytes(bytesOutDir));
            }
            prom.put("prometheusOk", true);
        } catch (Exception e) {
            prom.put("prometheusError", e.getMessage());
            prom.put("prometheusOk", false);
        }
        return prom;
    }

    private static Map<String, Double> parsePrometheusGauges(String body) {
        Map<String, Double> values = new LinkedHashMap<>();
        Map<String, Double> sums = new LinkedHashMap<>();
        for (String raw : body.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Matcher matcher = PROM_LINE.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            String name = matcher.group(1);
            double value = Double.parseDouble(matcher.group(2));
            // Keep last unlabeled / any series; also accumulate sums for labeled families
            values.put(name, value);
            sums.merge(name, value, Double::sum);
            // Store label-aware keys for packet bytes
            if (line.contains("{") && line.contains("}")) {
                int start = line.indexOf('{');
                int end = line.indexOf('}');
                if (start > 0 && end > start) {
                    String labels = line.substring(start + 1, end);
                    values.put(name + "|" + labels, value);
                }
            }
        }
        // Prefer summed totals for multi-series room/participant gauges when only labeled series exist
        for (Map.Entry<String, Double> e : sums.entrySet()) {
            values.putIfAbsent(e.getKey() + "__sum", e.getValue());
        }
        return values;
    }

    private static Double firstMetric(Map<String, Double> gauges, String... names) {
        for (String name : names) {
            if (gauges.containsKey(name)) {
                return gauges.get(name);
            }
            if (gauges.containsKey(name + "__sum")) {
                return gauges.get(name + "__sum");
            }
        }
        return null;
    }

    private static Double sumMatching(
            Map<String, Double> gauges,
            String namePrefix,
            String labelKey,
            String labelValue
    ) {
        double sum = 0;
        boolean found = false;
        String needle = labelKey + "=\"" + labelValue + "\"";
        for (Map.Entry<String, Double> e : gauges.entrySet()) {
            String key = e.getKey();
            if (!key.startsWith(namePrefix + "|")) {
                continue;
            }
            if (key.contains(needle)) {
                sum += e.getValue();
                found = true;
            }
        }
        return found ? sum : null;
    }

    private List<Map<String, Object>> sampleDockerStats() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (String name : CONTAINERS) {
            Map<String, Object> placeholder = new LinkedHashMap<>();
            placeholder.put("name", name);
            byName.put(name, placeholder);
        }

        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("docker");
            cmd.add("stats");
            cmd.add("--no-stream");
            cmd.add("--format");
            cmd.add("{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}|{{.MemPerc}}|{{.NetIO}}|{{.BlockIO}}");
            cmd.addAll(CONTAINERS);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(8, TimeUnit.SECONDS);
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            if (!finished) {
                process.destroyForcibly();
                for (Map<String, Object> row : byName.values()) {
                    row.put("error", "docker stats timeout");
                }
            } else {
                for (String line : lines) {
                    if (line == null || line.isBlank()) {
                        continue;
                    }
                    String lower = line.toLowerCase(Locale.ROOT);
                    if (lower.contains("no such") || lower.contains("error")) {
                        continue;
                    }
                    String[] parts = line.trim().split("\\|");
                    if (parts.length < 6) {
                        continue;
                    }
                    String name = parts[0].trim();
                    Map<String, Object> row = byName.get(name);
                    if (row == null) {
                        continue;
                    }
                    row.put("cpuPercent", parsePercent(parts[1]));
                    row.put("memUsage", parts[2].trim());
                    row.put("memPercent", parsePercent(parts[3]));
                    row.put("netIO", parts[4].trim());
                    row.put("blockIO", parts[5].trim());
                    row.put("raw", line.trim());
                }
            }
        } catch (Exception e) {
            for (Map<String, Object> row : byName.values()) {
                row.putIfAbsent("error", e.getMessage());
            }
        }

        rows.addAll(byName.values());
        return rows;
    }

    private static Double parsePercent(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim().replace("%", "");
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String formatMb(double mb) {
        if (mb >= 1024) {
            return String.format(Locale.US, "%.2fGiB", mb / 1024.0);
        }
        return String.format(Locale.US, "%.0fMiB", mb);
    }

    private static String formatBytes(double bytes) {
        if (bytes >= 1024d * 1024d * 1024d) {
            return String.format(Locale.US, "%.2f GiB", bytes / (1024d * 1024d * 1024d));
        }
        if (bytes >= 1024d * 1024d) {
            return String.format(Locale.US, "%.1f MiB", bytes / (1024d * 1024d));
        }
        if (bytes >= 1024d) {
            return String.format(Locale.US, "%.1f KiB", bytes / 1024d);
        }
        return String.format(Locale.US, "%.0f B", bytes);
    }
}
