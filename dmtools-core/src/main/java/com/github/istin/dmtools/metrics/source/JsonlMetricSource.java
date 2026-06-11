// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.metrics.source;

import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads JSONL (line-delimited JSON) files from a folder or single file
 * and produces KeyTime entries for reporting metrics.
 *
 * Supports:
 * - folderPath: scans all .json / .jsonl files in directory
 * - filePath: single file (alternative to folderPath)
 * - whoField, whenField, weightField with dot-notation for nested objects
 * - arrayField + arrayFilterField + arrayFilterValue: extract from arrays
 * - filterField + filterValue: top-level row filtering
 * - weightMultiplier: scale numeric values
 * - Caching by file path + lastModified
 */
public class JsonlMetricSource extends CommonSourceCollector {

    private static final Logger logger = LogManager.getLogger(JsonlMetricSource.class);
    private static final Map<String, CachedJsonl> CACHE = new ConcurrentHashMap<>();

    private final String folderPath;
    private final String filePath;
    private final String whoField;
    private final String whenField;
    private final String weightField;
    private final double weightMultiplier;
    private final String filterField;
    private final String filterValue;
    private final String arrayField;
    private final String arrayFilterField;
    private final String arrayFilterValue;
    private final String dateFormat;
    private final String groupByField;

    public JsonlMetricSource(IEmployees employees,
                             String folderPath,
                             String filePath,
                             String whoField,
                             String whenField,
                             String weightField,
                             double weightMultiplier,
                             String filterField,
                             String filterValue,
                             String arrayField,
                             String arrayFilterField,
                             String arrayFilterValue,
                             String dateFormat,
                             String groupByField) {
        super(employees);
        this.folderPath = folderPath;
        this.filePath = filePath;
        this.whoField = whoField != null ? whoField : "user_login";
        this.whenField = whenField != null ? whenField : "day";
        this.weightField = weightField;
        this.weightMultiplier = weightMultiplier;
        this.filterField = filterField;
        this.filterValue = filterValue;
        this.arrayField = arrayField;
        this.arrayFilterField = arrayFilterField;
        this.arrayFilterValue = arrayFilterValue;
        this.dateFormat = dateFormat;
        this.groupByField = groupByField;
    }

    @Override
    public List<KeyTime> performSourceCollection(boolean isPersonalized, String metricName) throws Exception {
        List<KeyTime> keyTimes = new ArrayList<>();

        List<File> files = resolveFiles();
        if (files.isEmpty()) {
            logger.warn("No JSON/JSONL files found for path: folderPath={}, filePath={}", folderPath, filePath);
            return keyTimes;
        }

        SimpleDateFormat sdf = dateFormat != null && !dateFormat.isEmpty()
                ? new SimpleDateFormat(dateFormat)
                : new SimpleDateFormat("yyyy-MM-dd");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        int fileIndex = 0;
        for (File file : files) {
            List<JSONObject> rows = loadJsonl(file);
            int rowIndex = 0;
            for (JSONObject row : rows) {
                try {
                    // Top-level filter
                    if (!passesFilter(row)) {
                        continue;
                    }

                    List<JSONObject> targets = resolveTargetObjects(row);
                    if (targets.isEmpty() && (arrayField == null || arrayField.isEmpty())) {
                        // Try top-level only if no array configured
                        targets = Collections.singletonList(row);
                    }
                    if (targets.isEmpty()) {
                        continue;
                    }

                    for (JSONObject target : targets) {
                        Double weight = extractNumeric(target, weightField);
                        if (weight == null) {
                            continue;
                        }

                        String whenStr = extractString(target, whenField);
                        if (whenStr == null) {
                            whenStr = extractString(row, whenField);
                        }
                        if (whenStr == null || whenStr.isEmpty()) {
                            continue;
                        }

                        Date parsedDate = sdf.parse(whenStr);
                        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                        cal.setTime(parsedDate);

                        String who = resolveWho(row, target, isPersonalized, metricName);
                        if (who == null || who.isEmpty()) {
                            continue;
                        }

                        String key = file.getName() + "_" + fileIndex + "_" + rowIndex;
                        KeyTime keyTime = new KeyTime(key, cal, who);
                        keyTime.setWeight(weight * weightMultiplier);

                        // Build summary from available context
                        String summary = buildSummary(row, target);
                        if (summary != null) {
                            keyTime.setSummary(summary);
                        }

                        keyTimes.add(keyTime);
                    }
                } catch (Exception e) {
                    logger.debug("Skipping malformed row {} in {}: {}", rowIndex, file.getName(), e.getMessage());
                }
                rowIndex++;
            }
            fileIndex++;
        }

        logger.info("JSONL collected {} entries for metric '{}' (weightField={})",
                keyTimes.size(), metricName, weightField);
        return keyTimes;
    }

    private List<File> resolveFiles() {
        if (filePath != null && !filePath.isEmpty()) {
            File f = new File(filePath);
            return f.exists() ? Collections.singletonList(f) : Collections.emptyList();
        }
        if (folderPath != null && !folderPath.isEmpty()) {
            File dir = new File(folderPath);
            if (dir.isDirectory()) {
                try (Stream<Path> walk = Files.walk(dir.toPath())) {
                    return walk
                            .filter(Files::isRegularFile)
                            .filter(p -> {
                                String name = p.getFileName().toString().toLowerCase();
                                return name.endsWith(".json") || name.endsWith(".jsonl");
                            })
                            .map(Path::toFile)
                            .sorted(Comparator.comparing(File::getAbsolutePath))
                            .collect(Collectors.toList());
                } catch (IOException e) {
                    logger.warn("Failed to walk directory {}: {}", folderPath, e.getMessage());
                }
            }
        }
        return Collections.emptyList();
    }

    private List<JSONObject> loadJsonl(File file) throws IOException {
        String canonicalPath = file.getCanonicalPath();
        long lastModified = file.lastModified();
        long length = file.length();

        CachedJsonl cached = CACHE.get(canonicalPath);
        if (cached != null && cached.lastModified == lastModified && cached.length == length) {
            return cached.rows;
        }

        List<JSONObject> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8), 256 * 1024)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    rows.add(new JSONObject(line));
                } catch (Exception e) {
                    logger.debug("Skipping invalid JSON line in {}: {}", file.getName(), e.getMessage());
                }
            }
        }

        CachedJsonl fresh = new CachedJsonl(rows, lastModified, length);
        CACHE.put(canonicalPath, fresh);
        return rows;
    }

    private boolean passesFilter(JSONObject row) {
        if (filterField == null || filterValue == null) {
            return true;
        }
        Object value = extractValue(row, filterField);
        if (value == null) {
            return false;
        }
        String strValue = value.toString();
        if (value instanceof Boolean) {
            strValue = value.toString();
        }
        return filterValue.equalsIgnoreCase(strValue);
    }

    private List<JSONObject> resolveTargetObjects(JSONObject row) {
        if (arrayField == null || arrayField.isEmpty()) {
            return Collections.emptyList();
        }
        Object arrObj = extractValue(row, arrayField);
        if (!(arrObj instanceof JSONArray)) {
            return Collections.emptyList();
        }
        JSONArray arr = (JSONArray) arrObj;
        List<JSONObject> result = new ArrayList<>();
        boolean matchAll = "*".equals(arrayFilterValue);
        for (int i = 0; i < arr.length(); i++) {
            Object item = arr.opt(i);
            if (item instanceof JSONObject) {
                JSONObject obj = (JSONObject) item;
                if (arrayFilterField == null || arrayFilterValue == null) {
                    result.add(obj);
                } else if (matchAll) {
                    result.add(obj);
                } else {
                    Object fv = extractValue(obj, arrayFilterField);
                    if (fv != null && arrayFilterValue.equalsIgnoreCase(fv.toString())) {
                        result.add(obj);
                    }
                }
            }
        }
        return result;
    }

    private String resolveWho(JSONObject row, JSONObject target, boolean isPersonalized, String metricName) {
        if (!isPersonalized) {
            return metricName;
        }
        String who;
        if (groupByField != null && !groupByField.isEmpty()) {
            who = extractString(target, groupByField);
            if (who == null || who.isEmpty()) {
                who = extractString(row, groupByField);
            }
        } else {
            who = extractString(target, whoField);
            if (who == null || who.isEmpty()) {
                who = extractString(row, whoField);
            }
        }
        if (who == null || who.isEmpty()) {
            who = metricName;
        }
        who = transformName(who);
        if (groupByField == null || groupByField.isEmpty()) {
            if (getEmployees() != null && !getEmployees().contains(who)) {
                who = IEmployees.UNKNOWN;
            }
        }
        return who;
    }

    private String buildSummary(JSONObject row, JSONObject target) {
        StringBuilder sb = new StringBuilder();
        // Add useful context fields for summary
        appendSummaryField(sb, row, "user_login");
        appendSummaryField(sb, row, "day");
        if (arrayFilterField != null) {
            appendSummaryField(sb, target, arrayFilterField);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private void appendSummaryField(StringBuilder sb, JSONObject obj, String field) {
        String value = extractString(obj, field);
        if (value != null && !value.isEmpty()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(field).append(": ").append(value);
        }
    }

    // ======== Dot-notation value extraction ========

    static Object extractValue(Object root, String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (current == null) {
                return null;
            }
            current = extractChild(current, part);
        }
        return current;
    }

    private static Object extractChild(Object parent, String key) {
        if (parent instanceof JSONObject) {
            JSONObject json = (JSONObject) parent;
            if (json.has(key)) {
                return json.opt(key);
            }
            // Try case-insensitive
            for (String k : json.keySet()) {
                if (k.equalsIgnoreCase(key)) {
                    return json.opt(k);
                }
            }
            return null;
        }
        if (parent instanceof JSONArray) {
            JSONArray arr = (JSONArray) parent;
            try {
                int idx = Integer.parseInt(key);
                if (idx >= 0 && idx < arr.length()) {
                    return arr.opt(idx);
                }
            } catch (NumberFormatException e) {
                // key is not an index, try to find object with matching field
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.opt(i);
                    if (item instanceof JSONObject) {
                        JSONObject obj = (JSONObject) item;
                        if (obj.has(key) || hasKeyIgnoreCase(obj, key)) {
                            return obj;
                        }
                    }
                }
            }
            return null;
        }
        return null;
    }

    private static boolean hasKeyIgnoreCase(JSONObject obj, String key) {
        for (String k : obj.keySet()) {
            if (k.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    static String extractString(Object root, String path) {
        Object value = extractValue(root, path);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    static Double extractNumeric(Object root, String path) {
        Object value = extractValue(root, path);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            double d = Double.parseDouble(value.toString());
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return null;
            }
            return d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static class CachedJsonl {
        private final List<JSONObject> rows;
        private final long lastModified;
        private final long length;

        CachedJsonl(List<JSONObject> rows, long lastModified, long length) {
            this.rows = rows;
            this.lastModified = lastModified;
            this.length = length;
        }
    }
}
