package com.github.istin.dmtools.reporting.model;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Setter
@Getter
public class MetricConfig {
    private String name;
    private Map<String, Object> params;

}
