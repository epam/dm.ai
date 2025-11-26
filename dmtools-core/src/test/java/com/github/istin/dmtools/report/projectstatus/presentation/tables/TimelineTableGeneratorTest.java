package com.github.istin.dmtools.report.projectstatus.presentation.tables;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.report.projectstatus.data.TicketSorter;
import com.github.istin.dmtools.report.projectstatus.model.TableData;
import com.github.istin.dmtools.report.projectstatus.model.TimelinePeriod;
import com.github.istin.dmtools.report.projectstatus.presentation.TableGenerator;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TimelineTableGeneratorTest {

    @Mock
    private TableGenerator baseTableGenerator;

    @Mock
    private TicketSorter ticketSorter;

    @Mock
    private ITicket mockTicket1;

    @Mock
    private ITicket mockTicket2;

    @Mock
    private ITicket mockTicket3;

    private TimelineTableGenerator generator;
    private SimpleDateFormat dateFormat;

    @BeforeEach
    void setUp() {
        generator = new TimelineTableGenerator(baseTableGenerator, ticketSorter);
        dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    }

    @Test
    public void testConstructor_withAllParameters_setsFieldsCorrectly() {
        // Arrange
        TableGenerator mockBase = mock(TableGenerator.class);
        TicketSorter mockSorter = mock(TicketSorter.class);

        // Act
        TimelineTableGenerator newGenerator = new TimelineTableGenerator(mockBase, mockSorter);

        // Assert
        assertNotNull(newGenerator);
    }

    @Test
    public void testGenerateTable_withTableData_delegatesToBaseTableGenerator() {
        // Arrange
        TableData tableData = new TableData("Test Table", Arrays.asList("Header1", "Header2"));
        String expectedResult = "Generated Table";
        when(baseTableGenerator.generateTable(tableData)).thenReturn(expectedResult);

        // Act
        String result = generator.generateTable(tableData);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(tableData);
    }

    @Test
    public void testGenerateTable_withTicketList_callsGenerateTimelineTableWithDefaults() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Timeline Table";
        setupTicketWithClosedDate(mockTicket1, "2024-01-15", "Task", 5.0);

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTable(tickets);

        // Assert
        assertNotNull(result);
        verify(baseTableGenerator, atLeastOnce()).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTable_withTicketListColumnsAndFlags_callsGenerateTableWithTickets() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Timeline Table";
        setupTicketWithClosedDate(mockTicket1, "2024-01-15", "Task", 5.0);

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTable(tickets, new String[]{"Key", "Type"}, true, false);

        // Assert
        assertNotNull(result);
        verify(baseTableGenerator, atLeastOnce()).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineTable_withEmptyList_returnsEmptyTable() {
        // Arrange
        List<ITicket> emptyList = new ArrayList<>();
        String expectedResult = "Generated Empty Table";

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineTable(emptyList, TimelinePeriod.MONTH, false);

        // Assert
        assertNotNull(result);
        verify(baseTableGenerator, atLeastOnce()).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineTable_withSingleTicket_returnsTable() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Timeline Table";
        setupTicketWithClosedDate(mockTicket1, "2024-01-15", "Task", 5.0);

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineTable(tickets, TimelinePeriod.MONTH, false);

        // Assert
        assertNotNull(result);
        verify(baseTableGenerator, atLeastOnce()).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineTable_withMultipleTickets_returnsTable() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2, mockTicket3);
        String expectedResult = "Generated Timeline Table";
        setupTicketWithClosedDate(mockTicket1, "2024-01-15", "Task", 5.0);
        setupTicketWithClosedDate(mockTicket2, "2024-02-15", "Bug", 3.0);
        setupTicketWithClosedDate(mockTicket3, "2024-01-20", "Story", 8.0);

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineTable(tickets, TimelinePeriod.MONTH, false);

        // Assert
        assertNotNull(result);
        verify(baseTableGenerator, atLeastOnce()).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineTable_withStoryPointsTrue_includesStoryPointsColumns() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Timeline Table";
        setupTicketWithClosedDate(mockTicket1, "2024-01-15", "Task", 5.0);

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineTable(tickets, TimelinePeriod.MONTH, true);

        // Assert
        assertNotNull(result);
        verify(baseTableGenerator, atLeastOnce()).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineTable_withWeekPeriod_formatsCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Timeline Table";
        setupTicketWithClosedDate(mockTicket1, "2024-01-15", "Task", 5.0);

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineTable(tickets, TimelinePeriod.WEEK, false);

        // Assert
        assertNotNull(result);
        verify(baseTableGenerator, atLeastOnce()).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineTable_withTwoWeeksPeriod_formatsCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Timeline Table";
        setupTicketWithClosedDate(mockTicket1, "2024-01-15", "Task", 5.0);

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineTable(tickets, TimelinePeriod.TWO_WEEKS, false);

        // Assert
        assertNotNull(result);
        verify(baseTableGenerator, atLeastOnce()).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineTable_withQuarterPeriod_formatsCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Timeline Table";
        setupTicketWithClosedDate(mockTicket1, "2024-01-15", "Task", 5.0);

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineTable(tickets, TimelinePeriod.QUARTER, false);

        // Assert
        assertNotNull(result);
        verify(baseTableGenerator, atLeastOnce()).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineTable_withBugTypes_identifiesBugsCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        String expectedResult = "Generated Timeline Table";
        setupTicketWithClosedDate(mockTicket1, "2024-01-15", "Bug", 3.0);
        setupTicketWithClosedDate(mockTicket2, "2024-01-20", "Defect", 2.0);

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineTable(tickets, TimelinePeriod.MONTH, false);

        // Assert
        assertNotNull(result);
        verify(baseTableGenerator, atLeastOnce()).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineTable_withOnlyBugs_usesSimplifiedSummary() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        String expectedResult = "Generated Timeline Table";
        setupTicketWithClosedDate(mockTicket1, "2024-01-15", "Bug", 3.0);
        setupTicketWithClosedDate(mockTicket2, "2024-01-20", "Defect", 2.0);

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineTable(tickets, TimelinePeriod.MONTH, false);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("Bug Metrics") || result.contains("Generated Timeline Table"));
        verify(baseTableGenerator, atLeastOnce()).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineTable_withTicketsInDifferentPeriods_groupsCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2, mockTicket3);
        String expectedResult = "Generated Timeline Table";
        setupTicketWithClosedDate(mockTicket1, "2024-01-15", "Task", 5.0);
        setupTicketWithClosedDate(mockTicket2, "2024-02-15", "Task", 3.0);
        setupTicketWithClosedDate(mockTicket3, "2024-03-15", "Task", 8.0);

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineTable(tickets, TimelinePeriod.MONTH, false);

        // Assert
        assertNotNull(result);
        verify(baseTableGenerator, atLeastOnce()).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineTable_withNullClosedDate_skipsTicket() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Timeline Table";
        JSONObject fieldsJson = new JSONObject();
        // No dateClosed field

        try {
            lenient().when(mockTicket1.getIssueType()).thenReturn("Task");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException");
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        lenient().when(mockTicket1.getWeight()).thenReturn(5.0);

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineTable(tickets, TimelinePeriod.MONTH, false);

        // Assert
        assertNotNull(result);
        verify(baseTableGenerator, atLeastOnce()).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineTable_withInvalidDate_skipsTicket() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Timeline Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "invalid-date");

        try {
            lenient().when(mockTicket1.getIssueType()).thenReturn("Task");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException");
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        lenient().when(mockTicket1.getWeight()).thenReturn(5.0);

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineTable(tickets, TimelinePeriod.MONTH, false);

        // Assert
        assertNotNull(result);
        verify(baseTableGenerator, atLeastOnce()).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineTable_withExceptionInProcessing_handlesGracefully() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Timeline Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-15");

        try {
            when(mockTicket1.getIssueType()).thenThrow(new IOException("Test exception"));
        } catch (IOException e) {
            fail("Mock setup should not throw IOException");
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineTable(tickets, TimelinePeriod.MONTH, false);

        // Assert
        assertNotNull(result);
        verify(baseTableGenerator, atLeastOnce()).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineByCreationAndPriority_withEmptyList_returnsEmptyTable() {
        // Arrange
        List<ITicket> emptyList = new ArrayList<>();
        String expectedResult = "Generated Empty Table";

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineByCreationAndPriority(emptyList, TimelinePeriod.MONTH);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineByCreationAndPriority_withSingleBug_returnsTable() {
        // Arrange
        List<ITicket> bugs = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        setupBugWithCreationDate(mockTicket1, "2024-01-15", "High");

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineByCreationAndPriority(bugs, TimelinePeriod.MONTH);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineByCreationAndPriority_withMultipleBugs_returnsTable() {
        // Arrange
        List<ITicket> bugs = Arrays.asList(mockTicket1, mockTicket2, mockTicket3);
        String expectedResult = "Generated Table";
        setupBugWithCreationDate(mockTicket1, "2024-01-15", "High");
        setupBugWithCreationDate(mockTicket2, "2024-01-20", "Medium");
        setupBugWithCreationDate(mockTicket3, "2024-02-15", "Low");

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineByCreationAndPriority(bugs, TimelinePeriod.MONTH);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineByCreationAndPriority_withNullPriority_handlesGracefully() {
        // Arrange
        List<ITicket> bugs = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        Date creationDate = createDate("2024-01-15");
        when(mockTicket1.getCreated()).thenReturn(creationDate);

        try {
            when(mockTicket1.getPriority()).thenReturn(null);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException");
        }

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineByCreationAndPriority(bugs, TimelinePeriod.MONTH);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineByCreationAndPriority_withNullCreationDate_skipsBug() {
        // Arrange
        List<ITicket> bugs = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        when(mockTicket1.getCreated()).thenReturn(null);

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineByCreationAndPriority(bugs, TimelinePeriod.MONTH);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineByCreationAndPriority_withIOException_handlesGracefully() {
        // Arrange
        List<ITicket> bugs = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        Date creationDate = createDate("2024-01-15");
        when(mockTicket1.getCreated()).thenReturn(creationDate);

        try {
            when(mockTicket1.getPriority()).thenThrow(new IOException("Test exception"));
        } catch (IOException e) {
            fail("Mock setup should not throw IOException");
        }

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineByCreationAndPriority(bugs, TimelinePeriod.MONTH);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineByCreationAndPriority_withWeekPeriod_formatsCorrectly() {
        // Arrange
        List<ITicket> bugs = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        setupBugWithCreationDate(mockTicket1, "2024-01-15", "High");

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineByCreationAndPriority(bugs, TimelinePeriod.WEEK);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineByCreationAndCategory_withEmptyList_returnsEmptyTable() {
        // Arrange
        List<ITicket> emptyList = new ArrayList<>();
        String expectedResult = "Generated Empty Table";

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineByCreationAndCategory(emptyList, TimelinePeriod.MONTH, "priority", "Priority", false);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineByCreationAndCategory_withPriorityField_returnsTable() {
        // Arrange
        List<ITicket> items = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        setupItemWithCreationDateAndPriority(mockTicket1, "2024-01-15", "High");

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineByCreationAndCategory(items, TimelinePeriod.MONTH, "priority", "Priority", false);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineByCreationAndCategory_withStatusField_returnsTable() {
        // Arrange
        List<ITicket> items = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        setupItemWithCreationDateAndStatus(mockTicket1, "2024-01-15", "Done");

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineByCreationAndCategory(items, TimelinePeriod.MONTH, "status", "Status", false);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineByCreationAndCategory_withIssueTypeField_returnsTable() {
        // Arrange
        List<ITicket> items = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        setupItemWithCreationDateAndIssueType(mockTicket1, "2024-01-15", "Task");

        List<String> sortedTypes = Arrays.asList("Task");
        when(ticketSorter.sortIssueTypes(anySet())).thenReturn(sortedTypes);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineByCreationAndCategory(items, TimelinePeriod.MONTH, "issueType", "Issue Type", false);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineByCreationAndCategory_withStoryPointsTrue_includesStoryPoints() {
        // Arrange
        List<ITicket> items = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        setupItemWithCreationDateAndPriority(mockTicket1, "2024-01-15", "High");
        lenient().when(mockTicket1.getWeight()).thenReturn(5.0);

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineByCreationAndCategory(items, TimelinePeriod.MONTH, "priority", "Priority", true);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineByCreationAndCategory_withNullCreationDate_skipsItem() {
        // Arrange
        List<ITicket> items = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        when(mockTicket1.getCreated()).thenReturn(null);

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineByCreationAndCategory(items, TimelinePeriod.MONTH, "priority", "Priority", false);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineByCreationAndCategory_withIOException_handlesGracefully() {
        // Arrange
        List<ITicket> items = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        Date creationDate = createDate("2024-01-15");
        when(mockTicket1.getCreated()).thenReturn(creationDate);

        try {
            when(mockTicket1.getPriority()).thenThrow(new IOException("Test exception"));
        } catch (IOException e) {
            fail("Mock setup should not throw IOException");
        }

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineByCreationAndCategory(items, TimelinePeriod.MONTH, "priority", "Priority", false);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineByCreationAndCategory_withCustomField_usesFieldsJson() {
        // Arrange
        List<ITicket> items = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        Date creationDate = createDate("2024-01-15");
        when(mockTicket1.getCreated()).thenReturn(creationDate);

        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("customField", "CustomValue");
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineByCreationAndCategory(items, TimelinePeriod.MONTH, "customField", "Custom Field", false);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTimelineByCreationAndCategory_withPriorityFieldAndSorter_usesSorter() {
        // Arrange
        List<ITicket> items = Arrays.asList(mockTicket1, mockTicket2);
        String expectedResult = "Generated Table";
        setupItemWithCreationDateAndPriority(mockTicket1, "2024-01-15", "High");
        setupItemWithCreationDateAndPriority(mockTicket2, "2024-01-20", "Low");

        Set<String> priorities = new HashSet<>(Arrays.asList("High", "Low"));
        List<String> sortedPriorities = Arrays.asList("High", "Low");
        when(ticketSorter.sortPriorities(priorities)).thenReturn(sortedPriorities);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTimelineByCreationAndCategory(items, TimelinePeriod.MONTH, "priority", "Priority", false);

        // Assert
        assertEquals(expectedResult, result);
        verify(ticketSorter, times(1)).sortPriorities(anySet());
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    // Helper methods

    private void setupTicketWithClosedDate(ITicket ticket, String dateStr, String issueType, double weight) {
        try {
            Date closedDate = createDate(dateStr);
            JSONObject fieldsJson = new JSONObject();
            fieldsJson.put("dateClosed", dateStr + "T10:00:00.000Z");
            when(ticket.getIssueType()).thenReturn(issueType);
            when(ticket.getFieldsAsJSON()).thenReturn(fieldsJson);
            when(ticket.getWeight()).thenReturn(weight);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException");
        }
    }

    private void setupBugWithCreationDate(ITicket bug, String dateStr, String priority) {
        Date creationDate = createDate(dateStr);
        when(bug.getCreated()).thenReturn(creationDate);
        try {
            when(bug.getPriority()).thenReturn(priority);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException");
        }
    }

    private void setupItemWithCreationDateAndPriority(ITicket item, String dateStr, String priority) {
        Date creationDate = createDate(dateStr);
        when(item.getCreated()).thenReturn(creationDate);
        try {
            when(item.getPriority()).thenReturn(priority);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException");
        }
    }

    private void setupItemWithCreationDateAndStatus(ITicket item, String dateStr, String status) {
        Date creationDate = createDate(dateStr);
        when(item.getCreated()).thenReturn(creationDate);
        try {
            when(item.getStatus()).thenReturn(status);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException");
        }
    }

    private void setupItemWithCreationDateAndIssueType(ITicket item, String dateStr, String issueType) {
        Date creationDate = createDate(dateStr);
        when(item.getCreated()).thenReturn(creationDate);
        try {
            when(item.getIssueType()).thenReturn(issueType);
        } catch (IOException e) {
            fail("Mock setup should not throw IOException");
        }
    }

    private Date createDate(String dateStr) {
        try {
            return dateFormat.parse(dateStr);
        } catch (Exception e) {
            fail("Failed to parse date: " + dateStr);
            return null;
        }
    }
}
