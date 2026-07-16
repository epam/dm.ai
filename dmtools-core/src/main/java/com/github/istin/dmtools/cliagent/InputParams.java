// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.cliagent;

import com.github.istin.dmtools.teammate.InputContextConfig;
import com.google.gson.annotations.JsonAdapter;
import lombok.Data;

/**
 * Configuration for the optional smart input context of {@link CliAgent}.
 * <p>
 * Can be specified either as a plain ticket-key string:
 * <pre>{@code
 *   "input": "PROJ-123"
 * }</pre>
 * or as a full object:
 * <pre>{@code
 *   "input": {
 *     "ticket": "PROJ-123",
 *     "smart": true,
 *     "sources": ["confluence", "figma"],
 *     "depth": 1
 *   }
 * }</pre>
 */
@Data
@JsonAdapter(InputParamsTypeAdapter.class)
public class InputParams implements InputContextConfig {

    private String ticket;
    private String jql;
    private boolean smart = true;
    private String[] sources;
    private int depth = 1;
    private boolean includeComments = true;
    private boolean includeAttachments = true;
    private boolean skipVideoAttachments = false;
    private boolean skipAllAttachments = false;
    private boolean ignoreClonedByRelationship = true;

    public InputParams() {
    }

    public InputParams(String ticket) {
        this.ticket = ticket;
    }

    @Override
    public boolean isIncludeLinkedTickets() {
        return depth > 0;
    }

    /**
     * Returns true if the given source is enabled by the whitelist.
     * When no sources are configured, all sources are considered enabled.
     */
    public boolean isSourceEnabled(String source) {
        if (sources == null || sources.length == 0) {
            return true;
        }
        for (String configured : sources) {
            if (configured != null && configured.equalsIgnoreCase(source)) {
                return true;
            }
        }
        return false;
    }
}
