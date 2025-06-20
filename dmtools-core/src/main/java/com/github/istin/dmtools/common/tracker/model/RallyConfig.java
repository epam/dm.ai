package com.github.istin.dmtools.common.tracker.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class RallyConfig extends TrackerConfig {

    public static final String WORKSPACE = "workspace";
    public static final String DEFAULT_QUERY = "default_query";
    public static final String ARTIFACT_TYPE = "artifact_type";

    @SerializedName(WORKSPACE)
    private String workspace;

    @SerializedName(DEFAULT_QUERY)
    private String defaultQuery;

    @SerializedName(ARTIFACT_TYPE)
    private String artifactType;

    public RallyConfig() {
        super();
        setType(Type.RALLY);
    }
} 