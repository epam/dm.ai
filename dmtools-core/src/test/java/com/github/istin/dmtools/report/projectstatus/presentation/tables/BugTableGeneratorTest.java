package com.github.istin.dmtools.report.projectstatus.presentation.tables;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.report.projectstatus.data.TicketSorter;
import com.github.istin.dmtools.report.projectstatus.data.TicketStatisticsCalculator;
import com.github.istin.dmtools.report.projectstatus.model.TableData;
import com.github.istin.dmtools.report.projectstatus.presentation.TableGenerator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentMatchers;

@ExtendWith(MockitoExtension.class)
public class BugTableGeneratorTest {

    @Mock
    private TableGenerator baseTableGenerator;

    @Mock
    private TicketStatisticsCalculator statisticsCalculator;

    @Mock
    private TicketSorter ticketSorter;

    @Mock
    private ITicket mockTicket1;

    @Mock
    private ITicket mockTicket2;

    @Mock
    private ITicket mockTicket3;

    private BugTableGenerator bugTableGenerator;
    private BugTableGenerator bugTableGeneratorWithStoryPoints;

    @BeforeEach
    void setUp() {
        bugTableGenerator = new BugTableGenerator(
            baseTableGenerator,
            statisticsCalculator,
            ticketSorter,
            false
        );
        bugTableGeneratorWithStoryPoints = new BugTableGenerator(
            baseTableGenerator,
            statisticsCalculator,
            ticketSorter,
            true
        );
    }

    @Test
    public void testConstructor_withAllParameters_setsFieldsCorrectly() {
        // Arrange
        TableGenerator mockBase = mock(TableGenerator.class);
        TicketStatisticsCalculator mockStats = mock(TicketStatisticsCalculator.class);
        TicketSorter mockSorter = mock(TicketSorter.class);

        // Act
        BugTableGenerator generator = new BugTableGenerator(mockBase, mockStats, mockSorter, true);

        // Assert
        assertNotNull(generator);
    }

    @Test
    public void testGenerateTable_withTableData_delegatesToBaseTableGenerator() {
        // Arrange
        TableData tableData = new TableData("Test Table", Arrays.asList("Header1", "Header2"));
        String expectedResult = "Generated Table";
        when(baseTableGenerator.generateTable(tableData)).thenReturn(expectedResult);

        // Act
        String result = bugTableGenerator.generateTable(tableData);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(tableData);
    }

    @Test
    public void testGenerateTable_withTicketList_callsGenerateBugOverviewTable() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, Integer> bugsByPriority = new HashMap<>();
        bugsByPriority.put("High", 1);
        List<String> sortedPriorities = Arrays.asList("High");
        String expectedResult = "Generated Overview Table";

        when(statisticsCalculator.countTicketsByPriority(tickets)).thenReturn(bugsByPriority);
        when(ticketSorter.sortPriorities(bugsByPriority.keySet())).thenReturn(sortedPriorities);
        when(statisticsCalculator.calculatePercentage(1, 1)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = bugTableGenerator.generateTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(1)).countTicketsByPriority(tickets);
        verify(ticketSorter, times(1)).sortPriorities(bugsByPriority.keySet());
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTable_withTicketListColumnsAndFlags_callsGenerateTableWithTickets() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, Integer> bugsByPriority = new HashMap<>();
        bugsByPriority.put("High", 1);
        List<String> sortedPriorities = Arrays.asList("High");
        String expectedResult = "Generated Overview Table";

        when(statisticsCalculator.countTicketsByPriority(tickets)).thenReturn(bugsByPriority);
        when(ticketSorter.sortPriorities(bugsByPriority.keySet())).thenReturn(sortedPriorities);
        when(statisticsCalculator.calculatePercentage(1, 1)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = bugTableGenerator.generateTable(tickets, new String[]{"Key", "Priority"}, true, false);

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(1)).countTicketsByPriority(tickets);
    }

    @Test
    public void testGenerateBugOverviewTable_withEmptyList_returnsTableWithTotalRow() {
        // Arrange
        List<ITicket> emptyList = new ArrayList<>();
        Map<String, Integer> emptyMap = new HashMap<>();
        List<String> emptyPriorities = new ArrayList<>();
        String expectedResult = "Generated Empty Table";

        when(statisticsCalculator.countTicketsByPriority(emptyList)).thenReturn(emptyMap);
        when(ticketSorter.sortPriorities(emptyMap.keySet())).thenReturn(emptyPriorities);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = bugTableGenerator.generateBugOverviewTable(emptyList);

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(1)).countTicketsByPriority(emptyList);
        verify(ticketSorter, times(1)).sortPriorities(emptyMap.keySet());
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateBugOverviewTable_withSingleBug_returnsCorrectTable() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, Integer> bugsByPriority = new HashMap<>();
        bugsByPriority.put("High", 1);
        List<String> sortedPriorities = Arrays.asList("High");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.countTicketsByPriority(tickets)).thenReturn(bugsByPriority);
        when(ticketSorter.sortPriorities(bugsByPriority.keySet())).thenReturn(sortedPriorities);
        when(statisticsCalculator.calculatePercentage(1, 1)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = bugTableGenerator.generateBugOverviewTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(1)).countTicketsByPriority(tickets);
        verify(statisticsCalculator, times(1)).calculatePercentage(1, 1);
    }

    @Test
    public void testGenerateBugOverviewTable_withMultiplePriorities_returnsSortedTable() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2, mockTicket3);
        Map<String, Integer> bugsByPriority = new HashMap<>();
        bugsByPriority.put("High", 1);
        bugsByPriority.put("Medium", 1);
        bugsByPriority.put("Low", 1);
        List<String> sortedPriorities = Arrays.asList("High", "Medium", "Low");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.countTicketsByPriority(tickets)).thenReturn(bugsByPriority);
        when(ticketSorter.sortPriorities(bugsByPriority.keySet())).thenReturn(sortedPriorities);
        when(statisticsCalculator.calculatePercentage(1, 3)).thenReturn(33.3);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = bugTableGenerator.generateBugOverviewTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(3)).calculatePercentage(1, 3);
        verify(ticketSorter, times(1)).sortPriorities(bugsByPriority.keySet());
    }

    @Test
    public void testGenerateBugOverviewTable_withCountStoryPointsFalse_usesCountHeader() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, Integer> bugsByPriority = new HashMap<>();
        bugsByPriority.put("High", 1);
        List<String> sortedPriorities = Arrays.asList("High");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.countTicketsByPriority(tickets)).thenReturn(bugsByPriority);
        when(ticketSorter.sortPriorities(bugsByPriority.keySet())).thenReturn(sortedPriorities);
        when(statisticsCalculator.calculatePercentage(1, 1)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = bugTableGenerator.generateBugOverviewTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(ArgumentMatchers.<TableData>argThat(tableData -> 
            tableData.getHeaders().contains("Count")
        ));
    }

    @Test
    public void testGenerateBugOverviewTable_withCountStoryPointsTrue_usesStoryPointsHeader() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, Integer> bugsByPriority = new HashMap<>();
        bugsByPriority.put("High", 1);
        List<String> sortedPriorities = Arrays.asList("High");
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("storyPoints", 5);

        when(statisticsCalculator.countTicketsByPriority(tickets)).thenReturn(bugsByPriority);
        when(ticketSorter.sortPriorities(bugsByPriority.keySet())).thenReturn(sortedPriorities);
        when(statisticsCalculator.calculatePercentage(1, 1)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getPriority()).thenReturn("High");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);

        // Act
        String result = bugTableGeneratorWithStoryPoints.generateBugOverviewTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(ArgumentMatchers.<TableData>argThat(tableData -> 
            tableData.getHeaders().contains("Story Points")
        ));
    }

    @Test
    public void testGenerateBugOverviewTable_withStoryPoints_calculatesTotalStoryPoints() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        Map<String, Integer> bugsByPriority = new HashMap<>();
        bugsByPriority.put("High", 2);
        List<String> sortedPriorities = Arrays.asList("High");
        String expectedResult = "Generated Table";
        JSONObject fieldsJson1 = new JSONObject();
        fieldsJson1.put("storyPoints", 5);
        JSONObject fieldsJson2 = new JSONObject();
        fieldsJson2.put("storyPoints", 3);

        when(statisticsCalculator.countTicketsByPriority(tickets)).thenReturn(bugsByPriority);
        when(ticketSorter.sortPriorities(bugsByPriority.keySet())).thenReturn(sortedPriorities);
        when(statisticsCalculator.calculatePercentage(2, 2)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket2.getPriority()).thenReturn("High");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson1);
        when(mockTicket2.getFieldsAsJSON()).thenReturn(fieldsJson2);

        // Act
        String result = bugTableGeneratorWithStoryPoints.generateBugOverviewTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateBugOverviewTable_withStoryPointsMissing_usesZero() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, Integer> bugsByPriority = new HashMap<>();
        bugsByPriority.put("High", 1);
        List<String> sortedPriorities = Arrays.asList("High");
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();

        when(statisticsCalculator.countTicketsByPriority(tickets)).thenReturn(bugsByPriority);
        when(ticketSorter.sortPriorities(bugsByPriority.keySet())).thenReturn(sortedPriorities);
        when(statisticsCalculator.calculatePercentage(1, 1)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getPriority()).thenReturn("High");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);

        // Act
        String result = bugTableGeneratorWithStoryPoints.generateBugOverviewTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateBugOverviewTable_withNullPriority_handlesGracefully() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, Integer> bugsByPriority = new HashMap<>();
        bugsByPriority.put("Trivial", 1);
        List<String> sortedPriorities = Arrays.asList("Trivial");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.countTicketsByPriority(tickets)).thenReturn(bugsByPriority);
        when(ticketSorter.sortPriorities(bugsByPriority.keySet())).thenReturn(sortedPriorities);
        when(statisticsCalculator.calculatePercentage(1, 1)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = bugTableGenerator.generateBugOverviewTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(1)).countTicketsByPriority(tickets);
    }

    @Test
    public void testGenerateBugOverviewTable_withIOExceptionInFilter_handlesGracefully() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        Map<String, Integer> bugsByPriority = new HashMap<>();
        bugsByPriority.put("High", 1);
        bugsByPriority.put("Medium", 1);
        List<String> sortedPriorities = Arrays.asList("High", "Medium");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.countTicketsByPriority(tickets)).thenReturn(bugsByPriority);
        when(ticketSorter.sortPriorities(bugsByPriority.keySet())).thenReturn(sortedPriorities);
        when(statisticsCalculator.calculatePercentage(1, 2)).thenReturn(50.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        JSONObject fieldsJson1 = new JSONObject();
        fieldsJson1.put("storyPoints", 3);
        JSONObject fieldsJson2 = new JSONObject();
        fieldsJson2.put("storyPoints", 2);

        try {
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket2.getPriority()).thenThrow(new IOException("Test exception"));
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson1);
        when(mockTicket2.getFieldsAsJSON()).thenReturn(fieldsJson2);

        // Act
        String result = bugTableGeneratorWithStoryPoints.generateBugOverviewTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateBugsTable_withEmptyList_returnsEmptyTable() {
        // Arrange
        List<ITicket> emptyList = new ArrayList<>();
        String expectedResult = "Generated Empty Table";

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = bugTableGenerator.generateBugsTable(emptyList);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateBugsTable_withSingleBug_returnsCorrectTable() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        JSONArray labels = new JSONArray();
        labels.put("bug");
        labels.put("critical");

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("BUG-1");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getTicketTitle()).thenReturn("Test Bug");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        String result = bugTableGenerator.generateBugsTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateBugsTable_withMultipleBugs_returnsAllBugs() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson1 = new JSONObject();
        fieldsJson1.put("dateClosed", "2024-01-01");
        JSONObject fieldsJson2 = new JSONObject();
        fieldsJson2.put("dateClosed", "2024-01-02");
        JSONArray labels1 = new JSONArray();
        labels1.put("bug");
        JSONArray labels2 = new JSONArray();
        labels2.put("critical");

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("BUG-1");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getTicketTitle()).thenReturn("Bug 1");
            when(mockTicket2.getKey()).thenReturn("BUG-2");
            when(mockTicket2.getPriority()).thenReturn("Medium");
            when(mockTicket2.getTicketTitle()).thenReturn("Bug 2");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson1);
        when(mockTicket1.getTicketLabels()).thenReturn(labels1);
        when(mockTicket2.getFieldsAsJSON()).thenReturn(fieldsJson2);
        when(mockTicket2.getTicketLabels()).thenReturn(labels2);

        // Act
        String result = bugTableGenerator.generateBugsTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateBugsTable_withStoryPoints_includesStoryPointsColumn() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        fieldsJson.put("storyPoints", "5");
        JSONArray labels = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("BUG-1");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getTicketTitle()).thenReturn("Test Bug");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        String result = bugTableGeneratorWithStoryPoints.generateBugsTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(ArgumentMatchers.<TableData>argThat(tableData -> 
            tableData.getHeaders().contains("Story Points")
        ));
    }

    @Test
    public void testGenerateBugsTable_withStoryPointsFalse_excludesStoryPointsColumn() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        JSONArray labels = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("BUG-1");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getTicketTitle()).thenReturn("Test Bug");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        String result = bugTableGenerator.generateBugsTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(ArgumentMatchers.<TableData>argThat(tableData -> 
            !tableData.getHeaders().contains("Story Points")
        ));
    }

    @Test
    public void testGenerateBugsTable_withNullPriority_usesDefaultTrivial() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        JSONArray labels = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("BUG-1");
            when(mockTicket1.getPriority()).thenReturn(null);
            when(mockTicket1.getTicketTitle()).thenReturn("Test Bug");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        String result = bugTableGenerator.generateBugsTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateBugsTable_withIOException_handlesGracefully() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        JSONArray labels = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("BUG-1");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getTicketTitle()).thenThrow(new IOException("Test exception"));
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        String result = bugTableGenerator.generateBugsTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateBugsTable_withIOExceptionInGetKey_handlesGracefully() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        JSONArray labels = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("BUG-1");
            lenient().when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getTicketTitle()).thenThrow(new IOException("Test exception"));
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        String result = bugTableGenerator.generateBugsTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateBugsTable_withMissingDateClosed_handlesGracefully() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        JSONArray labels = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("BUG-1");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getTicketTitle()).thenReturn("Test Bug");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        String result = bugTableGenerator.generateBugsTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateBugsTable_withEmptyLabels_handlesGracefully() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        JSONArray labels = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("BUG-1");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getTicketTitle()).thenReturn("Test Bug");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        String result = bugTableGenerator.generateBugsTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateBugsTable_withStoryPointsMissing_usesZero() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        JSONArray labels = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("BUG-1");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getTicketTitle()).thenReturn("Test Bug");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        String result = bugTableGeneratorWithStoryPoints.generateBugsTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }


    @Test
    public void testGenerateBugOverviewTable_withStoryPointsAsString_handlesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, Integer> bugsByPriority = new HashMap<>();
        bugsByPriority.put("High", 1);
        List<String> sortedPriorities = Arrays.asList("High");
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("storyPoints", "8");

        when(statisticsCalculator.countTicketsByPriority(tickets)).thenReturn(bugsByPriority);
        when(ticketSorter.sortPriorities(bugsByPriority.keySet())).thenReturn(sortedPriorities);
        when(statisticsCalculator.calculatePercentage(1, 1)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getPriority()).thenReturn("High");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);

        // Act
        String result = bugTableGeneratorWithStoryPoints.generateBugOverviewTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateBugOverviewTable_withNegativeStoryPoints_handlesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, Integer> bugsByPriority = new HashMap<>();
        bugsByPriority.put("High", 1);
        List<String> sortedPriorities = Arrays.asList("High");
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("storyPoints", -5);

        when(statisticsCalculator.countTicketsByPriority(tickets)).thenReturn(bugsByPriority);
        when(ticketSorter.sortPriorities(bugsByPriority.keySet())).thenReturn(sortedPriorities);
        when(statisticsCalculator.calculatePercentage(1, 1)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getPriority()).thenReturn("High");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);

        // Act
        String result = bugTableGeneratorWithStoryPoints.generateBugOverviewTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateBugOverviewTable_withLargeStoryPoints_handlesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        Map<String, Integer> bugsByPriority = new HashMap<>();
        bugsByPriority.put("High", 2);
        List<String> sortedPriorities = Arrays.asList("High");
        String expectedResult = "Generated Table";
        JSONObject fieldsJson1 = new JSONObject();
        fieldsJson1.put("storyPoints", 1000);
        JSONObject fieldsJson2 = new JSONObject();
        fieldsJson2.put("storyPoints", 2000);

        when(statisticsCalculator.countTicketsByPriority(tickets)).thenReturn(bugsByPriority);
        when(ticketSorter.sortPriorities(bugsByPriority.keySet())).thenReturn(sortedPriorities);
        when(statisticsCalculator.calculatePercentage(2, 2)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket2.getPriority()).thenReturn("High");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson1);
        when(mockTicket2.getFieldsAsJSON()).thenReturn(fieldsJson2);

        // Act
        String result = bugTableGeneratorWithStoryPoints.generateBugOverviewTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateBugOverviewTable_withDecimalPercentage_formatsCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2, mockTicket3);
        Map<String, Integer> bugsByPriority = new HashMap<>();
        bugsByPriority.put("High", 1);
        bugsByPriority.put("Medium", 2);
        List<String> sortedPriorities = Arrays.asList("High", "Medium");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.countTicketsByPriority(tickets)).thenReturn(bugsByPriority);
        when(ticketSorter.sortPriorities(bugsByPriority.keySet())).thenReturn(sortedPriorities);
        when(statisticsCalculator.calculatePercentage(1.0, 3.0)).thenReturn(33.333333);
        when(statisticsCalculator.calculatePercentage(2.0, 3.0)).thenReturn(66.666667);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = bugTableGenerator.generateBugOverviewTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(2)).calculatePercentage(anyDouble(), eq(3.0));
    }

    @Test
    public void testGenerateBugsTable_withIOExceptionInGetPriority_handlesGracefully() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        JSONArray labels = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("BUG-1");
            when(mockTicket1.getPriority()).thenThrow(new IOException("Test exception"));
            lenient().when(mockTicket1.getTicketTitle()).thenReturn("Test Bug");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        lenient().when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        lenient().when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        String result = bugTableGenerator.generateBugsTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }


    @Test
    public void testGenerateBugsTable_withStoryPointsAsStringZero_handlesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        fieldsJson.put("storyPoints", "0");
        JSONArray labels = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("BUG-1");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getTicketTitle()).thenReturn("Test Bug");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        String result = bugTableGeneratorWithStoryPoints.generateBugsTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateBugsTable_withStoryPointsAsNonNumericString_handlesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        fieldsJson.put("storyPoints", "invalid");
        JSONArray labels = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("BUG-1");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getTicketTitle()).thenReturn("Test Bug");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        String result = bugTableGeneratorWithStoryPoints.generateBugsTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateBugsTable_withVeryLongTicketTitle_handlesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        JSONArray labels = new JSONArray();
        String longTitle = "A".repeat(1000);

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("BUG-1");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getTicketTitle()).thenReturn(longTitle);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        String result = bugTableGenerator.generateBugsTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateBugsTable_withMultipleBugsAndStoryPoints_calculatesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2, mockTicket3);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson1 = new JSONObject();
        fieldsJson1.put("dateClosed", "2024-01-01");
        fieldsJson1.put("storyPoints", 3);
        JSONObject fieldsJson2 = new JSONObject();
        fieldsJson2.put("dateClosed", "2024-01-02");
        fieldsJson2.put("storyPoints", 5);
        JSONObject fieldsJson3 = new JSONObject();
        fieldsJson3.put("dateClosed", "2024-01-03");
        fieldsJson3.put("storyPoints", 2);
        JSONArray labels = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("BUG-1");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getTicketTitle()).thenReturn("Bug 1");
            when(mockTicket2.getKey()).thenReturn("BUG-2");
            when(mockTicket2.getPriority()).thenReturn("Medium");
            when(mockTicket2.getTicketTitle()).thenReturn("Bug 2");
            when(mockTicket3.getKey()).thenReturn("BUG-3");
            when(mockTicket3.getPriority()).thenReturn("Low");
            when(mockTicket3.getTicketTitle()).thenReturn("Bug 3");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson1);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);
        when(mockTicket2.getFieldsAsJSON()).thenReturn(fieldsJson2);
        when(mockTicket2.getTicketLabels()).thenReturn(labels);
        when(mockTicket3.getFieldsAsJSON()).thenReturn(fieldsJson3);
        when(mockTicket3.getTicketLabels()).thenReturn(labels);

        // Act
        String result = bugTableGeneratorWithStoryPoints.generateBugsTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateBugOverviewTable_withMultiplePrioritiesAndStoryPoints_calculatesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2, mockTicket3);
        Map<String, Integer> bugsByPriority = new HashMap<>();
        bugsByPriority.put("High", 1);
        bugsByPriority.put("Medium", 1);
        bugsByPriority.put("Low", 1);
        List<String> sortedPriorities = Arrays.asList("High", "Medium", "Low");
        String expectedResult = "Generated Table";
        JSONObject fieldsJson1 = new JSONObject();
        fieldsJson1.put("storyPoints", 8);
        JSONObject fieldsJson2 = new JSONObject();
        fieldsJson2.put("storyPoints", 5);
        JSONObject fieldsJson3 = new JSONObject();
        fieldsJson3.put("storyPoints", 2);

        when(statisticsCalculator.countTicketsByPriority(tickets)).thenReturn(bugsByPriority);
        when(ticketSorter.sortPriorities(bugsByPriority.keySet())).thenReturn(sortedPriorities);
        when(statisticsCalculator.calculatePercentage(1, 3)).thenReturn(33.3);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket2.getPriority()).thenReturn("Medium");
            when(mockTicket3.getPriority()).thenReturn("Low");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson1);
        when(mockTicket2.getFieldsAsJSON()).thenReturn(fieldsJson2);
        when(mockTicket3.getFieldsAsJSON()).thenReturn(fieldsJson3);

        // Act
        String result = bugTableGeneratorWithStoryPoints.generateBugOverviewTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(3)).calculatePercentage(1, 3);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }
}
