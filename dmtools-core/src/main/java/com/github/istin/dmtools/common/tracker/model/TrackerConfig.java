package com.github.istin.dmtools.common.tracker.model;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TrackerConfig {

    public static final String _KEY = "tracker_config";

    public static final String TYPE = "type";
    public static final String AUTH = "auth";
    public static final String BASE_URL = "base_url";
    public static final String API_VERSION = "api_version";
    public static final String PROJECT_KEY = "project_key";
    public static final String DEFAULT_JQL = "default_jql";

    public enum Type {
        JIRA, RALLY, AZURE_DEVOPS
    }

    @SerializedName(TYPE)
    private Type type;

    @SerializedName(AUTH)
    private String auth;

    @SerializedName(BASE_URL)
    private String baseUrl;

    @SerializedName(API_VERSION)
    private String apiVersion;

    @SerializedName(PROJECT_KEY)
    private String projectKey;

    public boolean isConfigured() {
        return baseUrl != null || auth != null || projectKey != null;
    }
} 