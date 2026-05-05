// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mermaid;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class MermaidToPngCommand {

    private MermaidToPngCommand() {
    }

    public static Path execute(String[] args) throws Exception {
        MermaidToPngRequest request = parse(args);
        return new MermaidToPngRenderer().renderToPng(request.definition(), request.outputPath());
    }

    static MermaidToPngRequest parse(String[] args) throws Exception {
        if (args == null || args.length < 2) {
            throw new IllegalArgumentException(usage());
        }

        StringBuilder definition = new StringBuilder();
        Path outputPath = null;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if ("--output".equals(arg) || "-o".equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for " + arg + ". " + usage());
                }
                outputPath = Paths.get(args[++i]);
            } else if ("--file".equals(arg) || "-f".equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for " + arg + ". " + usage());
                }
                definition.append(Files.readString(Paths.get(args[++i])));
            } else {
                if (!definition.isEmpty()) {
                    definition.append(' ');
                }
                definition.append(arg);
            }
        }

        String diagram = definition.toString().trim();
        if (diagram.isEmpty()) {
            throw new IllegalArgumentException("Mermaid diagram text is required. " + usage());
        }

        return new MermaidToPngRequest(diagram, outputPath);
    }

    private static String usage() {
        return "Usage: dmtools mermaid_to_png \"flowchart TD; A[Start] --> B[Done]\" [--output output.png]";
    }

    record MermaidToPngRequest(String definition, Path outputPath) {
    }
}
