// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.KBStructureBuilder;
import com.github.istin.dmtools.common.kb.agent.KBAggregationAgent;
import com.github.istin.dmtools.common.kb.params.AggregationParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KBAggregationHelperTest {

    private KBAggregationAgent aggregationAgent;
    private KBStructureBuilder structureBuilder;
    private KBContextLoader contextLoader;
    private KBAggregationHelper helper;

    @TempDir
    Path outputPath;

    @BeforeEach
    void setUp() {
        aggregationAgent = mock(KBAggregationAgent.class);
        structureBuilder = mock(KBStructureBuilder.class);
        contextLoader = mock(KBContextLoader.class);
        helper = new KBAggregationHelper(aggregationAgent, structureBuilder, contextLoader);
    }

    @Test
    void aggregatePersonReturnsEarlyWhenPersonFileMissing() throws Exception {
        when(structureBuilder.normalizePersonName("Alice")).thenReturn("alice");

        helper.aggregatePerson("Alice", outputPath, "extra");

        verify(aggregationAgent, never()).run(any());
        assertFalse(Files.exists(outputPath.resolve("people").resolve("alice").resolve("alice-desc.md")));
    }

    @Test
    void aggregatePersonGeneratesDescriptionWithLinkedItems() throws Exception {
        when(structureBuilder.normalizePersonName("Alice")).thenReturn("alice");
        when(aggregationAgent.run(any(AggregationParams.class))).thenReturn("AI person description");

        Path personDir = outputPath.resolve("people").resolve("alice");
        Files.createDirectories(personDir);
        String personContent = "---\nname: Alice\n---\n"
                + "Links: [[questions/q_1|Q1]] and [[answers/a_1|A1]] and [[notes/n_1|N1]]";
        Files.writeString(personDir.resolve("alice.md"), personContent);

        Files.createDirectories(outputPath.resolve("questions"));
        Files.writeString(outputPath.resolve("questions").resolve("q_1.md"), "question body");
        Files.createDirectories(outputPath.resolve("notes"));
        Files.writeString(outputPath.resolve("notes").resolve("n_1.md"), "note body");
        // answers/a_1.md intentionally missing to cover the not-exists branch

        helper.aggregatePerson("Alice", outputPath, "extra");

        verify(aggregationAgent).run(argThat(params ->
                "person".equals(params.getEntityType())
                        && "alice".equals(params.getEntityId())
                        && outputPath.equals(params.getKbPath())
                        && "extra".equals(params.getExtraInstructions())
                        && "Alice".equals(params.getEntityData().get("name"))
                        && params.getEntityData().get("content").toString().contains("question body")
                        && params.getEntityData().get("content").toString().contains("note body")));

        Path descFile = personDir.resolve("alice-desc.md");
        assertTrue(Files.exists(descFile));
        assertEquals("<!-- AI_CONTENT_START -->\nAI person description\n<!-- AI_CONTENT_END -->\n",
                Files.readString(descFile));
    }

    @Test
    void aggregateTopicReturnsEarlyWhenTopicFileMissing() throws Exception {
        when(structureBuilder.slugify("Mars")).thenReturn("mars");

        helper.aggregateTopic("Mars", outputPath, "extra");

        verify(aggregationAgent, never()).run(any());
        assertFalse(Files.exists(outputPath.resolve("topics").resolve("mars-desc.md")));
    }

    @Test
    void aggregateTopicGeneratesDescriptionWithEmbeddedItems() throws Exception {
        when(structureBuilder.slugify("Mars")).thenReturn("mars");
        when(aggregationAgent.run(any(AggregationParams.class))).thenReturn("AI topic description");

        Path topicsDir = outputPath.resolve("topics");
        Files.createDirectories(topicsDir);
        String topicContent = "---\ntitle: Mars\n---\n"
                + "Embeds: ![[q_1]] and ![[a_1]] and ![[n_1]]";
        Files.writeString(topicsDir.resolve("mars.md"), topicContent);

        Files.createDirectories(outputPath.resolve("questions"));
        Files.writeString(outputPath.resolve("questions").resolve("q_1.md"), "question body");
        Files.createDirectories(outputPath.resolve("answers"));
        Files.writeString(outputPath.resolve("answers").resolve("a_1.md"), "answer body");
        // notes/n_1.md intentionally missing to cover the not-exists branch

        helper.aggregateTopic("Mars", outputPath, "extra");

        verify(aggregationAgent).run(argThat(params ->
                "topic".equals(params.getEntityType())
                        && "mars".equals(params.getEntityId())
                        && outputPath.equals(params.getKbPath())
                        && "extra".equals(params.getExtraInstructions())
                        && "Mars".equals(params.getEntityData().get("title"))
                        && params.getEntityData().get("content").toString().contains("question body")
                        && params.getEntityData().get("content").toString().contains("answer body")));

        Path descFile = topicsDir.resolve("mars-desc.md");
        assertTrue(Files.exists(descFile));
        assertEquals("<!-- AI_CONTENT_START -->\nAI topic description\n<!-- AI_CONTENT_END -->\n",
                Files.readString(descFile));
    }

    @Test
    void aggregateTopicSkipsMetadataWhenNoFrontMatter() throws Exception {
        when(structureBuilder.slugify("Mars")).thenReturn("mars");
        when(aggregationAgent.run(any(AggregationParams.class))).thenReturn("desc");

        Path topicsDir = outputPath.resolve("topics");
        Files.createDirectories(topicsDir);
        Files.writeString(topicsDir.resolve("mars.md"), "no front matter here");

        helper.aggregateTopic("Mars", outputPath, null);

        verify(aggregationAgent).run(argThat(params ->
                !params.getEntityData().get("content").toString().contains("front matter here\n\n## ")));
        assertTrue(Files.exists(topicsDir.resolve("mars-desc.md")));
    }

    @Test
    void aggregateTopicByIdReturnsEarlyWhenTopicFileMissing() throws Exception {
        helper.aggregateTopicById("missing-topic", outputPath, "extra");

        verify(contextLoader, never()).extractTopicTitle(anyString(), anyString());
        verify(aggregationAgent, never()).run(any());
    }

    @Test
    void aggregateTopicByIdExtractsTitleAndDelegates() throws Exception {
        Path topicsDir = outputPath.resolve("topics");
        Files.createDirectories(topicsDir);
        Files.writeString(topicsDir.resolve("mars.md"), "---\ntitle: Mars\n---\nbody");

        when(contextLoader.extractTopicTitle(anyString(), eq("mars"))).thenReturn("Mars");
        when(structureBuilder.slugify("Mars")).thenReturn("mars");
        when(aggregationAgent.run(any(AggregationParams.class))).thenReturn("desc");

        helper.aggregateTopicById("mars", outputPath, "extra");

        verify(contextLoader).extractTopicTitle(anyString(), eq("mars"));
        verify(aggregationAgent).run(argThat(params ->
                "topic".equals(params.getEntityType()) && "mars".equals(params.getEntityId())));
        assertTrue(Files.exists(topicsDir.resolve("mars-desc.md")));
    }
}
