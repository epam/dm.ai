// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.qa;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.json.JSONObject;

/**
 * Generic container for custom test cases tracker configuration.
 * Only the {@code type} field is fixed; all tracker-specific config lives in
 * the {@code params} JSONObject. Each tracker adapter ships its own typed
 * accessor class (e.g., {@code TestRailAdapterParams}) for its own keys.
 *
 * <p>Example JSON:</p>
 * <pre>
 * {
 *   "type": "testrail",
 *   "params": {
 *     "projectNames": ["My Project"],
 *     "creationMode": "steps",
 *     "typeId": "7",
 *     "labelIds": "7,8"
 *   }
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomTestCasesTrackerParams {

    public static final String _KEY = "customTestCasesTracker";
    public static final String TYPE = "type";
    public static final String PARAMS = "params";

    @SerializedName(TYPE)
    private String type;

    @SerializedName(PARAMS)
    private JSONObject params;
}
