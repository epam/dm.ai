// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mermaid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MermaidToPngCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void renderToSvgReturnsSvgForBasicFlowchart() throws Exception {
        String svg = new MermaidToPngRenderer().renderToSvg("flowchart TD; A[Start] --> B[Done]");

        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("Start"));
        assertTrue(svg.contains("Done"));
    }

    @Test
    void renderToPngWritesPngFile() throws Exception {
        Path outputPath = tempDir.resolve("diagram.png");

        Path result = new MermaidToPngRenderer().renderToPng("flowchart TD; A[Start] --> B[Done]", outputPath);

        assertEquals(outputPath.toAbsolutePath().normalize(), result);
        assertTrue(Files.size(outputPath) > 0);
        byte[] header = Files.readAllBytes(outputPath);
        assertArrayEquals(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}, new byte[]{header[0], header[1], header[2], header[3]});
    }

    @Test
    void commandUsesOutputArgument() throws Exception {
        Path outputPath = tempDir.resolve("cli-diagram.png");

        Path result = MermaidToPngCommand.execute(new String[]{
                "mermaid_to_png",
                "flowchart TD; A[Start] --> B[Done]",
                "--output",
                outputPath.toString()
        });

        assertEquals(outputPath.toAbsolutePath().normalize(), result);
        assertTrue(Files.exists(outputPath));
    }

    @Test
    void commandRequiresDiagramText() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> MermaidToPngCommand.execute(new String[]{"mermaid_to_png"})
        );

        assertTrue(exception.getMessage().contains("Usage: dmtools mermaid_to_png"));
    }
}
