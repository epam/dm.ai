// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.csv;

import com.github.istin.dmtools.metrics.source.CommonSourceCollector;
import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.*;

/**
 * Reads a CSV file and produces KeyTime entries for reporting metrics.
 * Similar to ExcelMetricSource but for CSV files.
 *
 * Supports:
 * - Configurable columns: whoColumn (person), whenColumn (date), weightColumn (numeric value)
 * - defaultWho: fallback person name when no whoColumn is specified
 * - Quoted numeric values like "79997" or "0.04"
 * - ISO 8601 dates (2026-02-07T13:29:37.369Z) and simple dates (2026-02-07)
 * - Custom date format override via dateFormat param
 * - Graceful handling of invalid/non-numeric values (skipped)
 * - File path: absolute or relative to working directory
 */
public class CsvMetricSource extends CommonSourceCollector {

    private static final Logger logger = LogManager.getLogger(CsvMetricSource.class);
    private static final Map<String, CachedCsv> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private final String filePath;
    private final String whoColumn;
    private final String whenColumn;
    private final String weightColumn;
    private final double weightMultiplier;
    private final String defaultWho;
    private final String dateFormat;
    private final DateTimeFormatter customDateFormat;
    private final String filterColumn;
    private final List<String> filterValues;

    public CsvMetricSource(IEmployees employees, String filePath, String whoColumn, String whenColumn,
                           String weightColumn, double weightMultiplier, String defaultWho, String dateFormat) {
        this(employees, filePath, whoColumn, whenColumn, weightColumn, weightMultiplier, defaultWho, dateFormat, null, null);
    }

    public CsvMetricSource(IEmployees employees, String filePath, String whoColumn, String whenColumn,
                           String weightColumn, double weightMultiplier, String defaultWho, String dateFormat,
                           String filterColumn, List<String> filterValues) {
        super(employees);
        this.filePath = filePath;
        this.whoColumn = whoColumn;
        this.whenColumn = whenColumn;
        this.weightColumn = weightColumn;
        this.weightMultiplier = weightMultiplier;
        this.defaultWho = defaultWho;
        this.dateFormat = dateFormat;
        this.filterColumn = filterColumn;
        this.filterValues = (filterValues != null && !filterValues.isEmpty()) ? filterValues : null;
        if (dateFormat != null && !dateFormat.isEmpty()) {
            this.customDateFormat = DateTimeFormatter.ofPattern(dateFormat).withZone(ZoneOffset.UTC);
        } else {
            this.customDateFormat = null;
        }
    }

    @Override
    public List<KeyTime> performSourceCollection(boolean isPersonalized, String metricName) throws Exception {
        List<KeyTime> keyTimes = new ArrayList<>();

        CachedCsv csv = loadCsv();
        if (csv == null || csv.headers == null) {
            logger.warn("CSV file not found or empty: {}", filePath);
            return keyTimes;
        }

        String[] headers = csv.headers;
        List<String[]> rows = csv.rows;
            int whoIdx = findColumnIndex(headers, whoColumn);
            int whenIdx = findColumnIndex(headers, whenColumn);
            int weightIdx = findColumnIndex(headers, weightColumn);

            if (whenIdx == -1) {
                logger.warn("Date column '{}' not found in CSV headers: {}", whenColumn, Arrays.toString(headers));
                return keyTimes;
            }
            if (weightIdx == -1) {
                logger.warn("Weight column '{}' not found in CSV headers: {}", weightColumn, Arrays.toString(headers));
                return keyTimes;
            }

            // Precompute summary columns for faster per-row processing
            int[] summaryIndexes = buildSummaryIndexes(headers, whenIdx, weightIdx, whoIdx);
            String[] summaryHeaders = buildSummaryHeaders(headers, summaryIndexes);

            // Resolve filter column index once
            int filterIdx = (filterColumn != null) ? findColumnIndex(headers, filterColumn) : -1;
            if (filterColumn != null && filterIdx == -1) {
                logger.warn("Filter column '{}' not found in CSV headers: {}", filterColumn, Arrays.toString(headers));
            }

            // Parse rows
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                String[] values = rows.get(rowIndex);

                // Apply column filter
                if (filterIdx >= 0 && filterValues != null) {
                    String cellValue = safeGet(values, filterIdx).trim();
                    boolean matches = false;
                    for (String fv : filterValues) {
                        if (fv.equalsIgnoreCase(cellValue)) { matches = true; break; }
                    }
                    if (!matches) continue;
                }

                // Parse date
                Calendar cal = parseDateValue(safeGet(values, whenIdx));
                if (cal == null) {
                    continue;
                }

                // Parse weight
                Double weight = parseNumber(safeGet(values, weightIdx));
                if (weight == null) {
                    continue;
                }

                // Determine who
                String who;
                if (isPersonalized) {
                    if (whoIdx >= 0) {
                        who = safeGet(values, whoIdx).trim();
                        if (getEmployees() != null) {
                            who = getEmployees().transformName(who);
                            if (!getEmployees().contains(who)) {
                                who = IEmployees.UNKNOWN;
                            }
                        }
                    } else if (defaultWho != null && !defaultWho.isEmpty()) {
                        who = defaultWho;
                    } else {
                        who = metricName;
                    }
                } else {
                    who = metricName;
                }

                KeyTime keyTime = new KeyTime(filePath + "_" + rowIndex, cal, who);
                keyTime.setWeight(weight * weightMultiplier);

                // Build summary from available columns (precomputed indexes)
                if (summaryIndexes.length > 0) {
                    StringBuilder summary = null;
                    for (int i = 0; i < summaryIndexes.length; i++) {
                        int idx = summaryIndexes[i];
                        if (idx >= values.length) continue;
                        String v = values[idx].trim();
                        if (!v.isEmpty()) {
                            if (summary == null) {
                                summary = new StringBuilder();
                            } else {
                                summary.append(" | ");
                            }
                            summary.append(summaryHeaders[i]).append(": ").append(v);
                        }
                    }
                    if (summary != null) {
                        keyTime.setSummary(summary.toString());
                    }
                }

                keyTimes.add(keyTime);
            }

        logger.info("CSV '{}': collected {} entries from column '{}'", filePath, keyTimes.size(), weightColumn);
        return keyTimes;
    }

    private CachedCsv loadCsv() throws IOException {
        // Try absolute path first, then relative
        File file = new File(filePath);
        if (file.exists()) {
            String key = file.getCanonicalPath();
            long lastModified = file.lastModified();
            long length = file.length();
            CachedCsv cached = CACHE.get(key);
            if (cached != null && cached.lastModified == lastModified && cached.length == length) {
                return cached;
            }
            CachedCsv fresh = readCsv(new FileInputStream(file), lastModified, length);
            CACHE.put(key, fresh);
            return fresh;
        }
        // Try classpath (for tests)
        String cpKey = "classpath:" + filePath;
        CachedCsv cached = CACHE.get(cpKey);
        if (cached != null) return cached;
        InputStream is = getClass().getResourceAsStream(filePath);
        if (is != null) {
            CachedCsv fresh = readCsv(is, -1, -1);
            CACHE.put(cpKey, fresh);
            return fresh;
        }
        return null;
    }

    private CachedCsv readCsv(InputStream is, long lastModified, long length) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8), 64 * 1024)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return new CachedCsv(null, Collections.emptyList(), lastModified, length);
            }
            String[] headers = parseCsvLine(headerLine);
            List<String[]> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                rows.add(parseCsvLine(line));
            }
            return new CachedCsv(headers, rows, lastModified, length);
        }
    }

    static String[] parseCsvLine(String line) {
        if (line == null) return new String[0];
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder(line.length());
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts.toArray(new String[0]);
    }

    static String unquote(String value) {
        if (value == null) return "";
        value = value.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).replace("\"\"", "\"");
        }
        return value;
    }

    static int findColumnIndex(String[] headers, String columnName) {
        if (columnName == null || columnName.isEmpty()) return -1;
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i] != null ? headers[i].trim() : "";
            if (i == 0 && header.startsWith("\uFEFF")) {
                header = header.replace("\uFEFF", "");
            }
            if (header.equalsIgnoreCase(columnName)) {
                return i;
            }
        }
        return -1;
    }

    static Double parseNumber(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        value = value.trim().replace(",", "");
        // Skip known non-numeric strings
        if ("NaN".equalsIgnoreCase(value) || "null".equalsIgnoreCase(value)
                || "N/A".equalsIgnoreCase(value) || "-".equals(value) || "inf".equalsIgnoreCase(value)
                || "infinity".equalsIgnoreCase(value) || "+infinity".equalsIgnoreCase(value)
                || "-infinity".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            double d = Double.parseDouble(value);
            if (Double.isNaN(d) || Double.isInfinite(d)) return null;
            return d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Formatters for patterns that include a time component (all anchored to UTC). */
    private static final DateTimeFormatter[] DATETIME_FORMATTERS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC),
    };

    /** Formatters for date-only patterns (time is set to midnight UTC). */
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
    };

    private static Calendar toCalendar(Instant instant) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(instant.toEpochMilli());
        return cal;
    }

    static Calendar parseDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        value = value.trim();
        for (DateTimeFormatter fmt : DATETIME_FORMATTERS) {
            try {
                return toCalendar(Instant.from(fmt.parse(value)));
            } catch (DateTimeParseException ignored) {
            }
        }
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                return toCalendar(LocalDate.parse(value, fmt).atStartOfDay(ZoneOffset.UTC).toInstant());
            } catch (DateTimeParseException ignored) {
            }
        }
        logger.debug("Failed to parse date: {}", value);
        return null;
    }

    private Calendar parseDateValue(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        if (customDateFormat != null) {
            try {
                String trimmed = value.trim();
                TemporalAccessor parsed = customDateFormat.parse(trimmed);
                try {
                    return toCalendar(Instant.from(parsed));
                } catch (DateTimeParseException ignored) {
                    return toCalendar(LocalDate.from(parsed).atStartOfDay(ZoneOffset.UTC).toInstant());
                }
            } catch (Exception ignored) {
            }
        }
        return parseDate(value);
    }

    private static int[] buildSummaryIndexes(String[] headers, int whenIdx, int weightIdx, int whoIdx) {
        List<Integer> summaryIndexes = new ArrayList<>();
        for (int i = 0; i < headers.length; i++) {
            if (i == whenIdx || i == weightIdx || i == whoIdx) continue;
            String header = headers[i] != null ? headers[i].trim() : "";
            if (!header.isEmpty()) {
                summaryIndexes.add(i);
            }
        }
        return summaryIndexes.stream().mapToInt(Integer::intValue).toArray();
    }

    private static String[] buildSummaryHeaders(String[] headers, int[] summaryIndexes) {
        String[] summaryHeaders = new String[summaryIndexes.length];
        for (int i = 0; i < summaryIndexes.length; i++) {
            int idx = summaryIndexes[i];
            summaryHeaders[i] = headers[idx] != null ? headers[idx].trim() : "";
        }
        return summaryHeaders;
    }

    private static String safeGet(String[] arr, int idx) {
        if (idx < 0 || idx >= arr.length) return "";
        return arr[idx];
    }

    private static class CachedCsv {
        private final String[] headers;
        private final List<String[]> rows;
        private final long lastModified;
        private final long length;

        private CachedCsv(String[] headers, List<String[]> rows, long lastModified, long length) {
            this.headers = headers;
            this.rows = rows;
            this.lastModified = lastModified;
            this.length = length;
        }
    }
}
