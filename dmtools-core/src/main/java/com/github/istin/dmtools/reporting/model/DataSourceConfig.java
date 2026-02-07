package com.github.istin.dmtools.reporting.model;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Setter
@Getter
public class DataSourceConfig {
    private String name;
    private Map<String, Object> params;
    private List<MetricConfig> metrics;

}
