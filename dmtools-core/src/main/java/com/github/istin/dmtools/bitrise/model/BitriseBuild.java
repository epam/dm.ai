// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.bitrise.model;

import org.json.JSONObject;

public class BitriseBuild {

    private final JSONObject json;

    public BitriseBuild(JSONObject json) {
        this.json = json;
    }

    public BitriseBuild(String json) {
        this(new JSONObject(json));
    }

    public String getSlug() {
        return json.optString("slug", null);
    }

    public int getBuildNumber() {
        return json.optInt("build_number", -1);
    }

    /** status: 0=not started, 1=in progress, 2=success, 3=failed, 4=aborted */
    public int getStatus() {
        return json.optInt("status", -1);
    }

    public String getStatusText() {
        return json.optString("status_text", null);
    }

    public String getBranch() {
        return json.optString("branch", null);
    }

    public String getWorkflowId() {
        return json.optString("triggered_workflow", null);
    }

    public String getCommitHash() {
        return json.optString("commit_hash", null);
    }

    public String getCommitMessage() {
        return json.optString("commit_message", null);
    }

    public String getTriggeredAt() {
        return json.optString("triggered_at", null);
    }

    public String getStartedAt() {
        return json.optString("started_on_worker_at", null);
    }

    public String getFinishedAt() {
        return json.optString("finished_at", null);
    }

    public long getDurationSecs() {
        return json.optLong("duration", 0L);
    }

    public String getAbortReason() {
        return json.optString("abort_reason", null);
    }

    public boolean isOnHold() {
        return json.optBoolean("is_on_hold", false);
    }

    public JSONObject toJSON() {
        return json;
    }

    @Override
    public String toString() {
        return json.toString();
    }
}
