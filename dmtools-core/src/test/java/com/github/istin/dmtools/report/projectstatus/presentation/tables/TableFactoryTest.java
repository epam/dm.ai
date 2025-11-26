package com.github.istin.dmtools.report.projectstatus.presentation.tables;

import com.github.istin.dmtools.report.projectstatus.config.ReportConfiguration;
import com.github.istin.dmtools.report.projectstatus.model.TableType;
import com.github.istin.dmtools.report.projectstatus.presentation.TableGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class TableFactoryTest {

    private ReportConfiguration config;
    private TableFactory tableFactory;

    @BeforeEach
    void setUp() {
        config = ReportConfiguration.builder()
                .completedStatuses(new String[]{"Done", "Closed"})
                .rolePrefixes(new String[]{"Dev:", "QA:"})
                .priorityOrder(new ArrayList<>())
                .issueTypeOrder(new ArrayList<>())
                .roleDescriptions(new HashMap<>())
                .build();
        tableFactory = new TableFactory(config);
    }

    @Test
    public void testConstructor_withValidConfig_initializesCorrectly() {
        // Arrange
        ReportConfiguration testConfig = ReportConfiguration.builder()
                .completedStatuses(new String[]{"Done"})
                .rolePrefixes(new String[]{"Dev:"})
                .priorityOrder(new ArrayList<>())
                .issueTypeOrder(new ArrayList<>())
                .roleDescriptions(new HashMap<>())
                .build();

        // Act
        TableFactory factory = new TableFactory(testConfig);

        // Assert
        assertNotNull(factory);
    }

    @Test
    public void testCreateGenerator_withSummaryType_returnsSummaryTableGenerator() {
        // Act
        TableGenerator generator = tableFactory.createGenerator(TableType.SUMMARY);

        // Assert
        assertNotNull(generator);
        assertTrue(generator instanceof SummaryTableGenerator);
    }

    @Test
    public void testCreateGenerator_withBugOverviewType_returnsBugTableGenerator() {
        // Act
        TableGenerator generator = tableFactory.createGenerator(TableType.BUG_OVERVIEW);

        // Assert
        assertNotNull(generator);
        assertTrue(generator instanceof BugTableGenerator);
    }

    @Test
    public void testCreateGenerator_withBugsTableType_returnsBugTableGenerator() {
        // Act
        TableGenerator generator = tableFactory.createGenerator(TableType.BUGS_TABLE);

        // Assert
        assertNotNull(generator);
        assertTrue(generator instanceof BugTableGenerator);
    }

    @Test
    public void testCreateGenerator_withStoryPointsDistributionType_returnsStoryPointsTableGenerator() {
        // Act
        TableGenerator generator = tableFactory.createGenerator(TableType.STORY_POINTS_DISTRIBUTION);

        // Assert
        assertNotNull(generator);
        assertTrue(generator instanceof StoryPointsTableGenerator);
    }

    @Test
    public void testCreateGenerator_withRoleDistributionType_returnsRoleDistributionTableGenerator() {
        // Act
        TableGenerator generator = tableFactory.createGenerator(TableType.ROLE_DISTRIBUTION);

        // Assert
        assertNotNull(generator);
        assertTrue(generator instanceof RoleDistributionTableGenerator);
    }

    @Test
    public void testCreateGenerator_withRoleSpecificType_returnsRoleDistributionTableGenerator() {
        // Act
        TableGenerator generator = tableFactory.createGenerator(TableType.ROLE_SPECIFIC);

        // Assert
        assertNotNull(generator);
        assertTrue(generator instanceof RoleDistributionTableGenerator);
    }

    @Test
    public void testCreateGenerator_withTasksAndStoriesType_returnsTasksAndStoriesTableGenerator() {
        // Act
        TableGenerator generator = tableFactory.createGenerator(TableType.TASKS_AND_STORIES);

        // Assert
        assertNotNull(generator);
        assertTrue(generator instanceof TasksAndStoriesTableGenerator);
    }

    @Test
    public void testCreateGenerator_withTimelineType_returnsTimelineTableGenerator() {
        // Act
        TableGenerator generator = tableFactory.createGenerator(TableType.TIMELINE);

        // Assert
        assertNotNull(generator);
        assertTrue(generator instanceof TimelineTableGenerator);
    }

    @Test
    public void testCreateTimelineTableGenerator_returnsTimelineTableGenerator() {
        // Act
        TimelineTableGenerator generator = tableFactory.createTimelineTableGenerator();

        // Assert
        assertNotNull(generator);
    }

    @Test
    public void testCreateSummaryTableGenerator_returnsSummaryTableGenerator() {
        // Act
        SummaryTableGenerator generator = tableFactory.createSummaryTableGenerator();

        // Assert
        assertNotNull(generator);
    }

    @Test
    public void testCreateBugTableGenerator_withCountStoryPointsFalse_returnsBugTableGenerator() {
        // Act
        BugTableGenerator generator = tableFactory.createBugTableGenerator(false);

        // Assert
        assertNotNull(generator);
    }

    @Test
    public void testCreateBugTableGenerator_withCountStoryPointsTrue_returnsBugTableGenerator() {
        // Act
        BugTableGenerator generator = tableFactory.createBugTableGenerator(true);

        // Assert
        assertNotNull(generator);
    }

    @Test
    public void testCreateStoryPointsTableGenerator_returnsStoryPointsTableGenerator() {
        // Act
        StoryPointsTableGenerator generator = tableFactory.createStoryPointsTableGenerator();

        // Assert
        assertNotNull(generator);
    }

    @Test
    public void testCreateRoleDistributionTableGenerator_returnsRoleDistributionTableGenerator() {
        // Act
        RoleDistributionTableGenerator generator = tableFactory.createRoleDistributionTableGenerator();

        // Assert
        assertNotNull(generator);
    }

    @Test
    public void testCreateTasksAndStoriesTableGenerator_returnsTasksAndStoriesTableGenerator() {
        // Act
        TasksAndStoriesTableGenerator generator = tableFactory.createTasksAndStoriesTableGenerator();

        // Assert
        assertNotNull(generator);
    }

    @Test
    public void testCreateLabelAnalysisGenerator_returnsLabelAnalysisGenerator() {
        // Act
        LabelAnalysisGenerator generator = tableFactory.createLabelAnalysisGenerator();

        // Assert
        assertNotNull(generator);
    }

    @Test
    public void testCreateGenerator_withBugOverviewAndBugsTable_returnsSameType() {
        // Act
        TableGenerator bugOverviewGenerator = tableFactory.createGenerator(TableType.BUG_OVERVIEW);
        TableGenerator bugsTableGenerator = tableFactory.createGenerator(TableType.BUGS_TABLE);

        // Assert
        assertTrue(bugOverviewGenerator instanceof BugTableGenerator);
        assertTrue(bugsTableGenerator instanceof BugTableGenerator);
    }

    @Test
    public void testCreateGenerator_withRoleDistributionAndRoleSpecific_returnsSameType() {
        // Act
        TableGenerator roleDistributionGenerator = tableFactory.createGenerator(TableType.ROLE_DISTRIBUTION);
        TableGenerator roleSpecificGenerator = tableFactory.createGenerator(TableType.ROLE_SPECIFIC);

        // Assert
        assertTrue(roleDistributionGenerator instanceof RoleDistributionTableGenerator);
        assertTrue(roleSpecificGenerator instanceof RoleDistributionTableGenerator);
    }

    @Test
    public void testCreateGenerator_withDifferentTypes_returnsDifferentInstances() {
        // Act
        TableGenerator summaryGenerator = tableFactory.createGenerator(TableType.SUMMARY);
        TableGenerator timelineGenerator = tableFactory.createGenerator(TableType.TIMELINE);

        // Assert
        assertNotNull(summaryGenerator);
        assertNotNull(timelineGenerator);
        assertNotSame(summaryGenerator, timelineGenerator);
        assertTrue(summaryGenerator instanceof SummaryTableGenerator);
        assertTrue(timelineGenerator instanceof TimelineTableGenerator);
    }

    @Test
    public void testCreateGenerator_withSameType_returnsNewInstances() {
        // Act
        TableGenerator generator1 = tableFactory.createGenerator(TableType.SUMMARY);
        TableGenerator generator2 = tableFactory.createGenerator(TableType.SUMMARY);

        // Assert
        assertNotNull(generator1);
        assertNotNull(generator2);
        assertNotSame(generator1, generator2);
    }

    @Test
    public void testCreateBugTableGenerator_withDifferentFlags_returnsDifferentInstances() {
        // Act
        BugTableGenerator generator1 = tableFactory.createBugTableGenerator(false);
        BugTableGenerator generator2 = tableFactory.createBugTableGenerator(true);

        // Assert
        assertNotNull(generator1);
        assertNotNull(generator2);
        assertNotSame(generator1, generator2);
    }
}
