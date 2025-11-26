package com.github.istin.dmtools.report.projectstatus.presentation.tables;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.report.projectstatus.data.TicketSorter;
import com.github.istin.dmtools.report.projectstatus.data.TicketStatisticsCalculator;
import com.github.istin.dmtools.report.projectstatus.model.TableData;
import com.github.istin.dmtools.report.projectstatus.presentation.TableGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StoryPointsTableGeneratorTest {

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

    private StoryPointsTableGenerator storyPointsTableGenerator;

    @BeforeEach
    void setUp() {
        storyPointsTableGenerator = new StoryPointsTableGenerator(
            baseTableGenerator,
            statisticsCalculator,
            ticketSorter
        );
    }

    @Test
    public void testConstructor_withAllParameters_setsFieldsCorrectly() {
        // Arrange
        TableGenerator mockBase = mock(TableGenerator.class);
        TicketStatisticsCalculator mockStats = mock(TicketStatisticsCalculator.class);
        TicketSorter mockSorter = mock(TicketSorter.class);

        // Act
        StoryPointsTableGenerator generator = new StoryPointsTableGenerator(mockBase, mockStats, mockSorter);

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
        String result = storyPointsTableGenerator.generateTable(tableData);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(tableData);
    }

    @Test
    public void testGenerateTable_withTicketList_callsGenerateStoryPointsDistributionTable() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Set<String> types = new HashSet<>(Arrays.asList("Task"));
        List<String> sortedTypes = Arrays.asList("Task");
        String expectedResult = "Generated Table";

        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getWeight()).thenReturn(5.0);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(statisticsCalculator.calculatePercentage(5.0, 5.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = storyPointsTableGenerator.generateTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(ticketSorter, times(1)).sortIssueTypes(anySet());
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTable_withTicketListColumnsAndFlags_callsGenerateTableWithTickets() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        List<String> sortedTypes = Arrays.asList("Task");
        String expectedResult = "Generated Table";

        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getWeight()).thenReturn(5.0);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(statisticsCalculator.calculatePercentage(5.0, 5.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = storyPointsTableGenerator.generateTable(tickets, new String[]{"Key", "Type"}, true, true);

        // Assert
        assertEquals(expectedResult, result);
        verify(ticketSorter, times(1)).sortIssueTypes(anySet());
    }

    @Test
    public void testGenerateStoryPointsDistributionTable_withEmptyList_returnsTableWithTotalRow() {
        // Arrange
        List<ITicket> emptyList = new ArrayList<>();
        List<String> emptyTypes = new ArrayList<>();
        String expectedResult = "Generated Empty Table";

        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(emptyTypes);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = storyPointsTableGenerator.generateStoryPointsDistributionTable(emptyList);

        // Assert
        assertEquals(expectedResult, result);
        verify(ticketSorter, times(1)).sortIssueTypes(anySet());
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateStoryPointsDistributionTable_withSingleTicket_returnsCorrectTable() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        List<String> sortedTypes = Arrays.asList("Task");
        String expectedResult = "Generated Table";

        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getWeight()).thenReturn(5.0);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(statisticsCalculator.calculatePercentage(5.0, 5.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = storyPointsTableGenerator.generateStoryPointsDistributionTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(1)).calculatePercentage(5.0, 5.0);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateStoryPointsDistributionTable_withMultipleTypes_returnsSortedTable() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2, mockTicket3);
        List<String> sortedTypes = Arrays.asList("Bug", "Story", "Task");
        String expectedResult = "Generated Table";

        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getWeight()).thenReturn(5.0);
            when(mockTicket2.getIssueType()).thenReturn("Story");
            when(mockTicket2.getWeight()).thenReturn(8.0);
            when(mockTicket3.getIssueType()).thenReturn("Bug");
            when(mockTicket3.getWeight()).thenReturn(3.0);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(statisticsCalculator.calculatePercentage(5.0, 16.0)).thenReturn(31.25);
        when(statisticsCalculator.calculatePercentage(8.0, 16.0)).thenReturn(50.0);
        when(statisticsCalculator.calculatePercentage(3.0, 16.0)).thenReturn(18.75);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = storyPointsTableGenerator.generateStoryPointsDistributionTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(3)).calculatePercentage(anyDouble(), eq(16.0));
        verify(ticketSorter, times(1)).sortIssueTypes(anySet());
    }

    @Test
    public void testGenerateStoryPointsDistributionTable_withNullIssueType_usesDefaultTask() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        List<String> sortedTypes = Arrays.asList("Task");
        String expectedResult = "Generated Table";

        try {
            when(mockTicket1.getIssueType()).thenReturn(null);
            when(mockTicket1.getWeight()).thenReturn(5.0);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(statisticsCalculator.calculatePercentage(5.0, 5.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = storyPointsTableGenerator.generateStoryPointsDistributionTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(ticketSorter, times(1)).sortIssueTypes(anySet());
    }

    @Test
    public void testGenerateStoryPointsDistributionTable_withZeroStoryPoints_handlesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        List<String> sortedTypes = Arrays.asList("Task");
        String expectedResult = "Generated Table";

        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getWeight()).thenReturn(0.0);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(statisticsCalculator.calculatePercentage(0.0, 0.0)).thenReturn(0.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = storyPointsTableGenerator.generateStoryPointsDistributionTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(1)).calculatePercentage(0.0, 0.0);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateStoryPointsDistributionTable_withNegativeStoryPoints_handlesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        List<String> sortedTypes = Arrays.asList("Task");
        String expectedResult = "Generated Table";

        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getWeight()).thenReturn(-5.0);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(statisticsCalculator.calculatePercentage(-5.0, -5.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = storyPointsTableGenerator.generateStoryPointsDistributionTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateStoryPointsDistributionTable_withIOException_handlesGracefully() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        List<String> sortedTypes = Arrays.asList("Task");
        String expectedResult = "Generated Table";

        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getWeight()).thenReturn(5.0);
            when(mockTicket2.getIssueType()).thenThrow(new IOException("Test exception"));
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(statisticsCalculator.calculatePercentage(5.0, 5.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = storyPointsTableGenerator.generateStoryPointsDistributionTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateStoryPointsDistributionTable_withMultipleTicketsSameType_calculatesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        List<String> sortedTypes = Arrays.asList("Task");
        String expectedResult = "Generated Table";

        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getWeight()).thenReturn(5.0);
            when(mockTicket2.getIssueType()).thenReturn("Task");
            when(mockTicket2.getWeight()).thenReturn(3.0);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(statisticsCalculator.calculatePercentage(8.0, 8.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = storyPointsTableGenerator.generateStoryPointsDistributionTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(ArgumentMatchers.<TableData>argThat(tableData -> {
            List<List<String>> rows = tableData.getRows();
            if (rows.size() < 2) return false;
            List<String> dataRow = rows.get(0);
            List<String> totalsRow = rows.get(1);
            return dataRow.get(1).equals("2") && 
                   dataRow.get(2).equals("8.0") &&
                   totalsRow.get(1).equals("2") &&
                   totalsRow.get(2).equals("8.0");
        }));
    }

    @Test
    public void testGenerateStoryPointsDistributionTable_withDecimalStoryPoints_formatsCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        List<String> sortedTypes = Arrays.asList("Task");
        String expectedResult = "Generated Table";

        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getWeight()).thenReturn(5.75);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(statisticsCalculator.calculatePercentage(5.75, 5.75)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = storyPointsTableGenerator.generateStoryPointsDistributionTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(ArgumentMatchers.<TableData>argThat(tableData -> {
            List<List<String>> rows = tableData.getRows();
            if (rows.isEmpty()) return false;
            List<String> dataRow = rows.get(0);
            return dataRow.get(2).equals("5.8");
        }));
    }

    @Test
    public void testGenerateStoryPointsDistributionTable_withAverageCalculation_calculatesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        List<String> sortedTypes = Arrays.asList("Task");
        String expectedResult = "Generated Table";

        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getWeight()).thenReturn(5.0);
            when(mockTicket2.getIssueType()).thenReturn("Task");
            when(mockTicket2.getWeight()).thenReturn(3.0);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(statisticsCalculator.calculatePercentage(8.0, 8.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = storyPointsTableGenerator.generateStoryPointsDistributionTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(ArgumentMatchers.<TableData>argThat(tableData -> {
            List<List<String>> rows = tableData.getRows();
            if (rows.isEmpty()) return false;
            List<String> dataRow = rows.get(0);
            return dataRow.get(4).equals("4.00");
        }));
    }

    @Test
    public void testGenerateStoryPointsDistributionTable_withEmptyList_hasZeroAverage() {
        // Arrange
        List<ITicket> emptyList = new ArrayList<>();
        List<String> emptyTypes = new ArrayList<>();
        String expectedResult = "Generated Empty Table";

        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(emptyTypes);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = storyPointsTableGenerator.generateStoryPointsDistributionTable(emptyList);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(ArgumentMatchers.<TableData>argThat(tableData -> {
            List<List<String>> rows = tableData.getRows();
            if (rows.isEmpty()) return false;
            List<String> totalsRow = rows.get(rows.size() - 1);
            return totalsRow.get(4).equals("0.00");
        }));
    }

    @Test
    public void testGenerateStoryPointsDistributionTable_withLargeStoryPoints_handlesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        List<String> sortedTypes = Arrays.asList("Task");
        String expectedResult = "Generated Table";

        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getWeight()).thenReturn(1000.0);
            when(mockTicket2.getIssueType()).thenReturn("Task");
            when(mockTicket2.getWeight()).thenReturn(2000.0);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(statisticsCalculator.calculatePercentage(3000.0, 3000.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = storyPointsTableGenerator.generateStoryPointsDistributionTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateStoryPointsDistributionTable_withPercentageCalculation_calculatesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2, mockTicket3);
        List<String> sortedTypes = Arrays.asList("Task");
        String expectedResult = "Generated Table";

        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getWeight()).thenReturn(10.0);
            when(mockTicket2.getIssueType()).thenReturn("Task");
            when(mockTicket2.getWeight()).thenReturn(20.0);
            when(mockTicket3.getIssueType()).thenReturn("Task");
            when(mockTicket3.getWeight()).thenReturn(20.0);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(statisticsCalculator.calculatePercentage(50.0, 50.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = storyPointsTableGenerator.generateStoryPointsDistributionTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(1)).calculatePercentage(50.0, 50.0);
    }

    @Test
    public void testGenerateStoryPointsDistributionTable_withTotalsRow_hasCorrectFormat() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        List<String> sortedTypes = Arrays.asList("Task");
        String expectedResult = "Generated Table";

        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getWeight()).thenReturn(5.0);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(statisticsCalculator.calculatePercentage(5.0, 5.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = storyPointsTableGenerator.generateStoryPointsDistributionTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(ArgumentMatchers.<TableData>argThat(tableData -> {
            List<List<String>> rows = tableData.getRows();
            if (rows.isEmpty()) return false;
            List<String> totalsRow = rows.get(rows.size() - 1);
            return totalsRow.get(0).equals("**Total**") &&
                   totalsRow.get(3).equals("100.0%");
        }));
    }

    @Test
    public void testGenerateStoryPointsDistributionTable_withTableHeaders_hasCorrectHeaders() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        List<String> sortedTypes = Arrays.asList("Task");
        String expectedResult = "Generated Table";

        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getWeight()).thenReturn(5.0);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(statisticsCalculator.calculatePercentage(5.0, 5.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = storyPointsTableGenerator.generateStoryPointsDistributionTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(ArgumentMatchers.<TableData>argThat(tableData -> {
            List<String> headers = tableData.getHeaders();
            return headers.contains("Issue Type") &&
                   headers.contains("Ticket Count") &&
                   headers.contains("Story Points") &&
                   headers.contains("% of Total Points") &&
                   headers.contains("Avg. Points per Ticket") &&
                   tableData.getTitle().equals("Story Points Distribution by Issue Type");
        }));
    }

}
