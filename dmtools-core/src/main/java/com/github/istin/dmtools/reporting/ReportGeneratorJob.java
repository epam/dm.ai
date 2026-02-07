package com.github.istin.dmtools.reporting;

import com.github.istin.dmtools.common.code.SourceCode;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.tracker.TrackerClient;
import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.reporting.model.*;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import lombok.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Job for generating reports from JSON configuration.
 * Uses TrackerClient abstraction - works with Jira, ADO, Rally, etc.
 */
public class ReportGeneratorJob extends AbstractJob<
    ReportGeneratorJob.Params,
    ReportOutput
> {
    private static final Logger logger = LogManager.getLogger(ReportGeneratorJob.class);

    @Inject
    TrackerClient<? extends ITicket> trackerClient;

    @Data
    @EqualsAndHashCode(callSuper=false)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class Params extends com.github.istin.dmtools.job.Params {
        @SerializedName("reportName")
        private String reportName;

        @SerializedName("startDate")
        private String startDate;

        @SerializedName("endDate")
        private String endDate;

        @SerializedName("dataSources")
        private List<DataSourceConfig> dataSources;

        @SerializedName("timeGrouping")
        private Object timeGrouping; // Can be a single object or array - Gson stores as LinkedTreeMap or List

        @SerializedName("aggregation")
        private AggregationConfig aggregation;

        @SerializedName("output")
        private OutputConfig output;

        @SerializedName("employees")
        private List<String> employees;

        @SerializedName("aliases")
        private Map<String, List<String>> aliases;

        /**
         * Normalizes timeGrouping to a list of TimeGroupingConfig.
         * Handles both single-object and array forms from Gson deserialization.
         */
        public List<TimeGroupingConfig> getTimeGroupings() {
            if (timeGrouping == null) {
                return Collections.emptyList();
            }
            Gson gson = new Gson();
            String json = gson.toJson(timeGrouping);
            if (timeGrouping instanceof List) {
                // Array form
                return gson.fromJson(json, new TypeToken<List<TimeGroupingConfig>>(){}.getType());
            } else {
                // Single object form
                TimeGroupingConfig config = gson.fromJson(json, TimeGroupingConfig.class);
                return Collections.singletonList(config);
            }
        }

        /**
         * Backward-compatible getter: returns the first time grouping.
         */
        public TimeGroupingConfig getTimeGrouping() {
            List<TimeGroupingConfig> groupings = getTimeGroupings();
            return groupings.isEmpty() ? null : groupings.get(0);
        }
    }

    @Override
    protected void initializeStandalone() {
        DaggerReportGeneratorComponent.create().inject(this);
    }

    @Override
    protected void initializeServerManaged(JSONObject resolvedIntegrations) {
        // For server-managed mode, use same standalone injection for now
        DaggerReportGeneratorComponent.create().inject(this);
    }

    @Override
    public ReportOutput runJob(Params params) throws Exception {
        logger.info("ReportGeneratorJob starting...");

        // 1. Build configuration from params
        ReportConfig config = new ReportConfig();
        config.setReportName(params.getReportName());
        config.setStartDate(params.getStartDate());
        config.setEndDate(params.getEndDate());
        config.setDataSources(params.getDataSources());
        config.setTimeGroupings(params.getTimeGroupings());
        config.setAggregation(params.getAggregation());
        config.setOutput(params.getOutput());
        config.setEmployees(params.getEmployees());
        config.setAliases(params.getAliases());

        logger.info("Report config ready: {}", config.getReportName());
        logger.debug("Date range: {} to {}", config.getStartDate(), config.getEndDate());
        logger.debug("Data sources: {}", config.getDataSources() != null ? config.getDataSources().size() : 0);
        logger.debug("Time groupings: {}", config.getTimeGroupings().size());
        logger.debug("Tracker client: {}", trackerClient != null ? trackerClient.getClass().getSimpleName() : "null");

        // 2. Get source code - try each provider independently to avoid one failure killing all
        SourceCode sourceCode = null;
        List<SourceCode> sourceCodes = new ArrayList<>();
        for (String provider : new String[]{"github", "bitbucket", "gitlab"}) {
            try {
                List<SourceCode> found = SourceCode.Impl.getConfiguredSourceCodes(new org.json.JSONArray().put(provider));
                sourceCodes.addAll(found);
            } catch (Exception e) {
                logger.debug("Source code provider '{}' not available: {}", provider, e.getMessage());
            }
        }
        if (!sourceCodes.isEmpty()) {
            sourceCode = sourceCodes.get(0);
            logger.info("Source code configured: {} ({} provider(s) available)", sourceCode.getClass().getSimpleName(), sourceCodes.size());
        } else {
            logger.info("No source code providers configured");
        }

        // 3. Generate reports using abstract TrackerClient
        ReportGenerator generator = new ReportGenerator(trackerClient, sourceCode);

        logger.info("Generating reports...");
        List<ReportGenerator.ReportResult> results = generator.generateReports(config);

        logger.info("Report generation complete: {} grouping(s) produced", results.size());

        // 4. Conditionally run visualizer
        String visualizer = config.getOutput() != null ? config.getOutput().getVisualizer() : null;
        boolean runVisualizer = !"none".equalsIgnoreCase(visualizer);

        if (runVisualizer && !results.isEmpty()) {
            logger.info("Running ReportVisualizer for {} report(s)...", results.size());
            ReportVisualizer vis = new ReportVisualizer(trackerClient);
            for (ReportGenerator.ReportResult result : results) {
                try {
                    vis.visualize(result.getJsonPath());
                    logger.info("Visualized: {}", result.getJsonPath().replaceAll("\\.json$", ".html"));
                } catch (Exception e) {
                    logger.warn("Failed to visualize {}: {}", result.getJsonPath(), e.getMessage());
                }
            }
        }

        // Return the first report output for backward compatibility
        return results.isEmpty() ? null : results.get(0).getOutput();
    }

    @Override
    public AI getAi() {
        return null;
    }
}
