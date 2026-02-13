package com.github.istin.dmtools.reporting.datasource;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;

import java.util.List;
import java.util.Map;

/**
 * Factory for creating data sources from configuration.
 * Supports explicit sourceType param ("github", "bitbucket", "gitlab") for pullRequests/commits data sources.
 */
public class DataSourceFactory {

    private static final Logger logger = LogManager.getLogger(DataSourceFactory.class);

    public DataSourceFactory() {
    }

    public DataSource createDataSource(
        String name,
        Map<String, Object> params,
        TrackerClient trackerClient,
        SourceCode sourceCode
    ) {
        switch (name) {
            case "tracker":
                String jql = params != null ? (String) params.get("jql") : null;
                List<String> extraFields = parseFields(params != null ? params.get("fields") : null);
                return new TrackerDataSource(trackerClient, jql, extraFields);

            case "figma":
                return new FigmaDataSource();

            case "pullRequests":
                return new PullRequestsDataSource(resolveSourceCode(params, sourceCode), params);

            case "commits":
                return new CommitsDataSource(resolveSourceCode(params, sourceCode), params);

            case "csv":
                return new CsvDataSource(params);

            default:
                throw new IllegalArgumentException("Unknown data source: " + name);
        }
    }

    /**
     * Resolve SourceCode for a data source. If params contain "sourceType" (e.g. "github"),
     * create the specific provider. Otherwise fall back to the globally provided sourceCode.
     */
    private SourceCode resolveSourceCode(Map<String, Object> params, SourceCode fallback) {
        String sourceType = params != null ? (String) params.get("sourceType") : null;
        if (sourceType != null && !sourceType.isEmpty()) {
            try {
                List<SourceCode> sourceCodes = SourceCode.Impl.getConfiguredSourceCodes(
                    new JSONArray().put(sourceType)
                );
                if (!sourceCodes.isEmpty()) {
                    logger.info("Resolved source code provider '{}': {}", sourceType, sourceCodes.get(0).getClass().getSimpleName());
                    return sourceCodes.get(0);
                }
                logger.warn("Source code provider '{}' is not configured, falling back", sourceType);
            } catch (Exception e) {
                logger.warn("Failed to create source code provider '{}': {}", sourceType, e.getMessage());
            }
        }
        return fallback;
    }

    private List<String> parseFields(Object fieldsParam) {
        if (fieldsParam == null) {
            return null;
        }
        if (fieldsParam instanceof List) {
            List<?> raw = (List<?>) fieldsParam;
            if (raw.isEmpty()) return null;
            List<String> out = new java.util.ArrayList<>();
            for (Object v : raw) {
                if (v == null) continue;
                String s = v.toString().trim();
                if (!s.isEmpty()) out.add(s);
            }
            return out.isEmpty() ? null : out;
        }
        String s = fieldsParam.toString().trim();
        if (s.isEmpty()) return null;
        String[] parts = s.split(",");
        List<String> out = new java.util.ArrayList<>();
        for (String p : parts) {
            String v = p.trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out.isEmpty() ? null : out;
    }
}
