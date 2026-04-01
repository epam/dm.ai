// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.sync;

import com.github.istin.dmtools.atlassian.jira.utils.IssuesIDsParserParams;
import com.github.istin.dmtools.job.Params;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper=false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SourceCodeCommitTrackerSyncParams extends Params {

    public static final String ISSUE_ID_CODES = "issue_id_codes";
    public static final String SYNC_TYPE = "sync_type";
    public static final String START_DATE = "start_date";
    public static final String END_DATE = "end_date";

    public enum SyncType {
        ALL, ONE_DAY, RANGE
    }

    @SerializedName(ISSUE_ID_CODES)
    private String[] issueIdCodes;

    @SerializedName(SYNC_TYPE)
    private SyncType syncType;

    @SerializedName(START_DATE)
    private String startDate;

    @SerializedName(END_DATE)
    private String endDate;

    @SerializedName(IssuesIDsParserParams._KEY)
    private IssuesIDsParserParams issuesIDsParserParams;
}