package com.github.istin.dmtools.report.projectstatus.presentation.tables;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.report.projectstatus.model.TableData;
import com.github.istin.dmtools.report.projectstatus.model.TimelinePeriod;
import com.github.istin.dmtools.report.projectstatus.presentation.TableGenerator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentMatchers;

@ExtendWith(MockitoExtension.class)
public class LabelAnalysisGeneratorTest {

    @Mock
    private TableGenerator tableGenerator;

    @Mock
    private TimelineTableGenerator timelineTableGenerator;

    @Mock
    private ITicket mockTicket1;

    @Mock
    private ITicket mockTicket2;

    @Mock
    private ITicket mockTicket3;

    @Mock
    private ITicket mockTicket4;

    private LabelAnalysisGenerator labelAnalysisGenerator;

    @BeforeEach
    void setUp() {
        labelAnalysisGenerator = new LabelAnalysisGenerator(tableGenerator, timelineTableGenerator);
    }

    @Test
    public void testConstructor_withAllParameters_setsFieldsCorrectly() {
        // Arrange
        TableGenerator mockTableGen = mock(TableGenerator.class);
        TimelineTableGenerator mockTimelineGen = mock(TimelineTableGenerator.class);

        // Act
        LabelAnalysisGenerator generator = new LabelAnalysisGenerator(mockTableGen, mockTimelineGen);

        // Assert
        assertNotNull(generator);
    }

    @Test
    public void testGenerateLabelAnalysis_withNullFocusLabels_throwsIllegalArgumentException() {
        // Arrange
        List<ITicket> tickets = new ArrayList<>();
        TimelinePeriod period = TimelinePeriod.MONTH;

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            labelAnalysisGenerator.generateLabelAnalysis(tickets, period, null);
        });

        assertEquals("Focus labels list must not be null or empty", exception.getMessage());
    }

    @Test
    public void testGenerateLabelAnalysis_withEmptyFocusLabels_throwsIllegalArgumentException() {
        // Arrange
        List<ITicket> tickets = new ArrayList<>();
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> emptyLabels = new ArrayList<>();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            labelAnalysisGenerator.generateLabelAnalysis(tickets, period, emptyLabels);
        });

        assertEquals("Focus labels list must not be null or empty", exception.getMessage());
    }

    @Test
    public void testGenerateLabelAnalysis_withEmptyTicketsList_returnsReportWithZeroCounts() {
        // Arrange
        List<ITicket> emptyTickets = new ArrayList<>();
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("label1", "label2");
        String expectedTableResult = "Generated Table";

        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(emptyTickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("# Ticket Label Analysis"));
        assertTrue(result.contains(expectedTableResult));
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withNullTickets_handlesGracefully() {
        // Arrange
        List<ITicket> nullTickets = null;
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("label1");

        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            labelAnalysisGenerator.generateLabelAnalysis(nullTickets, period, focusLabels);
        });
    }

    @Test
    public void testGenerateLabelAnalysis_withTicketsHavingFocusLabels_returnsCorrectReport() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug", "feature");
        String expectedTableResult = "Generated Table";

        JSONArray labels1 = new JSONArray();
        labels1.put("bug");
        labels1.put("critical");

        JSONArray labels2 = new JSONArray();
        labels2.put("feature");

        when(mockTicket1.getTicketLabels()).thenReturn(labels1);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket2.getTicketLabels()).thenReturn(labels2);
        when(mockTicket2.getWeight()).thenReturn(3.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("# Ticket Label Analysis"));
        assertTrue(result.contains(expectedTableResult));
        assertTrue(result.contains("## Label Distribution"));
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withUnlabeledTickets_includesUnlabeledCategory() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug");
        String expectedTableResult = "Generated Table";

        JSONArray labels1 = new JSONArray();
        labels1.put("bug");

        JSONArray emptyLabels = new JSONArray();

        when(mockTicket1.getTicketLabels()).thenReturn(labels1);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket2.getTicketLabels()).thenReturn(emptyLabels);
        when(mockTicket2.getWeight()).thenReturn(3.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("Unlabeled"));
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withNullLabels_includesUnlabeledCategory() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug");
        String expectedTableResult = "Generated Table";

        when(mockTicket1.getTicketLabels()).thenReturn(null);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("Unlabeled"));
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withOtherLabeledTickets_includesOtherLabelsCategory() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug");
        String expectedTableResult = "Generated Table";

        JSONArray labels1 = new JSONArray();
        labels1.put("bug");

        JSONArray labels2 = new JSONArray();
        labels2.put("enhancement");

        when(mockTicket1.getTicketLabels()).thenReturn(labels1);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket2.getTicketLabels()).thenReturn(labels2);
        when(mockTicket2.getWeight()).thenReturn(3.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("Other Labels"));
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withTicketsHavingMultipleLabels_countsCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug", "critical");
        String expectedTableResult = "Generated Table";

        JSONArray labels = new JSONArray();
        labels.put("bug");
        labels.put("critical");
        labels.put("high-priority");

        when(mockTicket1.getTicketLabels()).thenReturn(labels);
        when(mockTicket1.getWeight()).thenReturn(8.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withExceptionInGetTicketLabels_handlesGracefully() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug");
        String expectedTableResult = "Generated Table";

        JSONArray labels2 = new JSONArray();
        labels2.put("bug");

        when(mockTicket1.getTicketLabels()).thenThrow(new RuntimeException("Test exception"));
        when(mockTicket2.getTicketLabels()).thenReturn(labels2);
        when(mockTicket2.getWeight()).thenReturn(5.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("# Ticket Label Analysis"));
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withZeroTotalTickets_handlesDivisionByZero() {
        // Arrange
        List<ITicket> emptyTickets = new ArrayList<>();
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("label1");
        String expectedTableResult = "Generated Table";

        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(emptyTickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withZeroTotalPoints_handlesDivisionByZero() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("label1");
        String expectedTableResult = "Generated Table";

        JSONArray labels = new JSONArray();
        labels.put("other");

        when(mockTicket1.getTicketLabels()).thenReturn(labels);
        when(mockTicket1.getWeight()).thenReturn(0.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withMultipleFocusLabels_sortsByCount() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2, mockTicket3);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug", "feature", "enhancement");
        String expectedTableResult = "Generated Table";

        JSONArray labels1 = new JSONArray();
        labels1.put("bug");

        JSONArray labels2 = new JSONArray();
        labels2.put("feature");
        labels2.put("bug");

        JSONArray labels3 = new JSONArray();
        labels3.put("enhancement");

        when(mockTicket1.getTicketLabels()).thenReturn(labels1);
        when(mockTicket1.getWeight()).thenReturn(3.0);
        when(mockTicket2.getTicketLabels()).thenReturn(labels2);
        when(mockTicket2.getWeight()).thenReturn(5.0);
        when(mockTicket3.getTicketLabels()).thenReturn(labels3);
        when(mockTicket3.getWeight()).thenReturn(2.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_includesPieChartData() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug");
        String expectedTableResult = "Generated Table";

        JSONArray labels = new JSONArray();
        labels.put("bug");

        when(mockTicket1.getTicketLabels()).thenReturn(labels);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("## Label Distribution"));
        assertTrue(result.contains("### Ticket Count by Label"));
        assertTrue(result.contains("### Story Points by Label"));
    }

    @Test
    public void testGenerateLabelAnalysis_withEmptyLabelsArray_includesUnlabeledCategory() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug");
        String expectedTableResult = "Generated Table";

        JSONArray emptyLabels = new JSONArray();

        when(mockTicket1.getTicketLabels()).thenReturn(emptyLabels);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("Unlabeled"));
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withTicketHavingBothFocusAndOtherLabels_categorizesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug");
        String expectedTableResult = "Generated Table";

        JSONArray labels = new JSONArray();
        labels.put("bug");
        labels.put("other-label");

        when(mockTicket1.getTicketLabels()).thenReturn(labels);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_calculatesPercentagesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2, mockTicket3);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug");
        String expectedTableResult = "Generated Table";

        JSONArray labels1 = new JSONArray();
        labels1.put("bug");

        JSONArray labels2 = new JSONArray();
        labels2.put("bug");

        JSONArray labels3 = new JSONArray();
        labels3.put("other");

        when(mockTicket1.getTicketLabels()).thenReturn(labels1);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket2.getTicketLabels()).thenReturn(labels2);
        when(mockTicket2.getWeight()).thenReturn(3.0);
        when(mockTicket3.getTicketLabels()).thenReturn(labels3);
        when(mockTicket3.getWeight()).thenReturn(2.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withDifferentTimelinePeriods_handlesAllPeriods() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        List<String> focusLabels = Arrays.asList("bug");
        String expectedTableResult = "Generated Table";

        JSONArray labels = new JSONArray();
        labels.put("bug");

        when(mockTicket1.getTicketLabels()).thenReturn(labels);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act & Assert - Test all TimelinePeriod values
        for (TimelinePeriod period : TimelinePeriod.values()) {
            String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);
            assertNotNull(result);
            assertTrue(result.contains("# Ticket Label Analysis"));
        }

        verify(tableGenerator, times(TimelinePeriod.values().length)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withTicketHavingNoWeight_usesZeroWeight() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug");
        String expectedTableResult = "Generated Table";

        JSONArray labels = new JSONArray();
        labels.put("bug");

        when(mockTicket1.getTicketLabels()).thenReturn(labels);
        when(mockTicket1.getWeight()).thenReturn(0.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withMultipleTicketsSameLabel_countsAll() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2, mockTicket3);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug");
        String expectedTableResult = "Generated Table";

        JSONArray labels = new JSONArray();
        labels.put("bug");

        when(mockTicket1.getTicketLabels()).thenReturn(labels);
        when(mockTicket1.getWeight()).thenReturn(3.0);
        when(mockTicket2.getTicketLabels()).thenReturn(labels);
        when(mockTicket2.getWeight()).thenReturn(5.0);
        when(mockTicket3.getTicketLabels()).thenReturn(labels);
        when(mockTicket3.getWeight()).thenReturn(2.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_includesTotalRow() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug");
        String expectedTableResult = "Generated Table";

        JSONArray labels1 = new JSONArray();
        labels1.put("bug");

        JSONArray labels2 = new JSONArray();
        labels2.put("other");

        when(mockTicket1.getTicketLabels()).thenReturn(labels1);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket2.getTicketLabels()).thenReturn(labels2);
        when(mockTicket2.getWeight()).thenReturn(3.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        verify(tableGenerator, times(1)).generateTable(ArgumentMatchers.<TableData>argThat(tableData -> {
            List<List<String>> rows = tableData.getRows();
            return rows.stream().anyMatch(row -> row.contains("**Total**"));
        }));
    }

    @Test
    public void testGenerateLabelAnalysis_withExceptionInLabelProcessing_continuesProcessing() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2, mockTicket3);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug");
        String expectedTableResult = "Generated Table";

        JSONArray labels2 = new JSONArray();
        labels2.put("bug");

        JSONArray labels3 = new JSONArray();
        labels3.put("bug");

        when(mockTicket1.getTicketLabels()).thenThrow(new RuntimeException("Error getting labels"));
        when(mockTicket2.getTicketLabels()).thenReturn(labels2);
        when(mockTicket2.getWeight()).thenReturn(5.0);
        when(mockTicket3.getTicketLabels()).thenReturn(labels3);
        when(mockTicket3.getWeight()).thenReturn(3.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("# Ticket Label Analysis"));
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withSpecialCharactersInLabels_handlesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug-fix", "feature/new", "test_label");
        String expectedTableResult = "Generated Table";

        JSONArray labels = new JSONArray();
        labels.put("bug-fix");
        labels.put("feature/new");

        when(mockTicket1.getTicketLabels()).thenReturn(labels);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("# Ticket Label Analysis"));
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withVeryLongLabelNames_handlesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        TimelinePeriod period = TimelinePeriod.MONTH;
        String longLabel = "a".repeat(100);
        List<String> focusLabels = Arrays.asList(longLabel);
        String expectedTableResult = "Generated Table";

        JSONArray labels = new JSONArray();
        labels.put(longLabel);

        when(mockTicket1.getTicketLabels()).thenReturn(labels);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("# Ticket Label Analysis"));
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withUnicodeCharactersInLabels_handlesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("バグ", "功能", "🐛");
        String expectedTableResult = "Generated Table";

        JSONArray labels = new JSONArray();
        labels.put("バグ");
        labels.put("功能");

        when(mockTicket1.getTicketLabels()).thenReturn(labels);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("# Ticket Label Analysis"));
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withVeryLargeWeights_handlesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug");
        String expectedTableResult = "Generated Table";

        JSONArray labels1 = new JSONArray();
        labels1.put("bug");

        JSONArray labels2 = new JSONArray();
        labels2.put("bug");

        when(mockTicket1.getTicketLabels()).thenReturn(labels1);
        when(mockTicket1.getWeight()).thenReturn(1000000.0);
        when(mockTicket2.getTicketLabels()).thenReturn(labels2);
        when(mockTicket2.getWeight()).thenReturn(500000.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("# Ticket Label Analysis"));
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withDecimalWeights_handlesPrecision() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug");
        String expectedTableResult = "Generated Table";

        JSONArray labels1 = new JSONArray();
        labels1.put("bug");

        JSONArray labels2 = new JSONArray();
        labels2.put("bug");

        when(mockTicket1.getTicketLabels()).thenReturn(labels1);
        when(mockTicket1.getWeight()).thenReturn(2.5);
        when(mockTicket2.getTicketLabels()).thenReturn(labels2);
        when(mockTicket2.getWeight()).thenReturn(3.75);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("# Ticket Label Analysis"));
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withLabelsHavingSameCount_sortsConsistently() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2, mockTicket3, mockTicket4);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug", "feature");
        String expectedTableResult = "Generated Table";

        JSONArray labels1 = new JSONArray();
        labels1.put("bug");

        JSONArray labels2 = new JSONArray();
        labels2.put("feature");

        JSONArray labels3 = new JSONArray();
        labels3.put("bug");

        JSONArray labels4 = new JSONArray();
        labels4.put("feature");

        when(mockTicket1.getTicketLabels()).thenReturn(labels1);
        when(mockTicket1.getWeight()).thenReturn(3.0);
        when(mockTicket2.getTicketLabels()).thenReturn(labels2);
        when(mockTicket2.getWeight()).thenReturn(3.0);
        when(mockTicket3.getTicketLabels()).thenReturn(labels3);
        when(mockTicket3.getWeight()).thenReturn(3.0);
        when(mockTicket4.getTicketLabels()).thenReturn(labels4);
        when(mockTicket4.getWeight()).thenReturn(3.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("# Ticket Label Analysis"));
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withAllLabelsZeroCount_includesAllInReport() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug", "feature", "enhancement");
        String expectedTableResult = "Generated Table";

        JSONArray labels = new JSONArray();
        labels.put("other-label");

        when(mockTicket1.getTicketLabels()).thenReturn(labels);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("# Ticket Label Analysis"));
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_includesFootnoteInTableData() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug");
        String expectedTableResult = "Generated Table";

        JSONArray labels = new JSONArray();
        labels.put("bug");

        when(mockTicket1.getTicketLabels()).thenReturn(labels);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        verify(tableGenerator, times(1)).generateTable(ArgumentMatchers.<TableData>argThat(tableData -> {
            return tableData.hasFootnote() && 
                   tableData.getFootnote().contains("Tickets may have multiple labels");
        }));
    }

    @Test
    public void testGenerateLabelAnalysis_withHundredPercentLabel_showsCorrectPercentage() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug");
        String expectedTableResult = "Generated Table";

        JSONArray labels = new JSONArray();
        labels.put("bug");

        when(mockTicket1.getTicketLabels()).thenReturn(labels);
        when(mockTicket1.getWeight()).thenReturn(10.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("# Ticket Label Analysis"));
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withTicketHavingExceptionInLabelStringAccess_handlesGracefully() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug");
        String expectedTableResult = "Generated Table";

        JSONArray labels1 = new JSONArray();
        labels1.put("bug");

        JSONArray labels2 = mock(JSONArray.class);
        when(labels2.length()).thenReturn(1);
        when(labels2.getString(0)).thenThrow(new RuntimeException("Error accessing label"));

        when(mockTicket1.getTicketLabels()).thenReturn(labels1);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket2.getTicketLabels()).thenReturn(labels2);
        when(mockTicket2.getWeight()).thenReturn(3.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("# Ticket Label Analysis"));
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withMultipleTicketsMultipleLabels_countsEachLabel() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2, mockTicket3);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug", "feature", "enhancement");
        String expectedTableResult = "Generated Table";

        JSONArray labels1 = new JSONArray();
        labels1.put("bug");
        labels1.put("feature");

        JSONArray labels2 = new JSONArray();
        labels2.put("feature");
        labels2.put("enhancement");

        JSONArray labels3 = new JSONArray();
        labels3.put("bug");
        labels3.put("enhancement");

        when(mockTicket1.getTicketLabels()).thenReturn(labels1);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket2.getTicketLabels()).thenReturn(labels2);
        when(mockTicket2.getWeight()).thenReturn(3.0);
        when(mockTicket3.getTicketLabels()).thenReturn(labels3);
        when(mockTicket3.getWeight()).thenReturn(2.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("# Ticket Label Analysis"));
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateLabelAnalysis_withPieChartDataFormatsCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug", "feature");
        String expectedTableResult = "Generated Table";

        JSONArray labels1 = new JSONArray();
        labels1.put("bug");

        JSONArray labels2 = new JSONArray();
        labels2.put("feature");

        when(mockTicket1.getTicketLabels()).thenReturn(labels1);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket2.getTicketLabels()).thenReturn(labels2);
        when(mockTicket2.getWeight()).thenReturn(3.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("## Label Distribution"));
        assertTrue(result.contains("### Ticket Count by Label"));
        assertTrue(result.contains("### Story Points by Label"));
        assertTrue(result.contains("```"));
    }

    @Test
    public void testGenerateLabelAnalysis_withSingleTicketSingleLabel_generatesCompleteReport() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        TimelinePeriod period = TimelinePeriod.MONTH;
        List<String> focusLabels = Arrays.asList("bug");
        String expectedTableResult = "Generated Table";

        JSONArray labels = new JSONArray();
        labels.put("bug");

        when(mockTicket1.getTicketLabels()).thenReturn(labels);
        when(mockTicket1.getWeight()).thenReturn(8.0);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn(expectedTableResult);

        // Act
        String result = labelAnalysisGenerator.generateLabelAnalysis(tickets, period, focusLabels);

        // Assert
        assertNotNull(result);
        assertTrue(result.startsWith("# Ticket Label Analysis"));
        assertTrue(result.contains("## Label Distribution"));
        verify(tableGenerator, times(1)).generateTable(any(TableData.class));
    }

}
