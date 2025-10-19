package com.github.istin.dmtools.common.kb;

import com.github.istin.dmtools.common.kb.model.Answer;
import com.github.istin.dmtools.common.kb.model.Question;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KBStatistics
 */
public class KBStatisticsTest {
    
    @Test
    void testGenerateStatistics(@TempDir Path tempDir) throws Exception {
        KBStatistics statistics = new KBStatistics();
        
        // Create some sample structure
        setupSampleKB(tempDir);
        
        // Generate statistics
        statistics.generateStatistics(tempDir);
        
        // Verify files created
        assertTrue(Files.exists(tempDir.resolve("stats")));
        assertTrue(Files.exists(tempDir.resolve("stats/activity_timeline.md")));
        assertTrue(Files.exists(tempDir.resolve("stats/topics_overview.md")));
        assertTrue(Files.exists(tempDir.resolve("INDEX.md")));
    }
    
    @Test
    void testGenerateIndex(@TempDir Path tempDir) throws Exception {
        KBStatistics statistics = new KBStatistics();
        
        setupSampleKB(tempDir);
        
        statistics.generateIndex(tempDir);
        
        Path indexFile = tempDir.resolve("INDEX.md");
        assertTrue(Files.exists(indexFile));
        
        String content = Files.readString(indexFile);
        assertTrue(content.contains("Knowledge Base Index"));
        assertTrue(content.contains("Quick Navigation"));
        assertTrue(content.contains("Statistics"));
    }
    
    @Test
    void testGenerateTopicOverview(@TempDir Path tempDir) throws Exception {
        KBStatistics statistics = new KBStatistics();
        
        setupSampleKB(tempDir);
        
        statistics.generateTopicOverview(tempDir);
        
        Path overviewFile = tempDir.resolve("stats/topics_overview.md");
        assertTrue(Files.exists(overviewFile));
        
        String content = Files.readString(overviewFile);
        assertTrue(content.contains("Topics Overview"));
        assertTrue(content.contains("| Topic | Questions | Answers | Notes | Total |"));
    }
    
    private void setupSampleKB(Path tempDir) throws Exception {
        // Create topics structure
        Path topicDir = tempDir.resolve("topics/sample-topic");
        Files.createDirectories(topicDir.resolve("questions"));
        Files.createDirectories(topicDir.resolve("answers"));
        Files.createDirectories(topicDir.resolve("notes"));
        
        // Create sample question
        Files.writeString(
                topicDir.resolve("questions/q_0001.md"),
                "---\ndate: 2024-10-10T12:00:00Z\n---\nQuestion"
        );
        
        // Create sample answer
        Files.writeString(
                topicDir.resolve("answers/a_0001.md"),
                "---\ndate: 2024-10-10T13:00:00Z\n---\nAnswer"
        );
        
        // Create people directory
        Path peopleDir = tempDir.resolve("people/John_Doe");
        Files.createDirectories(peopleDir);
    }
}


