package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.model.KBResult;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for KBAggregateOnlyService with smart mode.
 */
@ExtendWith(MockitoExtension.class)
public class KBAggregateOnlyServiceTest {

    @Mock
    private KBAggregationHelper aggregationHelper;

    @Mock
    private KBStructureManager structureManager;

    @Mock
    private KBFileUtils fileUtils;

    @Mock
    private Logger logger;

    private KBAggregateOnlyService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() {
        service = new KBAggregateOnlyService(aggregationHelper, structureManager);
    }

    @Test
    public void testAggregateExisting_SmartMode_SkipsUnchanged() throws Exception {
        // Setup: Create person with old Q/A/N and newer description
        Path peopleDir = tempDir.resolve("people/john_doe");
        Files.createDirectories(peopleDir);
        
        // Create Q/A/N file (old)
        Path questionsDir = tempDir.resolve("questions");
        Files.createDirectories(questionsDir);
        Path qanFile = questionsDir.resolve("q-abc123.md");
        Files.writeString(qanFile, "Question content");
        Thread.sleep(100);
        
        // Create entity and description (newer)
        Path entityFile = peopleDir.resolve("john_doe.md");
        Files.writeString(entityFile, "# John Doe\n\n[Q-abc123]");
        
        Path descFile = peopleDir.resolve("john_doe-desc.md");
        Files.writeString(descFile, "Description");
        
        // Mock structure manager
        KBResult mockResult = new KBResult();
        mockResult.setSuccess(true);
        when(structureManager.buildResult(any(), any(), any())).thenReturn(mockResult);
        
        // Test: Run with smart mode enabled
        KBResult result = service.aggregateExisting(tempDir, null, fileUtils, logger, true);
        
        // Verify: aggregatePerson should NOT be called (skipped due to no changes)
        verify(aggregationHelper, never()).aggregatePerson(eq("john_doe"), any(), any());
        verify(structureManager).generateIndexes(tempDir);
        assertTrue(result.isSuccess());
    }

    @Test
    public void testAggregateExisting_SmartMode_RegeneratesChanged() throws Exception {
        // Setup: Create person with new Q/A/N and old description
        Path peopleDir = tempDir.resolve("people/john_doe");
        Files.createDirectories(peopleDir);
        
        // Create description (old)
        Path descFile = peopleDir.resolve("john_doe-desc.md");
        Files.writeString(descFile, "Description");
        Thread.sleep(100);
        
        // Create entity
        Path entityFile = peopleDir.resolve("john_doe.md");
        Files.writeString(entityFile, "# John Doe\n\n[Q-abc123]");
        
        // Create Q/A/N file (newer)
        Path questionsDir = tempDir.resolve("questions");
        Files.createDirectories(questionsDir);
        Path qanFile = questionsDir.resolve("q-abc123.md");
        Files.writeString(qanFile, "Question content");
        
        // Mock structure manager
        KBResult mockResult = new KBResult();
        mockResult.setSuccess(true);
        when(structureManager.buildResult(any(), any(), any())).thenReturn(mockResult);
        
        // Test: Run with smart mode enabled
        KBResult result = service.aggregateExisting(tempDir, null, fileUtils, logger, true);
        
        // Verify: aggregatePerson SHOULD be called (Q/A/N changed)
        verify(aggregationHelper, times(1)).aggregatePerson(eq("john_doe"), eq(tempDir), isNull());
        verify(structureManager).generateIndexes(tempDir);
        assertTrue(result.isSuccess());
    }

    @Test
    public void testAggregateExisting_NonSmartMode_RegeneratesAll() throws Exception {
        // Setup: Create person (regardless of timestamps)
        Path peopleDir = tempDir.resolve("people/john_doe");
        Files.createDirectories(peopleDir);
        
        Path entityFile = peopleDir.resolve("john_doe.md");
        Files.writeString(entityFile, "# John Doe\n\n[Q-abc123]");
        
        Path descFile = peopleDir.resolve("john_doe-desc.md");
        Files.writeString(descFile, "Description");
        
        Path questionsDir = tempDir.resolve("questions");
        Files.createDirectories(questionsDir);
        Path qanFile = questionsDir.resolve("q-abc123.md");
        Files.writeString(qanFile, "Question content");
        
        // Mock structure manager
        KBResult mockResult = new KBResult();
        mockResult.setSuccess(true);
        when(structureManager.buildResult(any(), any(), any())).thenReturn(mockResult);
        
        // Test: Run with smart mode DISABLED
        KBResult result = service.aggregateExisting(tempDir, null, fileUtils, logger, false);
        
        // Verify: aggregatePerson SHOULD be called (always regenerate in non-smart mode)
        verify(aggregationHelper, times(1)).aggregatePerson(eq("john_doe"), eq(tempDir), isNull());
        verify(structureManager).generateIndexes(tempDir);
        assertTrue(result.isSuccess());
    }

    @Test
    public void testAggregateExisting_SmartMode_NoDescription() throws Exception {
        // Setup: Create person with Q/A/N but no description file
        Path peopleDir = tempDir.resolve("people/john_doe");
        Files.createDirectories(peopleDir);
        
        Path entityFile = peopleDir.resolve("john_doe.md");
        Files.writeString(entityFile, "# John Doe\n\n[Q-abc123]");
        
        // No description file created
        
        Path questionsDir = tempDir.resolve("questions");
        Files.createDirectories(questionsDir);
        Path qanFile = questionsDir.resolve("q-abc123.md");
        Files.writeString(qanFile, "Question content");
        
        // Mock structure manager
        KBResult mockResult = new KBResult();
        mockResult.setSuccess(true);
        when(structureManager.buildResult(any(), any(), any())).thenReturn(mockResult);
        
        // Test: Run with smart mode enabled
        KBResult result = service.aggregateExisting(tempDir, null, fileUtils, logger, true);
        
        // Verify: aggregatePerson SHOULD be called (no description exists)
        verify(aggregationHelper, times(1)).aggregatePerson(eq("john_doe"), eq(tempDir), isNull());
        verify(structureManager).generateIndexes(tempDir);
        assertTrue(result.isSuccess());
    }

    @Test
    public void testAggregateExisting_SmartMode_Topics() throws Exception {
        // Setup: Create topic with old Q/A/N and newer description
        Path topicsDir = tempDir.resolve("topics");
        Files.createDirectories(topicsDir);
        
        // Create Q/A/N file (old)
        Path questionsDir = tempDir.resolve("questions");
        Files.createDirectories(questionsDir);
        Path qanFile = questionsDir.resolve("q-abc123.md");
        Files.writeString(qanFile, "Question content");
        Thread.sleep(100);
        
        // Create topic and description (newer)
        Path topicFile = topicsDir.resolve("topic_ai.md");
        Files.writeString(topicFile, "# AI\n\n[Q-abc123]");
        
        Path descFile = topicsDir.resolve("topic_ai-desc.md");
        Files.writeString(descFile, "AI Description");
        
        // Mock structure manager
        KBResult mockResult = new KBResult();
        mockResult.setSuccess(true);
        when(structureManager.buildResult(any(), any(), any())).thenReturn(mockResult);
        
        // Test: Run with smart mode enabled
        KBResult result = service.aggregateExisting(tempDir, null, fileUtils, logger, true);
        
        // Verify: aggregateTopicById should NOT be called (skipped due to no changes)
        verify(aggregationHelper, never()).aggregateTopicById(eq("topic_ai"), any(), any());
        verify(structureManager).generateIndexes(tempDir);
        assertTrue(result.isSuccess());
    }

    @Test
    public void testAggregateExisting_SmartMode_MultiplePeopleAndTopics() throws Exception {
        // Setup: Create multiple people and topics with mixed timestamps
        
        // Person 1: Old Q/A/N, new description (should skip)
        Path person1Dir = tempDir.resolve("people/person1");
        Files.createDirectories(person1Dir);
        Path q1Dir = tempDir.resolve("questions");
        Files.createDirectories(q1Dir);
        Path q1File = q1Dir.resolve("q-1.md");
        Files.writeString(q1File, "Q1");
        Thread.sleep(100);
        Files.writeString(person1Dir.resolve("person1.md"), "[Q-1]");
        Files.writeString(person1Dir.resolve("person1-desc.md"), "Desc1");
        
        // Person 2: New Q/A/N, old description (should regenerate)
        Path person2Dir = tempDir.resolve("people/person2");
        Files.createDirectories(person2Dir);
        Files.writeString(person2Dir.resolve("person2-desc.md"), "Desc2");
        Thread.sleep(100);
        Files.writeString(person2Dir.resolve("person2.md"), "[Q-2]");
        Path q2File = q1Dir.resolve("q-2.md");
        Files.writeString(q2File, "Q2");
        
        // Topic: No description (should regenerate)
        Path topicsDir = tempDir.resolve("topics");
        Files.createDirectories(topicsDir);
        Files.writeString(topicsDir.resolve("topic1.md"), "[Q-3]");
        Path q3File = q1Dir.resolve("q-3.md");
        Files.writeString(q3File, "Q3");
        
        // Mock structure manager
        KBResult mockResult = new KBResult();
        mockResult.setSuccess(true);
        when(structureManager.buildResult(any(), any(), any())).thenReturn(mockResult);
        
        // Test: Run with smart mode enabled
        KBResult result = service.aggregateExisting(tempDir, null, fileUtils, logger, true);
        
        // Verify: Only person2 and topic1 should be regenerated
        verify(aggregationHelper, never()).aggregatePerson(eq("person1"), any(), any());
        verify(aggregationHelper, times(1)).aggregatePerson(eq("person2"), eq(tempDir), isNull());
        verify(aggregationHelper, times(1)).aggregateTopicById(eq("topic1"), eq(tempDir), isNull());
        verify(structureManager).generateIndexes(tempDir);
        assertTrue(result.isSuccess());
    }

    @Test
    public void testAggregateExisting_DefaultOverload_UsesNonSmartMode() throws Exception {
        // Setup: Create person
        Path peopleDir = tempDir.resolve("people/john_doe");
        Files.createDirectories(peopleDir);
        Files.writeString(peopleDir.resolve("john_doe.md"), "[Q-1]");
        
        Path questionsDir = tempDir.resolve("questions");
        Files.createDirectories(questionsDir);
        Files.writeString(questionsDir.resolve("q-1.md"), "Q1");
        
        // Mock structure manager
        KBResult mockResult = new KBResult();
        mockResult.setSuccess(true);
        when(structureManager.buildResult(any(), any(), any())).thenReturn(mockResult);
        
        // Test: Call default overload (without smartMode parameter)
        KBResult result = service.aggregateExisting(tempDir, null, fileUtils, logger);
        
        // Verify: Should use non-smart mode (always regenerate)
        verify(aggregationHelper, times(1)).aggregatePerson(eq("john_doe"), eq(tempDir), isNull());
        verify(structureManager).generateIndexes(tempDir);
        assertTrue(result.isSuccess());
    }
}
