package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.KBStructureBuilder;
import com.github.istin.dmtools.common.kb.model.KBResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class KBAggregateOnlyServiceTest {

    private KBAggregationHelper aggregationHelper;
    private KBStructureBuilder structureBuilder;
    private KBStructureManager structureManager;
    private KBAggregateOnlyService aggregateOnlyService;
    private KBFileUtils fileUtils;
    private Logger logger;

    @BeforeEach
    void setUp() {
        aggregationHelper = mock(KBAggregationHelper.class);
        structureBuilder = mock(KBStructureBuilder.class);
        structureManager = mock(KBStructureManager.class);
        aggregateOnlyService = new KBAggregateOnlyService(aggregationHelper, structureManager);
        fileUtils = mock(KBFileUtils.class);
        logger = LogManager.getLogger(KBAggregateOnlyServiceTest.class);
    }

    @Test
    void aggregatesPeopleAndTopicsFromFilesystem() throws Exception {
        Path tempDir = Files.createTempDirectory("kb-aggregate-only-test");
        Path topicsDir = Files.createDirectories(tempDir.resolve("topics"));
        Files.createDirectories(tempDir.resolve("people").resolve("alice"));
        Files.createDirectories(tempDir.resolve("people").resolve("bob"));
        Files.writeString(topicsDir.resolve("mars.md"), "topic");
        Files.writeString(topicsDir.resolve("rocket.md"), "topic");

        KBResult expected = new KBResult();
        when(structureManager.buildResult(null, tempDir, fileUtils)).thenReturn(expected);

        KBResult result = aggregateOnlyService.aggregateExisting(tempDir, "extra", fileUtils, logger);

        assertEquals(expected, result);
        verify(aggregationHelper).aggregatePerson("alice", tempDir, "extra");
        verify(aggregationHelper).aggregatePerson("bob", tempDir, "extra");
        verify(aggregationHelper).aggregateTopicById("mars", tempDir, "extra");
        verify(aggregationHelper).aggregateTopicById("rocket", tempDir, "extra");
        verify(structureManager).generateIndexes(tempDir);
    }
}
