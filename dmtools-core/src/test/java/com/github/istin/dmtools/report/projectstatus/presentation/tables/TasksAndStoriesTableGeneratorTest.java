package com.github.istin.dmtools.report.projectstatus.presentation.tables;

import com.github.istin.dmtools.common.model.ITicket;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TasksAndStoriesTableGeneratorTest {

    @Mock
    private TableGenerator baseTableGenerator;

    @Mock
    private ITicket mockTicket1;

    @Mock
    private ITicket mockTicket2;

    @Mock
    private ITicket mockTicket3;

    private TasksAndStoriesTableGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new TasksAndStoriesTableGenerator(baseTableGenerator);
    }

    @Test
    public void testConstructor_withBaseTableGenerator_setsFieldCorrectly() {
        // Arrange
        TableGenerator mockBase = mock(TableGenerator.class);

        // Act
        TasksAndStoriesTableGenerator newGenerator = new TasksAndStoriesTableGenerator(mockBase);

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
    public void testGenerateTable_withTicketList_callsGenerateTasksAndStoriesTable() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        JSONArray labels = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("TASK-1");
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getWeight()).thenReturn(5.0);
            when(mockTicket1.getTicketTitle()).thenReturn("Test Task");
            when(mockTicket1.getTicketDescription()).thenReturn("Test Description");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        String result = generator.generateTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTable_withTicketListColumnsAndFlags_callsGenerateTableWithTickets() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        JSONArray labels = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("TASK-1");
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getWeight()).thenReturn(5.0);
            when(mockTicket1.getTicketTitle()).thenReturn("Test Task");
            when(mockTicket1.getTicketDescription()).thenReturn("Test Description");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        String result = generator.generateTable(tickets, new String[]{"Key", "Type"}, true, true);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTasksAndStoriesTable_withEmptyList_returnsTableWithHeadersOnly() {
        // Arrange
        List<ITicket> emptyList = new ArrayList<>();
        String expectedResult = "Generated Empty Table";

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTasksAndStoriesTable(emptyList);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTasksAndStoriesTable_withSingleTicket_returnsCorrectTable() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        JSONArray labels = new JSONArray();
        labels.put("label1");
        labels.put("label2");

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("TASK-1");
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getWeight()).thenReturn(5.0);
            when(mockTicket1.getTicketTitle()).thenReturn("Test Task");
            when(mockTicket1.getTicketDescription()).thenReturn("Test Description");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        String result = generator.generateTasksAndStoriesTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTasksAndStoriesTable_withMultipleTickets_returnsAllTickets() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson1 = new JSONObject();
        fieldsJson1.put("dateClosed", "2024-01-01");
        JSONObject fieldsJson2 = new JSONObject();
        fieldsJson2.put("dateClosed", "2024-01-02");
        JSONArray labels1 = new JSONArray();
        labels1.put("label1");
        JSONArray labels2 = new JSONArray();
        labels2.put("label2");

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("TASK-1");
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getWeight()).thenReturn(5.0);
            when(mockTicket1.getTicketTitle()).thenReturn("Task 1");
            when(mockTicket1.getTicketDescription()).thenReturn("Description 1");

            when(mockTicket2.getKey()).thenReturn("STORY-1");
            when(mockTicket2.getIssueType()).thenReturn("Story");
            when(mockTicket2.getPriority()).thenReturn("Medium");
            when(mockTicket2.getWeight()).thenReturn(8.0);
            when(mockTicket2.getTicketTitle()).thenReturn("Story 1");
            when(mockTicket2.getTicketDescription()).thenReturn("Description 2");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson1);
        when(mockTicket1.getTicketLabels()).thenReturn(labels1);
        when(mockTicket2.getFieldsAsJSON()).thenReturn(fieldsJson2);
        when(mockTicket2.getTicketLabels()).thenReturn(labels2);

        // Act
        String result = generator.generateTasksAndStoriesTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }


    @Test
    public void testGenerateTasksAndStoriesTable_withEmptyLabels_handlesGracefully() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        JSONArray labels = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("TASK-1");
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getWeight()).thenReturn(5.0);
            when(mockTicket1.getTicketTitle()).thenReturn("Test Task");
            when(mockTicket1.getTicketDescription()).thenReturn("Test Description");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        String result = generator.generateTasksAndStoriesTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTasksAndStoriesTable_withIOException_handlesGracefully() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        JSONArray labels = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("TASK-1");
            when(mockTicket1.getIssueType()).thenThrow(new IOException("Test exception"));
            lenient().when(mockTicket1.getPriority()).thenReturn("High");
            lenient().when(mockTicket1.getWeight()).thenReturn(5.0);
            lenient().when(mockTicket1.getTicketTitle()).thenReturn("Test Task");
            lenient().when(mockTicket1.getTicketDescription()).thenReturn("Test Description");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        lenient().when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        lenient().when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        String result = generator.generateTasksAndStoriesTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }


    @Test
    public void testGenerateTasksAndStoriesTable_withZeroWeight_handlesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        JSONArray labels = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("TASK-1");
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getWeight()).thenReturn(0.0);
            when(mockTicket1.getTicketTitle()).thenReturn("Test Task");
            when(mockTicket1.getTicketDescription()).thenReturn("Test Description");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        String result = generator.generateTasksAndStoriesTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTasksAndStoriesTable_withLargeWeight_handlesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        JSONArray labels = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("TASK-1");
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getWeight()).thenReturn(100.0);
            when(mockTicket1.getTicketTitle()).thenReturn("Test Task");
            when(mockTicket1.getTicketDescription()).thenReturn("Test Description");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        String result = generator.generateTasksAndStoriesTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTasksAndStoriesTable_withDescriptionContainingUrls_removesUrls() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        JSONArray labels = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("TASK-1");
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getWeight()).thenReturn(5.0);
            when(mockTicket1.getTicketTitle()).thenReturn("Test Task");
            when(mockTicket1.getTicketDescription()).thenReturn("Check https://example.com for details");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        String result = generator.generateTasksAndStoriesTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTasksAndStoriesTable_withMultipleTicketsAndIOException_skipsFailingTicket() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson1 = new JSONObject();
        fieldsJson1.put("dateClosed", "2024-01-01");
        JSONObject fieldsJson2 = new JSONObject();
        fieldsJson2.put("dateClosed", "2024-01-02");
        JSONArray labels1 = new JSONArray();
        JSONArray labels2 = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("TASK-1");
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getWeight()).thenReturn(5.0);
            when(mockTicket1.getTicketTitle()).thenReturn("Task 1");
            when(mockTicket1.getTicketDescription()).thenReturn("Description 1");

            when(mockTicket2.getKey()).thenReturn("TASK-2");
            when(mockTicket2.getIssueType()).thenThrow(new IOException("Test exception"));
            lenient().when(mockTicket2.getPriority()).thenReturn("Medium");
            lenient().when(mockTicket2.getWeight()).thenReturn(8.0);
            lenient().when(mockTicket2.getTicketTitle()).thenReturn("Task 2");
            lenient().when(mockTicket2.getTicketDescription()).thenReturn("Description 2");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson1);
        when(mockTicket1.getTicketLabels()).thenReturn(labels1);
        lenient().when(mockTicket2.getFieldsAsJSON()).thenReturn(fieldsJson2);
        lenient().when(mockTicket2.getTicketLabels()).thenReturn(labels2);

        // Act
        String result = generator.generateTasksAndStoriesTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTasksAndStoriesTable_createsCorrectHeaders() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        JSONArray labels = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("TASK-1");
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getWeight()).thenReturn(5.0);
            when(mockTicket1.getTicketTitle()).thenReturn("Test Task");
            when(mockTicket1.getTicketDescription()).thenReturn("Test Description");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        generator.generateTasksAndStoriesTable(tickets);

        // Assert
        verify(baseTableGenerator, times(1)).generateTable(argThat((TableData tableData) -> {
            List<String> headers = tableData.getHeaders();
            return headers.contains("Key") &&
                   headers.contains("Type") &&
                   headers.contains("Priority") &&
                   headers.contains("Story Points") &&
                   headers.contains("Closed Date") &&
                   headers.contains("Labels") &&
                   headers.contains("Summary") &&
                   headers.contains("Description") &&
                   headers.size() == 8;
        }));
    }

    @Test
    public void testGenerateTasksAndStoriesTable_setsCorrectTitle() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        String expectedResult = "Generated Table";
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");
        JSONArray labels = new JSONArray();

        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        try {
            when(mockTicket1.getKey()).thenReturn("TASK-1");
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getWeight()).thenReturn(5.0);
            when(mockTicket1.getTicketTitle()).thenReturn("Test Task");
            when(mockTicket1.getTicketDescription()).thenReturn("Test Description");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket1.getTicketLabels()).thenReturn(labels);

        // Act
        generator.generateTasksAndStoriesTable(tickets);

        // Assert
        verify(baseTableGenerator, times(1)).generateTable(argThat((TableData tableData) ->
            "Tasks And Stories Work Items".equals(tableData.getTitle())
        ));
    }
}
