package com.cohortlens.dev;

import com.cohortlens.core.AnalyticsService;
import com.cohortlens.core.AnalyticsService.Filter;
import com.cohortlens.core.AnalyticsService.GapDimension;
import com.cohortlens.core.AuditEntry;
import com.cohortlens.core.CsvExporter;
import com.cohortlens.core.EconomicStatus;
import com.cohortlens.core.ImportMerger;
import com.cohortlens.core.ImportProcessor;
import com.cohortlens.core.ImportResult;
import com.cohortlens.core.ImportSummary;
import com.cohortlens.core.MiniJson;
import com.cohortlens.core.Observation;
import com.cohortlens.core.Pseudonymizer;
import com.cohortlens.core.ValidationIssue;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DEVELOPMENT ONLY. A tiny in memory server built on the JDK's own HTTP server. It serves the same
 * REST API as the Spring Boot application, using the same core classes, but it has no database, no
 * login and no persistence. It lets you work on the dashboard without Postgres or Maven.
 *
 * <pre>
 * javac -d out $(find src/main/java/com/cohortlens/core src/main/java/com/cohortlens/dev -name '*.java')
 * java -cp out com.cohortlens.dev.DevServer --load ../data/synthetic_education_messy.csv
 * </pre>
 */
public final class DevServer {

    private final Pseudonymizer pseudonymizer = new Pseudonymizer("dev-only-secret-change-me-please");
    private final ImportProcessor processor = new ImportProcessor(pseudonymizer);
    private final AnalyticsService analytics = new AnalyticsService();

    private final List<Observation> observations = new ArrayList<>();
    private final Map<Long, ImportSummary> imports = new LinkedHashMap<>();
    private final Map<Long, List<ValidationIssue>> importIssues = new LinkedHashMap<>();
    private final List<AuditEntry> audit = new ArrayList<>();
    private final AtomicLong importIds = new AtomicLong();
    private final AtomicLong auditIds = new AtomicLong();

    public static void main(String[] args) throws Exception {
        int port = 8080;
        String load = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--port")) {
                port = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--load")) {
                load = args[++i];
            }
        }
        DevServer server = new DevServer();
        if (load != null) {
            String csv = Files.readString(Path.of(load));
            ImportSummary s = server.runImport(Path.of(load).getFileName().toString(), "REPLACE", csv);
            System.out.println("Loaded " + s.acceptedRows() + " rows (" + s.rejectedRows() + " rejected, "
                    + s.warnings() + " warnings)");
        }
        server.start(port);
    }

    void start(int port) throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        http.createContext("/api/", this::handle);
        http.setExecutor(Executors.newFixedThreadPool(4));
        http.start();
        System.out.println("CohortLens dev server (in memory, no auth) on http://127.0.0.1:" + port);
    }

    // ---------------------------------------------------------------- routing

    private void handle(HttpExchange ex) throws IOException {
        try {
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
            String method = ex.getRequestMethod();
            if (method.equals("OPTIONS")) {
                send(ex, 204, "text/plain", "");
                return;
            }
            String path = ex.getRequestURI().getPath();
            Map<String, String> q = query(ex.getRequestURI().getRawQuery());

            synchronized (this) {
                if (method.equals("GET") && path.equals("/api/me")) {
                    json(ex, 200, Map.of("username", "dev", "roles", List.of("ADMIN", "RESEARCHER", "VIEWER")));
                } else if (method.equals("GET") && path.equals("/api/analytics/options")) {
                    json(ex, 200, analytics.options(observations));
                } else if (method.equals("GET") && path.equals("/api/analytics/overview")) {
                    json(ex, 200, analytics.overview(observations, filter(q)));
                } else if (method.equals("GET") && path.equals("/api/analytics/trend")) {
                    json(ex, 200, analytics.trend(observations, filter(q)));
                } else if (method.equals("GET") && path.equals("/api/analytics/gaps")) {
                    GapDimension d = GapDimension.valueOf(q.getOrDefault("dimension", "ECONOMIC_STATUS"));
                    json(ex, 200, analytics.gaps(observations, filter(q), d));
                } else if (method.equals("GET") && path.equals("/api/analytics/risk/summary")) {
                    json(ex, 200, analytics.riskSummary(observations, filter(q)));
                } else if (method.equals("GET") && path.equals("/api/analytics/risk/students")) {
                    int limit = Integer.parseInt(q.getOrDefault("limit", "50"));
                    var all = analytics.riskStudents(observations, filter(q));
                    json(ex, 200, all.subList(0, Math.min(limit, all.size())));
                } else if (method.equals("GET") && path.equals("/api/imports")) {
                    List<ImportSummary> list = new ArrayList<>(imports.values());
                    java.util.Collections.reverse(list);
                    json(ex, 200, list);
                } else if (method.equals("POST") && path.equals("/api/imports")) {
                    String csv = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String filename = q.getOrDefault("filename", "upload.csv");
                    String mode = q.getOrDefault("mode", "APPEND").toUpperCase(Locale.ROOT);
                    if (!mode.equals("APPEND") && !mode.equals("REPLACE")) {
                        json(ex, 400, Map.of("error", "mode must be APPEND or REPLACE"));
                        return;
                    }
                    json(ex, 201, runImport(filename, mode, csv));
                } else if (method.equals("GET") && path.matches("/api/imports/\\d+/issues")) {
                    long id = Long.parseLong(path.split("/")[3]);
                    List<ValidationIssue> issues = importIssues.get(id);
                    if (issues == null) {
                        json(ex, 404, Map.of("error", "import not found"));
                        return;
                    }
                    String severity = q.get("severity");
                    String code = q.get("code");
                    int limit = Integer.parseInt(q.getOrDefault("limit", "200"));
                    json(ex, 200, issues.stream()
                            .filter(i -> severity == null || i.severity().name().equalsIgnoreCase(severity))
                            .filter(i -> code == null || i.code().equals(code))
                            .limit(limit).toList());
                } else if (method.equals("GET") && path.equals("/api/export.csv")) {
                    addAudit("EXPORT", "observations", observations.size() + " rows");
                    send(ex, 200, "text/csv", CsvExporter.toCsv(observations));
                } else if (method.equals("GET") && path.equals("/api/audit")) {
                    List<AuditEntry> list = new ArrayList<>(audit);
                    java.util.Collections.reverse(list);
                    json(ex, 200, list.subList(0, Math.min(200, list.size())));
                } else {
                    json(ex, 404, Map.of("error", "not found"));
                }
            }
        } catch (IllegalArgumentException e) {
            json(ex, 400, Map.of("error", String.valueOf(e.getMessage())));
        } catch (RuntimeException e) {
            e.printStackTrace();
            json(ex, 500, Map.of("error", "internal error"));
        }
    }

    // ---------------------------------------------------------------- behavior

    synchronized ImportSummary runImport(String filename, String mode, String csv) {
        ImportResult result = processor.process(csv);
        if (mode.equals("APPEND")) {
            Set<String> existing = new HashSet<>();
            for (Observation o : observations) {
                existing.add(o.naturalKey());
            }
            result = ImportMerger.rejectExisting(result, existing);
        }
        long id = importIds.incrementAndGet();
        ImportSummary summary = ImportSummary.of(id, filename, mode, result, Instant.now().toString(), "dev");
        if (!result.fatal()) {
            if (mode.equals("REPLACE")) {
                addAudit("DELETE_ALL", "observations", observations.size() + " rows removed by replace import " + id);
                observations.clear();
            }
            observations.addAll(result.accepted());
        }
        imports.put(id, summary);
        importIssues.put(id, result.issues());
        addAudit("IMPORT", "import " + id, filename + ": " + summary.acceptedRows() + " accepted, "
                + summary.rejectedRows() + " rejected");
        return summary;
    }

    private void addAudit(String action, String entity, String detail) {
        audit.add(new AuditEntry(auditIds.incrementAndGet(), Instant.now().toString(), "dev", action, entity, detail));
    }

    // ---------------------------------------------------------------- http helpers

    private static Filter filter(Map<String, String> q) {
        String school = blankToNull(q.get("school"));
        String grade = blankToNull(q.get("grade"));
        String econ = blankToNull(q.get("economicStatus"));
        String el = blankToNull(q.get("englishLearner"));
        return new Filter(school, grade == null ? null : Integer.valueOf(grade),
                econ == null ? null : EconomicStatus.valueOf(econ), el == null ? null : Boolean.valueOf(el));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            map.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return map;
    }

    private static void json(HttpExchange ex, int status, Object body) throws IOException {
        send(ex, status, "application/json", MiniJson.write(body));
    }

    private static void send(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        if (status == 204) {
            ex.sendResponseHeaders(status, -1);
        } else {
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
        }
        ex.close();
    }
}
