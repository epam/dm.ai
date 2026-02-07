package com.github.istin.dmtools.reporting.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ReportConfig {
    @Setter
    @Getter
    private String reportName;
    @Setter
    @Getter
    private String startDate;
    @Setter
    @Getter
    private String endDate;

    @Setter
    @Getter
    @JsonProperty("dataSources")
    private List<DataSourceConfig> dataSources;

    @JsonProperty("timeGrouping")
    @JsonDeserialize(using = TimeGroupingDeserializer.class)
    private List<TimeGroupingConfig> timeGroupings;

    @Setter
    @Getter
    @JsonProperty("aggregation")
    private AggregationConfig aggregation;

    @Setter
    @Getter
    @JsonProperty("output")
    private OutputConfig output;

    @Setter
    @Getter
    private List<String> employees;

    @Setter
    @Getter
    private Map<String, List<String>> aliases;

    /**
     * Returns the list of time groupings. For backward compatibility,
     * a single-object JSON value is deserialized as a list with one element.
     */
    @JsonIgnore
    public List<TimeGroupingConfig> getTimeGroupings() {
        return timeGroupings != null ? timeGroupings : Collections.emptyList();
    }

    @JsonIgnore
    public void setTimeGroupings(List<TimeGroupingConfig> timeGroupings) {
        this.timeGroupings = timeGroupings;
    }

    /**
     * Backward-compatible getter. Returns the first time grouping or null.
     */
    @JsonIgnore
    public TimeGroupingConfig getTimeGrouping() {
        return timeGroupings != null && !timeGroupings.isEmpty() ? timeGroupings.get(0) : null;
    }

    /**
     * Backward-compatible setter. Sets a single time grouping as a list of one.
     */
    @JsonIgnore
    public void setTimeGrouping(TimeGroupingConfig timeGrouping) {
        this.timeGroupings = timeGrouping != null ? Collections.singletonList(timeGrouping) : null;
    }

    /**
     * Returns true if multiple time groupings are configured.
     */
    @JsonIgnore
    public boolean isMultiGrouping() {
        return timeGroupings != null && timeGroupings.size() > 1;
    }

}
