package com.github.istin.dmtools.report.projectstatus.presentation.tables;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.report.projectstatus.config.ReportConfiguration;
import com.github.istin.dmtools.report.projectstatus.data.TicketSorter;
import com.github.istin.dmtools.report.projectstatus.data.TicketStatisticsCalculator;
import com.github.istin.dmtools.report.projectstatus.model.TableData;
import com.github.istin.dmtools.report.projectstatus.presentation.TableGenerator;
import org.json.JSONObject;
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
public class RoleDistributionTableGeneratorTest {

    @Mock
    private TableGenerator baseTableGenerator;

    @Mock
    private TicketStatisticsCalculator statisticsCalculator;

    @Mock
    private TicketSorter ticketSorter;

    @Mock
    private ReportConfiguration config;

    @Mock
    private ITicket mockTicket1;

    @Mock
    private ITicket mockTicket2;

    @Mock
    private ITicket mockTicket3;

    @Mock
    private ITicket mockTicket4;

    private RoleDistributionTableGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new RoleDistributionTableGenerator(
            baseTableGenerator,
            statisticsCalculator,
            ticketSorter,
            config
        );
    }

    @Test
    public void testConstructor_withAllParameters_setsFieldsCorrectly() {
        // Arrange
        TableGenerator mockBase = mock(TableGenerator.class);
        TicketStatisticsCalculator mockStats = mock(TicketStatisticsCalculator.class);
        TicketSorter mockSorter = mock(TicketSorter.class);
        ReportConfiguration mockConfig = mock(ReportConfiguration.class);

        // Act
        RoleDistributionTableGenerator newGenerator = new RoleDistributionTableGenerator(
            mockBase, mockStats, mockSorter, mockConfig
        );

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
    public void testGenerateTable_withTicketList_callsGenerateRoleBasedDistributionTable() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("Development", tickets);
        List<String> sortedRoles = Arrays.asList("Development");
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("Development", "Development");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.categorizeTicketsByRole(tickets)).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket1.getKey()).thenReturn("TICKET-1");
        when(statisticsCalculator.calculatePercentage(1, 1)).thenReturn(100.0);
        when(statisticsCalculator.calculatePercentage(5.0, 5.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(1)).categorizeTicketsByRole(tickets);
        verify(ticketSorter, times(1)).sortRoles(ticketsByRole.keySet());
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateTable_withTicketListColumnsAndFlags_callsGenerateTableWithTickets() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("Development", tickets);
        List<String> sortedRoles = Arrays.asList("Development");
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("Development", "Development");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.categorizeTicketsByRole(tickets)).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket1.getKey()).thenReturn("TICKET-1");
        when(statisticsCalculator.calculatePercentage(1, 1)).thenReturn(100.0);
        when(statisticsCalculator.calculatePercentage(5.0, 5.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateTable(tickets, new String[]{"Key", "Role"}, true, false);

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(1)).categorizeTicketsByRole(tickets);
    }

    @Test
    public void testGenerateRoleBasedDistributionTable_withEmptyList_returnsTableWithTotalRow() {
        // Arrange
        List<ITicket> emptyList = new ArrayList<>();
        Map<String, List<ITicket>> emptyMap = new HashMap<>();
        List<String> emptyRoles = new ArrayList<>();
        Map<String, String> roleDescriptions = new HashMap<>();
        String expectedResult = "Generated Empty Table";

        when(statisticsCalculator.categorizeTicketsByRole(emptyList)).thenReturn(emptyMap);
        when(ticketSorter.sortRoles(emptyMap.keySet())).thenReturn(emptyRoles);
        lenient().when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateRoleBasedDistributionTable(emptyList);

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(1)).categorizeTicketsByRole(emptyList);
        verify(ticketSorter, times(1)).sortRoles(emptyMap.keySet());
        verify(baseTableGenerator, times(1)).generateTable(ArgumentMatchers.<TableData>argThat(tableData -> {
            List<List<String>> rows = tableData.getRows();
            if (rows.isEmpty()) {
                return false;
            }
            List<String> totalsRow = rows.get(rows.size() - 1);
            return totalsRow.get(0).equals("**Total**") && totalsRow.get(1).equals("0");
        }));
    }

    @Test
    public void testGenerateRoleBasedDistributionTable_withSingleTicket_returnsCorrectTable() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("Development", tickets);
        List<String> sortedRoles = Arrays.asList("Development");
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("Development", "Development");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.categorizeTicketsByRole(tickets)).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket1.getKey()).thenReturn("TICKET-1");
        when(statisticsCalculator.calculatePercentage(1, 1)).thenReturn(100.0);
        when(statisticsCalculator.calculatePercentage(5.0, 5.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateRoleBasedDistributionTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(1)).categorizeTicketsByRole(tickets);
        verify(statisticsCalculator, times(2)).calculatePercentage(anyDouble(), anyDouble());
        verify(baseTableGenerator, times(1)).generateTable(ArgumentMatchers.<TableData>argThat(tableData -> {
            List<String> headers = tableData.getHeaders();
            return headers.size() == 6 &&
                   headers.contains("Role") &&
                   headers.contains("Ticket Count") &&
                   headers.contains("% of Tickets") &&
                   headers.contains("Story Points") &&
                   headers.contains("% of Points") &&
                   headers.contains("Avg. Points per Ticket");
        }));
    }

    @Test
    public void testGenerateRoleBasedDistributionTable_withMultipleRoles_returnsSortedTable() {
        // Arrange
        List<ITicket> devTickets = Arrays.asList(mockTicket1, mockTicket2);
        List<ITicket> qaTickets = Arrays.asList(mockTicket3);
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("Development", devTickets);
        ticketsByRole.put("QA", qaTickets);
        List<String> sortedRoles = Arrays.asList("QA", "Development");
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("Development", "Development");
        roleDescriptions.put("QA", "Quality Assurance");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.categorizeTicketsByRole(anyList())).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket1.getKey()).thenReturn("TICKET-1");
        when(mockTicket2.getWeight()).thenReturn(3.0);
        when(mockTicket2.getKey()).thenReturn("TICKET-2");
        when(mockTicket3.getWeight()).thenReturn(2.0);
        when(mockTicket3.getKey()).thenReturn("TICKET-3");
        when(statisticsCalculator.calculatePercentage(2, 3)).thenReturn(66.7);
        when(statisticsCalculator.calculatePercentage(1, 3)).thenReturn(33.3);
        when(statisticsCalculator.calculatePercentage(8.0, 10.0)).thenReturn(80.0);
        when(statisticsCalculator.calculatePercentage(2.0, 10.0)).thenReturn(20.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateRoleBasedDistributionTable(
            Arrays.asList(mockTicket1, mockTicket2, mockTicket3)
        );

        // Assert
        assertEquals(expectedResult, result);
        verify(statisticsCalculator, times(4)).calculatePercentage(anyDouble(), anyDouble());
        verify(ticketSorter, times(1)).sortRoles(ticketsByRole.keySet());
    }

    @Test
    public void testGenerateRoleBasedDistributionTable_withZeroStoryPoints_handlesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("Development", tickets);
        List<String> sortedRoles = Arrays.asList("Development");
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("Development", "Development");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.categorizeTicketsByRole(tickets)).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        when(mockTicket1.getWeight()).thenReturn(0.0);
        when(mockTicket1.getKey()).thenReturn("TICKET-1");
        when(statisticsCalculator.calculatePercentage(1, 1)).thenReturn(100.0);
        when(statisticsCalculator.calculatePercentage(0.0, 0.0)).thenReturn(0.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateRoleBasedDistributionTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateRoleBasedDistributionTable_withMissingRoleDescription_usesRoleName() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("CustomRole", tickets);
        List<String> sortedRoles = Arrays.asList("CustomRole");
        Map<String, String> roleDescriptions = new HashMap<>();
        String expectedResult = "Generated Table";

        when(statisticsCalculator.categorizeTicketsByRole(tickets)).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket1.getKey()).thenReturn("TICKET-1");
        when(statisticsCalculator.calculatePercentage(1, 1)).thenReturn(100.0);
        when(statisticsCalculator.calculatePercentage(5.0, 5.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateRoleBasedDistributionTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(ArgumentMatchers.<TableData>argThat(tableData -> {
            List<List<String>> rows = tableData.getRows();
            if (rows.isEmpty()) {
                return false;
            }
            List<String> firstRow = rows.get(0);
            return firstRow.get(0).equals("CustomRole");
        }));
    }

    @Test
    public void testGenerateRoleBasedDistributionTable_withMultipleTicketsSameRole_calculatesTotalsCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2, mockTicket3);
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("Development", tickets);
        List<String> sortedRoles = Arrays.asList("Development");
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("Development", "Development");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.categorizeTicketsByRole(tickets)).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket1.getKey()).thenReturn("TICKET-1");
        when(mockTicket2.getWeight()).thenReturn(3.0);
        when(mockTicket2.getKey()).thenReturn("TICKET-2");
        when(mockTicket3.getWeight()).thenReturn(2.0);
        when(mockTicket3.getKey()).thenReturn("TICKET-3");
        when(statisticsCalculator.calculatePercentage(3, 3)).thenReturn(100.0);
        when(statisticsCalculator.calculatePercentage(10.0, 10.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateRoleBasedDistributionTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(ArgumentMatchers.<TableData>argThat(tableData -> {
            List<List<String>> rows = tableData.getRows();
            if (rows.size() < 2) {
                return false;
            }
            List<String> totalsRow = rows.get(rows.size() - 1);
            return totalsRow.get(0).equals("**Total**") &&
                   totalsRow.get(1).equals("3") &&
                   totalsRow.get(3).equals("10.0");
        }));
    }

    @Test
    public void testGenerateRoleBasedDistributionTable_withZeroTotalTickets_handlesDivisionByZero() {
        // Arrange
        List<ITicket> emptyList = new ArrayList<>();
        Map<String, List<ITicket>> emptyMap = new HashMap<>();
        List<String> emptyRoles = new ArrayList<>();
        Map<String, String> roleDescriptions = new HashMap<>();
        String expectedResult = "Generated Table";

        when(statisticsCalculator.categorizeTicketsByRole(emptyList)).thenReturn(emptyMap);
        when(ticketSorter.sortRoles(emptyMap.keySet())).thenReturn(emptyRoles);
        lenient().when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateRoleBasedDistributionTable(emptyList);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(any(TableData.class));
        verify(statisticsCalculator, never()).calculatePercentage(anyDouble(), anyDouble());
    }

    @Test
    public void testGenerateRoleSpecificTables_withEmptyList_returnsHeaderOnly() {
        // Arrange
        List<ITicket> emptyList = new ArrayList<>();
        Map<String, List<ITicket>> emptyMap = new HashMap<>();
        List<String> emptyRoles = new ArrayList<>();
        Map<String, String> roleDescriptions = new HashMap<>();

        when(statisticsCalculator.categorizeTicketsByRole(emptyList)).thenReturn(emptyMap);
        when(ticketSorter.sortRoles(emptyMap.keySet())).thenReturn(emptyRoles);
        lenient().when(config.getRoleDescriptions()).thenReturn(roleDescriptions);

        // Act
        String result = generator.generateRoleSpecificTables(emptyList);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("## Role-Specific Work Details"));
        verify(statisticsCalculator, times(1)).categorizeTicketsByRole(emptyList);
        verify(baseTableGenerator, never()).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateRoleSpecificTables_withSingleRole_generatesTableForRole() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("Development", tickets);
        List<String> sortedRoles = Arrays.asList("Development");
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("Development", "Development");
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");

        when(statisticsCalculator.categorizeTicketsByRole(tickets)).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getTicketTitle()).thenReturn("Test Ticket");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket1.getKey()).thenReturn("TICKET-1");
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(statisticsCalculator.calculatePercentage(5.0, 5.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn("Generated Table");

        // Act
        String result = generator.generateRoleSpecificTables(tickets);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("### Development Work"));
        verify(statisticsCalculator, times(1)).categorizeTicketsByRole(tickets);
    }

    @Test
    public void testGenerateRoleSpecificTables_withMultipleRoles_generatesTablesForAllRoles() {
        // Arrange
        List<ITicket> devTickets = Arrays.asList(mockTicket1);
        List<ITicket> qaTickets = Arrays.asList(mockTicket2);
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("Development", devTickets);
        ticketsByRole.put("QA", qaTickets);
        List<String> sortedRoles = Arrays.asList("QA", "Development");
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("Development", "Development");
        roleDescriptions.put("QA", "Quality Assurance");
        JSONObject fieldsJson1 = new JSONObject();
        fieldsJson1.put("dateClosed", "2024-01-01");
        JSONObject fieldsJson2 = new JSONObject();
        fieldsJson2.put("dateClosed", "2024-01-02");

        when(statisticsCalculator.categorizeTicketsByRole(anyList())).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getTicketTitle()).thenReturn("Dev Ticket");
            when(mockTicket2.getIssueType()).thenReturn("Bug");
            when(mockTicket2.getPriority()).thenReturn("Medium");
            when(mockTicket2.getTicketTitle()).thenReturn("QA Ticket");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket1.getKey()).thenReturn("TICKET-1");
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson1);
        when(mockTicket2.getWeight()).thenReturn(3.0);
        when(mockTicket2.getKey()).thenReturn("TICKET-2");
        when(mockTicket2.getFieldsAsJSON()).thenReturn(fieldsJson2);
        when(statisticsCalculator.calculatePercentage(anyDouble(), anyDouble())).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn("Generated Table");

        // Act
        String result = generator.generateRoleSpecificTables(
            Arrays.asList(mockTicket1, mockTicket2)
        );

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("### Quality Assurance Work"));
        assertTrue(result.contains("### Development Work"));
        verify(statisticsCalculator, atLeast(1)).categorizeTicketsByRole(anyList());
    }

    @Test
    public void testGenerateRoleSpecificTables_withIOExceptionInStoryPoints_handlesGracefully() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("Development", tickets);
        List<String> sortedRoles = Arrays.asList("Development");
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("Development", "Development");

        when(statisticsCalculator.categorizeTicketsByRole(tickets)).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        lenient().when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        try {
            when(mockTicket1.getIssueType()).thenThrow(new IOException("Test exception"));
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        lenient().when(mockTicket1.getWeight()).thenReturn(5.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn("Generated Table");

        // Act
        String result = generator.generateRoleSpecificTables(tickets);

        // Assert
        assertNotNull(result);
        verify(baseTableGenerator, atLeast(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateRoleSpecificTables_withIOExceptionInTicketTable_handlesGracefully() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("Development", tickets);
        List<String> sortedRoles = Arrays.asList("Development");
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("Development", "Development");
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");

        when(statisticsCalculator.categorizeTicketsByRole(tickets)).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getTicketTitle()).thenThrow(new IOException("Test exception"));
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket1.getKey()).thenReturn("TICKET-1");
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(statisticsCalculator.calculatePercentage(5.0, 5.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn("Generated Table");

        // Act
        String result = generator.generateRoleSpecificTables(tickets);

        // Assert
        assertNotNull(result);
        verify(baseTableGenerator, atLeast(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateRoleSpecificTables_withNullIssueType_usesDefaultTask() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("Development", tickets);
        List<String> sortedRoles = Arrays.asList("Development");
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("Development", "Development");
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");

        when(statisticsCalculator.categorizeTicketsByRole(tickets)).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        try {
            when(mockTicket1.getIssueType()).thenReturn(null);
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getTicketTitle()).thenReturn("Test Ticket");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket1.getKey()).thenReturn("TICKET-1");
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(statisticsCalculator.calculatePercentage(5.0, 5.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn("Generated Table");

        // Act
        String result = generator.generateRoleSpecificTables(tickets);

        // Assert
        assertNotNull(result);
        verify(baseTableGenerator, atLeast(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateRoleSpecificTables_withNullPriority_usesDefaultTrivial() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("Development", tickets);
        List<String> sortedRoles = Arrays.asList("Development");
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("Development", "Development");
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");

        when(statisticsCalculator.categorizeTicketsByRole(tickets)).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getPriority()).thenReturn(null);
            when(mockTicket1.getTicketTitle()).thenReturn("Test Ticket");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket1.getKey()).thenReturn("TICKET-1");
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(statisticsCalculator.calculatePercentage(5.0, 5.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn("Generated Table");

        // Act
        String result = generator.generateRoleSpecificTables(tickets);

        // Assert
        assertNotNull(result);
        verify(baseTableGenerator, atLeast(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateRoleBasedDistributionTable_withNullRoleDescription_usesRoleName() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("CustomRole", tickets);
        List<String> sortedRoles = Arrays.asList("CustomRole");
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("CustomRole", null);
        String expectedResult = "Generated Table";

        when(statisticsCalculator.categorizeTicketsByRole(tickets)).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket1.getKey()).thenReturn("TICKET-1");
        when(statisticsCalculator.calculatePercentage(1, 1)).thenReturn(100.0);
        when(statisticsCalculator.calculatePercentage(5.0, 5.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateRoleBasedDistributionTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(ArgumentMatchers.<TableData>argThat(tableData -> {
            List<List<String>> rows = tableData.getRows();
            if (rows.isEmpty()) {
                return false;
            }
            List<String> firstRow = rows.get(0);
            String roleDescription = firstRow.get(0);
            return roleDescription == null || roleDescription.equals("CustomRole");
        }));
    }

    @Test
    public void testGenerateRoleBasedDistributionTable_calculatesAveragePointsPerTicket() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2);
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("Development", tickets);
        List<String> sortedRoles = Arrays.asList("Development");
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("Development", "Development");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.categorizeTicketsByRole(tickets)).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket1.getKey()).thenReturn("TICKET-1");
        when(mockTicket2.getWeight()).thenReturn(3.0);
        when(mockTicket2.getKey()).thenReturn("TICKET-2");
        when(statisticsCalculator.calculatePercentage(2, 2)).thenReturn(100.0);
        when(statisticsCalculator.calculatePercentage(8.0, 8.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateRoleBasedDistributionTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(ArgumentMatchers.<TableData>argThat(tableData -> {
            List<List<String>> rows = tableData.getRows();
            if (rows.isEmpty()) {
                return false;
            }
            List<String> firstRow = rows.get(0);
            double avgPoints = Double.parseDouble(firstRow.get(5));
            return Math.abs(avgPoints - 4.0) < 0.01;
        }));
    }

    @Test
    public void testGenerateRoleBasedDistributionTable_withZeroCount_handlesDivisionByZero() {
        // Arrange
        List<ITicket> tickets = new ArrayList<>();
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("Development", tickets);
        List<String> sortedRoles = Arrays.asList("Development");
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("Development", "Development");
        String expectedResult = "Generated Table";

        when(statisticsCalculator.categorizeTicketsByRole(tickets)).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        when(statisticsCalculator.calculatePercentage(0, 0)).thenReturn(0.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn(expectedResult);

        // Act
        String result = generator.generateRoleBasedDistributionTable(tickets);

        // Assert
        assertEquals(expectedResult, result);
        verify(baseTableGenerator, times(1)).generateTable(ArgumentMatchers.<TableData>argThat(tableData -> {
            List<List<String>> rows = tableData.getRows();
            if (rows.isEmpty()) {
                return false;
            }
            List<String> firstRow = rows.get(0);
            double avgPoints = Double.parseDouble(firstRow.get(5));
            return Math.abs(avgPoints - 0.0) < 0.01;
        }));
    }

    @Test
    public void testGenerateRoleSpecificTables_withRoleHavingEmptyTickets_generatesHeaderOnly() {
        // Arrange
        List<ITicket> emptyTickets = new ArrayList<>();
        List<ITicket> devTickets = Arrays.asList(mockTicket1);
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("Development", devTickets);
        ticketsByRole.put("QA", emptyTickets);
        List<String> sortedRoles = Arrays.asList("Development", "QA");
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("Development", "Development");
        roleDescriptions.put("QA", "Quality Assurance");
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");

        when(statisticsCalculator.categorizeTicketsByRole(anyList())).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getTicketTitle()).thenReturn("Dev Ticket");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket1.getKey()).thenReturn("TICKET-1");
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(statisticsCalculator.calculatePercentage(5.0, 5.0)).thenReturn(100.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn("Generated Table");

        // Act
        String result = generator.generateRoleSpecificTables(devTickets);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("### Development Work"));
        assertTrue(result.contains("### Quality Assurance Work"));
        verify(baseTableGenerator, atLeast(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateRoleSpecificTables_withMultipleIssueTypes_sortsCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1, mockTicket2, mockTicket3);
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("Development", tickets);
        List<String> sortedRoles = Arrays.asList("Development");
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("Development", "Development");
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");

        when(statisticsCalculator.categorizeTicketsByRole(tickets)).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        try {
            when(mockTicket1.getIssueType()).thenReturn("Bug");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getTicketTitle()).thenReturn("Bug Ticket");
            when(mockTicket2.getIssueType()).thenReturn("Task");
            when(mockTicket2.getPriority()).thenReturn("Medium");
            when(mockTicket2.getTicketTitle()).thenReturn("Task Ticket");
            when(mockTicket3.getIssueType()).thenReturn("Story");
            when(mockTicket3.getPriority()).thenReturn("Low");
            when(mockTicket3.getTicketTitle()).thenReturn("Story Ticket");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getWeight()).thenReturn(3.0);
        when(mockTicket1.getKey()).thenReturn("TICKET-1");
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket2.getWeight()).thenReturn(5.0);
        when(mockTicket2.getKey()).thenReturn("TICKET-2");
        when(mockTicket2.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(mockTicket3.getWeight()).thenReturn(8.0);
        when(mockTicket3.getKey()).thenReturn("TICKET-3");
        when(mockTicket3.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(statisticsCalculator.calculatePercentage(anyDouble(), anyDouble())).thenReturn(18.75, 31.25, 50.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn("Generated Table");

        // Act
        String result = generator.generateRoleSpecificTables(tickets);

        // Assert
        assertNotNull(result);
        verify(baseTableGenerator, atLeast(1)).generateTable(ArgumentMatchers.<TableData>argThat(tableData -> {
            if (tableData.getTitle().equals("Story Points by Issue Type")) {
                List<List<String>> rows = tableData.getRows();
                if (rows.size() < 4) {
                    return false;
                }
                List<String> firstRow = rows.get(0);
                List<String> secondRow = rows.get(1);
                List<String> thirdRow = rows.get(2);
                return firstRow.get(0).equals("Bug") && 
                       secondRow.get(0).equals("Story") && 
                       thirdRow.get(0).equals("Task");
            }
            return true;
        }));
    }

    @Test
    public void testGenerateRoleSpecificTables_withMissingDateClosed_handlesGracefully() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("Development", tickets);
        List<String> sortedRoles = Arrays.asList("Development");
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("Development", "Development");
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("otherField", "value");

        when(statisticsCalculator.categorizeTicketsByRole(tickets)).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getPriority()).thenReturn("High");
            lenient().when(mockTicket1.getTicketTitle()).thenReturn("Test Ticket");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket1.getKey()).thenReturn("TICKET-1");
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        lenient().when(statisticsCalculator.calculatePercentage(5.0, 5.0)).thenReturn(100.0);
        lenient().when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn("Generated Table");

        // Act & Assert
        assertThrows(Exception.class, () -> {
            generator.generateRoleSpecificTables(tickets);
        });
    }

    @Test
    public void testGenerateRoleSpecificTables_withNullFieldsAsJSON_handlesGracefully() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("Development", tickets);
        List<String> sortedRoles = Arrays.asList("Development");
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("Development", "Development");

        when(statisticsCalculator.categorizeTicketsByRole(tickets)).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getPriority()).thenReturn("High");
            lenient().when(mockTicket1.getTicketTitle()).thenReturn("Test Ticket");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getWeight()).thenReturn(5.0);
        when(mockTicket1.getKey()).thenReturn("TICKET-1");
        when(mockTicket1.getFieldsAsJSON()).thenReturn(null);
        lenient().when(statisticsCalculator.calculatePercentage(5.0, 5.0)).thenReturn(100.0);
        lenient().when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn("Generated Table");

        // Act & Assert
        assertThrows(Exception.class, () -> {
            generator.generateRoleSpecificTables(tickets);
        });
    }

    @Test
    public void testGenerateRoleSpecificTables_withZeroTotalPoints_handlesCorrectly() {
        // Arrange
        List<ITicket> tickets = Arrays.asList(mockTicket1);
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("Development", tickets);
        List<String> sortedRoles = Arrays.asList("Development");
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("Development", "Development");
        JSONObject fieldsJson = new JSONObject();
        fieldsJson.put("dateClosed", "2024-01-01");

        when(statisticsCalculator.categorizeTicketsByRole(tickets)).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        try {
            when(mockTicket1.getIssueType()).thenReturn("Task");
            when(mockTicket1.getPriority()).thenReturn("High");
            when(mockTicket1.getTicketTitle()).thenReturn("Test Ticket");
        } catch (IOException e) {
            fail("Mock setup should not throw IOException: " + e.getMessage());
        }
        when(mockTicket1.getWeight()).thenReturn(0.0);
        when(mockTicket1.getKey()).thenReturn("TICKET-1");
        when(mockTicket1.getFieldsAsJSON()).thenReturn(fieldsJson);
        when(statisticsCalculator.calculatePercentage(0.0, 0.0)).thenReturn(0.0);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn("Generated Table");

        // Act
        String result = generator.generateRoleSpecificTables(tickets);

        // Assert
        assertNotNull(result);
        verify(baseTableGenerator, atLeast(1)).generateTable(any(TableData.class));
    }

    @Test
    public void testGenerateRoleSpecificTables_withEmptyRoleTickets_generatesEmptyTables() {
        // Arrange
        List<ITicket> emptyTickets = new ArrayList<>();
        Map<String, List<ITicket>> ticketsByRole = new HashMap<>();
        ticketsByRole.put("Development", emptyTickets);
        List<String> sortedRoles = Arrays.asList("Development");
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("Development", "Development");

        when(statisticsCalculator.categorizeTicketsByRole(emptyTickets)).thenReturn(ticketsByRole);
        when(ticketSorter.sortRoles(ticketsByRole.keySet())).thenReturn(sortedRoles);
        when(config.getRoleDescriptions()).thenReturn(roleDescriptions);
        when(baseTableGenerator.generateTable(any(TableData.class))).thenReturn("Generated Table");

        // Act
        String result = generator.generateRoleSpecificTables(emptyTickets);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("### Development Work"));
        verify(baseTableGenerator, atLeast(1)).generateTable(ArgumentMatchers.<TableData>argThat(tableData -> {
            if (tableData.getTitle().equals("Story Points by Issue Type")) {
                List<List<String>> rows = tableData.getRows();
                if (rows.isEmpty()) {
                    return false;
                }
                List<String> totalsRow = rows.get(rows.size() - 1);
                return totalsRow.get(0).equals("**Total**") && totalsRow.get(1).equals("0.0");
            }
            return true;
        }));
    }
}
