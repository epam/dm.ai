// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp.cli.interactive;

import java.util.Objects;

/**
 * A selectable item in the interactive command picker.
 *
 * <p>Each item has a kind (MCP tool, built-in job, JavaScript file, or JSON config),
 * an integration/category tag, a display name, and a short description.</p>
 */
public final class CommandItem {

    public enum Kind {
        MCP("mcp"),
        JOB("job"),
        JS("js"),
        JSON("json");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private final Kind kind;
    private final String integration;
    private final String name;
    private final String description;

    public CommandItem(Kind kind, String integration, String name, String description) {
        this.kind = kind;
        this.integration = integration == null ? "" : integration;
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
    }

    public Kind getKind() {
        return kind;
    }

    public String getIntegration() {
        return integration;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Returns the text used for filtering. Spaces are normalised to underscores
     * so that typing "jira_get_ticket" matches "jira get ticket" aliases.
     */
    public String getSearchableText() {
        return (kind.getId() + " " + integration + " " + name).toLowerCase().replace(" ", "_");
    }

    /**
     * Returns the shell command that should be executed for this item.
     * For {@code Kind.MCP} this is the tool name; for runnable files/jobs
     * it is prefixed with {@code run }.
     */
    public String toShellCommand() {
        if (kind == Kind.MCP) {
            return name;
        }
        return "run " + name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommandItem)) return false;
        CommandItem that = (CommandItem) o;
        return kind == that.kind && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, name);
    }
}
