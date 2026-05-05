// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.mermaid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MermaidToPngCommandTest {

    @TempDir
    Path tempDir;

    static Stream<DiagramFixture> supportedDiagrams() {
        return Stream.of(
                new DiagramFixture(
                        "flowchart",
                        "flowchart TD; A[Start] --> B{Check}; B -->|Yes| C[Done]; B -->|No| D[Retry]",
                        "Check"
                ),
                new DiagramFixture(
                        "sequence",
                        """
                        sequenceDiagram
                        participant User
                        participant API
                        participant Jira
                        User->>API: Request status
                        API->>Jira: Fetch issue
                        Jira-->>API: Issue payload
                        API-->>User: Render response
                        """,
                        "Request status"
                ),
                new DiagramFixture(
                        "class",
                        """
                        classDiagram
                        class Story
                        Story : +String key
                        Story : +Status status
                        class Blocker
                        Blocker : +resolve()
                        Story --> Blocker : blocked by
                        """,
                        "Story"
                ),
                new DiagramFixture(
                        "state",
                        """
                        stateDiagram-v2
                        [*] --> Blocked
                        Blocked --> Backlog : blockers done
                        Backlog --> InProgress : sprint starts
                        InProgress --> Done : accepted
                        Done --> [*]
                        """,
                        "Blocked"
                )
        );
    }

    @Test
    void renderToSvgReturnsSvgForBasicFlowchart() throws Exception {
        String svg = new MermaidToPngRenderer().renderToSvg("flowchart TD; A[Start] --> B[Done]");

        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("Start"));
        assertTrue(svg.contains("Done"));
    }

    @ParameterizedTest
    @MethodSource("supportedDiagrams")
    void renderToSvgSupportsDiagramTypes(DiagramFixture fixture) throws Exception {
        String svg = new MermaidToPngRenderer().renderToSvg(fixture.definition());

        assertTrue(svg.contains("<svg"), fixture.name());
        assertTrue(svg.contains(fixture.expectedText()), fixture.name());
    }

    @ParameterizedTest
    @MethodSource("supportedDiagrams")
    void renderToPngSupportsDiagramTypes(DiagramFixture fixture) throws Exception {
        Path outputPath = tempDir.resolve(fixture.name() + ".png");

        Path result = new MermaidToPngRenderer().renderToPng(fixture.definition(), outputPath);

        assertEquals(outputPath.toAbsolutePath().normalize(), result);
        assertPng(outputPath);
    }

    @Test
    void renderToPngWritesPngFile() throws Exception {
        Path outputPath = tempDir.resolve("diagram.png");

        Path result = new MermaidToPngRenderer().renderToPng("flowchart TD; A[Start] --> B[Done]", outputPath);

        assertEquals(outputPath.toAbsolutePath().normalize(), result);
        assertPng(outputPath);
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

    @Test
    void renderToSvgRejectsUnsupportedDiagramTypes() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new MermaidToPngRenderer().renderToPng("pie title Pets", tempDir.resolve("pie.png"))
        );

        assertTrue(exception.getMessage().contains("Unsupported Mermaid diagram type"));
    }

    private void assertPng(Path outputPath) throws Exception {
        assertTrue(Files.size(outputPath) > 0);
        byte[] header = Files.readAllBytes(outputPath);
        assertArrayEquals(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}, new byte[]{header[0], header[1], header[2], header[3]});
    }

    record DiagramFixture(String name, String definition, String expectedText) {
    }
}
