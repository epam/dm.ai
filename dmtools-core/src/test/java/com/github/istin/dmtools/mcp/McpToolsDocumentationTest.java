// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mcp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Ensures every MCP tool declared via {@code @MCPTool} has a corresponding entry in the
 * end-user documentation under {@code dmtools-ai-docs/references/mcp-tools}.
 */
class McpToolsDocumentationTest {

    private static final Pattern TOOL_NAME_PATTERN =
            Pattern.compile("@MCPTool\\s*\\(\\s*name\\s*=\\s*\"([^\"]+)\"");

    private static final Pattern WORD_BOUNDARY = Pattern.compile("\\b");

    @Test
    void everyMcpToolIsDocumented() throws IOException {
        Path moduleRoot = Paths.get("").toAbsolutePath();
        Path sourceRoot = moduleRoot.resolve("src/main/java");
        Path docsRoot = moduleRoot.getParent().resolve("dmtools-ai-docs/references/mcp-tools");

        if (!Files.exists(sourceRoot)) {
            fail("Source root not found: " + sourceRoot);
        }
        if (!Files.exists(docsRoot)) {
            fail("Documentation root not found: " + docsRoot);
        }

        Set<String> toolNames = collectToolNames(sourceRoot);
        assertTrue(!toolNames.isEmpty(), "No @MCPTool annotated methods found; check source scanning logic.");

        String allDocs = concatenateDocs(docsRoot);

        Set<String> undocumented = toolNames.stream()
                .filter(name -> !isDocumented(allDocs, name))
                .collect(Collectors.toSet());

        if (!undocumented.isEmpty()) {
            List<String> sorted = undocumented.stream().sorted().toList();
            fail("Missing documentation for MCP tools: " + String.join(", ", sorted)
                    + ". Please add them to dmtools-ai-docs/references/mcp-tools/*.md");
        }
    }

    private Set<String> collectToolNames(Path sourceRoot) throws IOException {
        Set<String> names = new HashSet<>();
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            List<Path> javaFiles = walk
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();
            for (Path file : javaFiles) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = TOOL_NAME_PATTERN.matcher(content);
                while (matcher.find()) {
                    names.add(matcher.group(1));
                }
            }
        }
        return names;
    }

    private String concatenateDocs(Path docsRoot) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> walk = Files.walk(docsRoot)) {
            List<Path> mdFiles = walk
                    .filter(p -> p.toString().endsWith(".md"))
                    .toList();
            for (Path file : mdFiles) {
                sb.append(Files.readString(file, StandardCharsets.UTF_8)).append('\n');
            }
        }
        return sb.toString();
    }

    private boolean isDocumented(String docs, String toolName) {
        return docs.contains("`" + toolName + "`")
                || docs.contains(toolName);
    }
}
