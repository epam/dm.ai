package com.github.istin.dmtools.report.projectstatus.presentation.tables;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.report.projectstatus.data.TicketSorter;
import com.github.istin.dmtools.report.projectstatus.data.TicketStatisticsCalculator;
import com.github.istin.dmtools.report.projectstatus.model.TableData;
import com.github.istin.dmtools.report.projectstatus.presentation.TableGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SummaryTableGeneratorTest {

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

    private SummaryTableGenerator summaryTableGenerator;

    @BeforeEach
    void setUp() {
        summaryTableGenerator = new SummaryTableGenerator(
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
        SummaryTableGenerator generator = new SummaryTableGenerator(mockBase, mockStats, mockSorter);

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
        String result = summaryTableGenerator.generateTable(tableData);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(tableData);
    }

    @Test
    public void testGenerateTable_withTicketList_callsGenerateSummaryTable() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, Map<String, Integer>> typePriorityStats = new HashMap<>();
        Map<String, Integer> priorityCounts = new HashMap<>();
        priorityCounts.put("High", 1);
        typePriorityStats.put("Bug", priorityCounts);
        List<String> sortedPriorities = Arrays.asList("High");
        List<String> sortedTypes = Arrays.asList("Bug");
        String expectedResult = "Generated Summary Table";

        when(statisticsCalculator.collectTicketStatisticsByTypeAndPriority(tickets))
            .thenReturn(typePriorityStats);
        when(ticketSorter.sortPriorities(anySet())).thenReturn(sortedPriorities);
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = summaryTableGenerator.generateTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(1)).collectTicketStatisticsByTypeAndPriority(tickets);
        verify(ticketSorter, times(1)).sortPriorities(anySet());
        verify(ticketSorter, times(1)).sortIssueTypes(anySet());
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTable_withTicketListColumnsAndFlags_callsGenerateTableWithTickets() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, Map<String, Integer>> typePriorityStats = new HashMap<>();
        Map<String, Integer> priorityCounts = new HashMap<>();
        priorityCounts.put("High", 1);
        typePriorityStats.put("Bug", priorityCounts);
        List<String> sortedPriorities = Arrays.asList("High");
        List<String> sortedTypes = Arrays.asList("Bug");
        String expectedResult = "Generated Summary Table";

        when(statisticsCalculator.collectTicketStatisticsByTypeAndPriority(tickets))
            .thenReturn(typePriorityStats);
        when(ticketSorter.sortPriorities(anySet())).thenReturn(sortedPriorities);
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = summaryTableGenerator.generateTable(tickets, new String[]{"Key", "Priority"}, true, false);

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(1)).collectTicketStatisticsByTypeAndPriority(tickets);
    }

    @Test
    public void testGenerateSummaryTable_withEmptyList_returnsTableWithTotalRow() {
        // Arrange
        List<ITicket> emptyList = new ArrayList<>();
        Map<String, Map<String, Integer>> emptyStats = new HashMap<>();
        List<String> emptyPriorities = new ArrayList<>();
        List<String> emptyTypes = new ArrayList<>();
        String expectedResult = "Generated Empty Table";

        when(statisticsCalculator.collectTicketStatisticsByTypeAndPriority(emptyList))
            .thenReturn(emptyStats);
        when(ticketSorter.sortPriorities(anySet())).thenReturn(emptyPriorities);
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(emptyTypes);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = summaryTableGenerator.generateSummaryTable(emptyList);

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(1)).collectTicketStatisticsByTypeAndPriority(emptyList);
        verify(ticketSorter, times(1)).sortPriorities(anySet());
        verify(ticketSorter, times(1)).sortIssueTypes(anySet());
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateSummaryTable_withSingleTicket_returnsCorrectTable() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, Map<String, Integer>> typePriorityStats = new HashMap<>();
        Map<String, Integer> priorityCounts = new HashMap<>();
        priorityCounts.put("High", 1);
        typePriorityStats.put("Bug", priorityCounts);
        List<String> sortedPriorities = Arrays.asList("High");
        List<String> sortedTypes = Arrays.asList("Bug");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.collectTicketStatisticsByTypeAndPriority(tickets))
            .thenReturn(typePriorityStats);
        when(ticketSorter.sortPriorities(anySet())).thenReturn(sortedPriorities);
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = summaryTableGenerator.generateSummaryTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(1)).collectTicketStatisticsByTypeAndPriority(tickets);
        
        ArgumentCaptor<TableData> tableDataCaptor = ArgumentCaptor.forClass(TableData.class);
        verify(baseTableGenerator, times(1)).generateTable(tableDataCaptor.capture());
        TableData capturedTableData = tableDataCaptor.getValue();
        
        assertEquals("Summary by Issue Type and Priority", capturedTableData.getTitle());
        assertTrue(capturedTableData.getHeaders().contains("Issue Type"));
        assertTrue(capturedTableData.getHeaders().contains("High"));
        assertTrue(capturedTableData.getHeaders().contains("Total"));
        assertEquals(2, capturedTableData.getRows().size()); // One data row + one totals row
    }

    @Test
    public void testGenerateSummaryTable_withMultipleTypesAndPriorities_returnsCorrectTable() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2, mockTicket3);
        Map<String, Map<String, Integer>> typePriorityStats = new HashMap<>();
        
        Map<String, Integer> bugPriorities = new HashMap<>();
        bugPriorities.put("High", 1);
        bugPriorities.put("Medium", 1);
        typePriorityStats.put("Bug", bugPriorities);
        
        Map<String, Integer> taskPriorities = new HashMap<>();
        taskPriorities.put("Low", 1);
        typePriorityStats.put("Task", taskPriorities);
        
        List<String> sortedPriorities = Arrays.asList("High", "Medium", "Low");
        List<String> sortedTypes = Arrays.asList("Bug", "Task");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.collectTicketStatisticsByTypeAndPriority(tickets))
            .thenReturn(typePriorityStats);
        when(ticketSorter.sortPriorities(anySet())).thenReturn(sortedPriorities);
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = summaryTableGenerator.generateSummaryTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(1)).collectTicketStatisticsByTypeAndPriority(tickets);
        
        ArgumentCaptor<TableData> tableDataCaptor = ArgumentCaptor.forClass(TableData.class);
        verify(baseTableGenerator, times(1)).generateTable(tableDataCaptor.capture());
        TableData capturedTableData = tableDataCaptor.getValue();
        
        assertEquals("Summary by Issue Type and Priority", capturedTableData.getTitle());
        assertEquals(5, capturedTableData.getHeaders().size()); // Issue Type + 3 priorities + Total
        assertEquals(3, capturedTableData.getRows().size()); // 2 data rows + 1 totals row
    }

    @Test
    public void testGenerateSummaryTable_withMultiplePriorities_calculatesTotalsCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        Map<String, Map<String, Integer>> typePriorityStats = new HashMap<>();
        Map<String, Integer> priorityCounts = new HashMap<>();
        priorityCounts.put("High", 1);
        priorityCounts.put("Medium", 1);
        typePriorityStats.put("Bug", priorityCounts);
        List<String> sortedPriorities = Arrays.asList("High", "Medium");
        List<String> sortedTypes = Arrays.asList("Bug");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.collectTicketStatisticsByTypeAndPriority(tickets))
            .thenReturn(typePriorityStats);
        when(ticketSorter.sortPriorities(anySet())).thenReturn(sortedPriorities);
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = summaryTableGenerator.generateSummaryTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        
        ArgumentCaptor<TableData> tableDataCaptor = ArgumentCaptor.forClass(TableData.class);
        verify(baseTableGenerator, times(1)).generateTable(tableDataCaptor.capture());
        TableData capturedTableData = tableDataCaptor.getValue();
        
        // Verify Bug row has correct totals
        List<String> bugRow = capturedTableData.getRows().get(0);
        assertEquals("Bug", bugRow.get(0));
        assertEquals("1", bugRow.get(1)); // High count
        assertEquals("1", bugRow.get(2)); // Medium count
        assertEquals("2", bugRow.get(3)); // Total
        
        // Verify totals row
        List<String> totalsRow = capturedTableData.getRows().get(1);
        assertEquals("**Total**", totalsRow.get(0));
        assertEquals("1", totalsRow.get(1)); // High total
        assertEquals("1", totalsRow.get(2)); // Medium total
        assertEquals("2", totalsRow.get(3)); // Grand total
    }

    @Test
    public void testGenerateSummaryTable_withMissingPriorityInType_usesZero() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, Map<String, Integer>> typePriorityStats = new HashMap<>();
        Map<String, Integer> priorityCounts = new HashMap<>();
        priorityCounts.put("High", 1);
        typePriorityStats.put("Bug", priorityCounts);
        List<String> sortedPriorities = Arrays.asList("High", "Medium", "Low");
        List<String> sortedTypes = Arrays.asList("Bug");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.collectTicketStatisticsByTypeAndPriority(tickets))
            .thenReturn(typePriorityStats);
        when(ticketSorter.sortPriorities(anySet())).thenReturn(sortedPriorities);
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = summaryTableGenerator.generateSummaryTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        
        ArgumentCaptor<TableData> tableDataCaptor = ArgumentCaptor.forClass(TableData.class);
        verify(baseTableGenerator, times(1)).generateTable(tableDataCaptor.capture());
        TableData capturedTableData = tableDataCaptor.getValue();
        
        List<String> bugRow = capturedTableData.getRows().get(0);
        assertEquals("Bug", bugRow.get(0));
        assertEquals("1", bugRow.get(1)); // High count
        assertEquals("0", bugRow.get(2)); // Medium count (missing, should be 0)
        assertEquals("0", bugRow.get(3)); // Low count (missing, should be 0)
        assertEquals("1", bugRow.get(4)); // Total
    }

    @Test
    public void testGenerateSummaryTable_withMultipleTypes_calculatesGrandTotalCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2, mockTicket3);
        Map<String, Map<String, Integer>> typePriorityStats = new HashMap<>();
        
        Map<String, Integer> bugPriorities = new HashMap<>();
        bugPriorities.put("High", 2);
        typePriorityStats.put("Bug", bugPriorities);
        
        Map<String, Integer> taskPriorities = new HashMap<>();
        taskPriorities.put("Medium", 1);
        typePriorityStats.put("Task", taskPriorities);
        
        List<String> sortedPriorities = Arrays.asList("High", "Medium");
        List<String> sortedTypes = Arrays.asList("Bug", "Task");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.collectTicketStatisticsByTypeAndPriority(tickets))
            .thenReturn(typePriorityStats);
        when(ticketSorter.sortPriorities(anySet())).thenReturn(sortedPriorities);
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = summaryTableGenerator.generateSummaryTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        
        ArgumentCaptor<TableData> tableDataCaptor = ArgumentCaptor.forClass(TableData.class);
        verify(baseTableGenerator, times(1)).generateTable(tableDataCaptor.capture());
        TableData capturedTableData = tableDataCaptor.getValue();
        
        // Verify totals row has correct grand total
        List<String> totalsRow = capturedTableData.getRows().get(2);
        assertEquals("**Total**", totalsRow.get(0));
        assertEquals("2", totalsRow.get(1)); // High total
        assertEquals("1", totalsRow.get(2)); // Medium total
        assertEquals("3", totalsRow.get(3)); // Grand total
    }

    @Test
    public void testGenerateSummaryTable_withEmptyStatistics_handlesGracefully() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, Map<String, Integer>> emptyStats = new HashMap<>();
        List<String> sortedPriorities = new ArrayList<>();
        List<String> sortedTypes = new ArrayList<>();
        String expectedResult = "Generated Table";

        when(statisticsCalculator.collectTicketStatisticsByTypeAndPriority(tickets))
            .thenReturn(emptyStats);
        when(ticketSorter.sortPriorities(anySet())).thenReturn(sortedPriorities);
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = summaryTableGenerator.generateSummaryTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        
        ArgumentCaptor<TableData> tableDataCaptor = ArgumentCaptor.forClass(TableData.class);
        verify(baseTableGenerator, times(1)).generateTable(tableDataCaptor.capture());
        TableData capturedTableData = tableDataCaptor.getValue();
        
        assertEquals("Summary by Issue Type and Priority", capturedTableData.getTitle());
        assertEquals(2, capturedTableData.getHeaders().size()); // Issue Type + Total
        assertEquals(1, capturedTableData.getRows().size()); // Only totals row
    }

    @Test
    public void testGenerateSummaryTable_withNullPriorityInStatistics_handlesGracefully() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, Map<String, Integer>> typePriorityStats = new HashMap<>();
        Map<String, Integer> priorityCounts = new HashMap<>();
        priorityCounts.put(null, 1);
        typePriorityStats.put("Bug", priorityCounts);
        List<String> sortedPriorities = Arrays.asList("High");
        List<String> sortedTypes = Arrays.asList("Bug");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.collectTicketStatisticsByTypeAndPriority(tickets))
            .thenReturn(typePriorityStats);
        when(ticketSorter.sortPriorities(anySet())).thenReturn(sortedPriorities);
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = summaryTableGenerator.generateSummaryTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateSummaryTable_withNullTypeInStatistics_handlesGracefully() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, Map<String, Integer>> typePriorityStats = new HashMap<>();
        Map<String, Integer> priorityCounts = new HashMap<>();
        priorityCounts.put("High", 1);
        typePriorityStats.put(null, priorityCounts);
        List<String> sortedPriorities = Arrays.asList("High");
        List<String> sortedTypes = Arrays.asList("Task");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.collectTicketStatisticsByTypeAndPriority(tickets))
            .thenReturn(typePriorityStats);
        when(ticketSorter.sortPriorities(anySet())).thenReturn(sortedPriorities);
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = summaryTableGenerator.generateSummaryTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateSummaryTable_verifiesTableDataStructure() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        Map<String, Map<String, Integer>> typePriorityStats = new HashMap<>();
        
        Map<String, Integer> bugPriorities = new HashMap<>();
        bugPriorities.put("High", 1);
        typePriorityStats.put("Bug", bugPriorities);
        
        Map<String, Integer> taskPriorities = new HashMap<>();
        taskPriorities.put("Medium", 1);
        typePriorityStats.put("Task", taskPriorities);
        
        List<String> sortedPriorities = Arrays.asList("High", "Medium");
        List<String> sortedTypes = Arrays.asList("Bug", "Task");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.collectTicketStatisticsByTypeAndPriority(tickets))
            .thenReturn(typePriorityStats);
        when(ticketSorter.sortPriorities(anySet())).thenReturn(sortedPriorities);
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = summaryTableGenerator.generateSummaryTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        
        ArgumentCaptor<TableData> tableDataCaptor = ArgumentCaptor.forClass(TableData.class);
        verify(baseTableGenerator, times(1)).generateTable(tableDataCaptor.capture());
        TableData capturedTableData = tableDataCaptor.getValue();
        
        // Verify headers structure
        List<String> headers = capturedTableData.getHeaders();
        assertEquals("Issue Type", headers.get(0));
        assertEquals("High", headers.get(1));
        assertEquals("Medium", headers.get(2));
        assertEquals("Total", headers.get(3));
        
        // Verify all rows have correct number of columns
        for (List<String> row : capturedTableData.getRows()) {
            assertEquals(headers.size(), row.size(), "Row size must match header size");
        }
    }

    @Test
    public void testGenerateSummaryTable_withSingleTypeMultiplePriorities_calculatesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2, mockTicket3);
        Map<String, Map<String, Integer>> typePriorityStats = new HashMap<>();
        Map<String, Integer> priorityCounts = new HashMap<>();
        priorityCounts.put("High", 1);
        priorityCounts.put("Medium", 1);
        priorityCounts.put("Low", 1);
        typePriorityStats.put("Bug", priorityCounts);
        List<String> sortedPriorities = Arrays.asList("High", "Medium", "Low");
        List<String> sortedTypes = Arrays.asList("Bug");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.collectTicketStatisticsByTypeAndPriority(tickets))
            .thenReturn(typePriorityStats);
        when(ticketSorter.sortPriorities(anySet())).thenReturn(sortedPriorities);
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = summaryTableGenerator.generateSummaryTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        
        ArgumentCaptor<TableData> tableDataCaptor = ArgumentCaptor.forClass(TableData.class);
        verify(baseTableGenerator, times(1)).generateTable(tableDataCaptor.capture());
        TableData capturedTableData = tableDataCaptor.getValue();
        
        // Verify Bug row
        List<String> bugRow = capturedTableData.getRows().get(0);
        assertEquals("Bug", bugRow.get(0));
        assertEquals("1", bugRow.get(1)); // High
        assertEquals("1", bugRow.get(2)); // Medium
        assertEquals("1", bugRow.get(3)); // Low
        assertEquals("3", bugRow.get(4)); // Total
        
        // Verify totals row
        List<String> totalsRow = capturedTableData.getRows().get(1);
        assertEquals("**Total**", totalsRow.get(0));
        assertEquals("1", totalsRow.get(1)); // High total
        assertEquals("1", totalsRow.get(2)); // Medium total
        assertEquals("1", totalsRow.get(3)); // Low total
        assertEquals("3", totalsRow.get(4)); // Grand total
    }
}
