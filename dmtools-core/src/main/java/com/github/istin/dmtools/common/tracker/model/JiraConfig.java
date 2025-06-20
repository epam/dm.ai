package com.github.istin.dmtools.common.tracker.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class JiraConfig extends TrackerConfig {

    public static final String CLOUD_ID = "cloud_id";
    public static final String DEFAULT_JQL = "default_jql";
    public static final String CUSTOM_FIELDS = "custom_fields";

    @SerializedName(CLOUD_ID)
    private String cloudId;

    @SerializedName(DEFAULT_JQL)
    private String defaultJql;

    @SerializedName(CUSTOM_FIELDS)
    private String[] customFields;

    public JiraConfig() {
        super();
        setType(Type.JIRA);
    }
} 