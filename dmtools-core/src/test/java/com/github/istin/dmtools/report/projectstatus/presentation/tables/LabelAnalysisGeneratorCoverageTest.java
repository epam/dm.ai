// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.projectstatus.presentation.tables;

import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.report.projectstatus.model.TableData;
import com.github.istin.dmtools.report.projectstatus.model.TimelinePeriod;
import com.github.istin.dmtools.report.projectstatus.presentation.TableGenerator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Coverage tests for the private timeline-analysis methods of
 * {@link LabelAnalysisGenerator} (generateLabelTimelineDynamics,
 * generateLabelCountTimeline, generateLabelPointsTimeline,
 * countTicketsByLabelInPeriod, calculatePointsByLabelInPeriod,
 * groupTicketsByPeriod, parseDate, formatDateToPeriod, filterTicketsByLabel).
 *
 * These methods are not reachable through the public API because the call in
 * generateLabelAnalysis is commented out, so they are exercised via reflection.
 */
class LabelAnalysisGeneratorCoverageTest {

    private TableGenerator tableGenerator;
    private TimelineTableGenerator timelineTableGenerator;
    private LabelAnalysisGenerator generator;

    @BeforeEach
    void setUp() {
        tableGenerator = mock(TableGenerator.class);
        timelineTableGenerator = mock(TimelineTableGenerator.class);
        when(tableGenerator.generateTable(any(TableData.class))).thenReturn("Generated Table");
        generator = new LabelAnalysisGenerator(tableGenerator, timelineTableGenerator);
    }

    // --- helpers -------------------------------------------------------------

    private ITicket mockTicket(double weight, String dateClosed, String... labels) {
        ITicket ticket = mock(ITicket.class);
        lenient().when(ticket.getWeight()).thenReturn(weight);
        JSONArray labelsJson = new JSONArray();
        for (String label : labels) {
            labelsJson.put(label);
        }
        lenient().when(ticket.getTicketLabels()).thenReturn(labelsJson);
        if (dateClosed != null) {
            JSONObject fields = new JSONObject();
            fields.put("dateClosed", dateClosed);
            lenient().when(ticket.getFieldsAsJSON()).thenReturn(fields);
        }
        return ticket;
    }

    private Method privateMethod(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = LabelAnalysisGenerator.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    @SuppressWarnings("unchecked")
    private String invokeTimelineDynamics(List<ITicket> tickets, TimelinePeriod period) throws Exception {
        Method method = privateMethod("generateLabelTimelineDynamics",
                List.class, java.util.Set.class, TimelinePeriod.class, List.class, List.class);
        return (String) method.invoke(generator, tickets,
                new java.util.HashSet<>(Arrays.asList("bug", "feature")), period,
                Collections.<ITicket>emptyList(), Collections.<ITicket>emptyList());
    }

    // --- generateLabelTimelineDynamics ----------------------------------------

    @Test
    void testTimelineDynamics_withTicketsInMultiplePeriods_generatesBothTimelines() throws Exception {
        ITicket t1 = mockTicket(5.0, "2024-01-10T12:00:00.000+0000", "bug");
        ITicket t2 = mockTicket(3.0, "2024-02-15T12:00:00.000+0000", "feature");
        ITicket t3 = mockTicket(2.0, "2024-01-20T08:30:00", "bug", "feature");

        String result = invokeTimelineDynamics(Arrays.asList(t1, t2, t3), TimelinePeriod.MONTH);

        assertNotNull(result);
        assertTrue(result.contains("## Label Timeline Dynamics (Monthly)"));
        verify(tableGenerator, times(2)).generateTable(any(TableData.class));
    }

    @Test
    void testTimelineDynamics_withEmptyTickets_generatesEmptyTimelines() throws Exception {
        String result = invokeTimelineDynamics(Collections.emptyList(), TimelinePeriod.MONTH);

        assertNotNull(result);
        assertTrue(result.contains("## Label Timeline Dynamics (Monthly)"));
        verify(tableGenerator, times(2)).generateTable(any(TableData.class));
    }

    @Test
    void testTimelineDynamics_withAllPeriodTypes_generatesTimelines() throws Exception {
        ITicket ticket = mockTicket(5.0, "2024-03-15T10:00:00", "bug");

        for (TimelinePeriod period : TimelinePeriod.values()) {
            String result = invokeTimelineDynamics(Collections.singletonList(ticket), period);
            assertNotNull(result);
            assertTrue(result.contains(period.getDescription()));
        }
    }

    // --- groupTicketsByPeriod --------------------------------------------------

    @Test
    void testGroupTicketsByPeriod_withUnparseableDate_skipsTicket() throws Exception {
        ITicket ticket = mockTicket(5.0, "not-a-date", "bug");

        Method method = privateMethod("groupTicketsByPeriod", List.class, TimelinePeriod.class);
        @SuppressWarnings("unchecked")
        Map<String, List<ITicket>> result = (Map<String, List<ITicket>>) method.invoke(
                generator, Collections.singletonList(ticket), TimelinePeriod.MONTH);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGroupTicketsByPeriod_withMissingDateClosed_skipsTicket() throws Exception {
        ITicket ticket = mockTicket(5.0, null, "bug");
        lenient().when(ticket.getFieldsAsJSON()).thenReturn(new JSONObject());

        Method method = privateMethod("groupTicketsByPeriod", List.class, TimelinePeriod.class);
        @SuppressWarnings("unchecked")
        Map<String, List<ITicket>> result = (Map<String, List<ITicket>>) method.invoke(
                generator, Collections.singletonList(ticket), TimelinePeriod.MONTH);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGroupTicketsByPeriod_withExceptionInFields_skipsTicket() throws Exception {
        ITicket ticket = mockTicket(5.0, null, "bug");
        when(ticket.getFieldsAsJSON()).thenThrow(new RuntimeException("fields error"));

        Method method = privateMethod("groupTicketsByPeriod", List.class, TimelinePeriod.class);
        @SuppressWarnings("unchecked")
        Map<String, List<ITicket>> result = (Map<String, List<ITicket>>) method.invoke(
                generator, Collections.singletonList(ticket), TimelinePeriod.MONTH);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGroupTicketsByPeriod_groupsByMonthAndSortsKeys() throws Exception {
        ITicket t1 = mockTicket(1.0, "2024-03-05T00:00:00", "bug");
        ITicket t2 = mockTicket(1.0, "2024-01-15T00:00:00", "bug");

        Method method = privateMethod("groupTicketsByPeriod", List.class, TimelinePeriod.class);
        @SuppressWarnings("unchecked")
        Map<String, List<ITicket>> result = (Map<String, List<ITicket>>) method.invoke(
                generator, Arrays.asList(t1, t2), TimelinePeriod.MONTH);

        assertEquals(2, result.size());
        assertEquals(Arrays.asList("2024-01", "2024-03"), List.copyOf(result.keySet()));
    }

    // --- parseDate ---------------------------------------------------------------

    @Test
    void testParseDate_withNullAndEmpty_returnsNull() throws Exception {
        Method method = privateMethod("parseDate", String.class);

        assertNull(method.invoke(generator, (Object) null));
        assertNull(method.invoke(generator, ""));
    }

    @Test
    void testParseDate_withSupportedFormats_parsesSuccessfully() throws Exception {
        Method method = privateMethod("parseDate", String.class);

        List<String> dates = Arrays.asList(
                "2024-01-15T10:30:00.123+0000",
                "2024-01-15T10:30:00.123",
                "2024-01-15T10:30:00+0000",
                "2024-01-15T10:30:00",
                "2024-01-15 10:30:00",
                "2024-01-15"
        );

        for (String date : dates) {
            Object parsed = method.invoke(generator, date);
            assertNotNull(parsed, "Expected to parse: " + date);
            assertInstanceOf(Date.class, parsed);
        }
    }

    @Test
    void testParseDate_withUnparseableDate_returnsNull() throws Exception {
        Method method = privateMethod("parseDate", String.class);

        assertNull(method.invoke(generator, "15/01/2024"));
    }

    // --- formatDateToPeriod --------------------------------------------------------

    @Test
    void testFormatDateToPeriod_withEachPeriod_formatsCorrectly() throws Exception {
        Method method = privateMethod("formatDateToPeriod", Date.class, TimelinePeriod.class);

        Calendar cal = Calendar.getInstance();
        cal.set(2024, Calendar.MARCH, 15, 12, 0, 0);
        Date date = cal.getTime();

        String week = (String) method.invoke(generator, date, TimelinePeriod.WEEK);
        assertTrue(week.matches("2024-W\\d{2}"), week);

        String twoWeeks = (String) method.invoke(generator, date, TimelinePeriod.TWO_WEEKS);
        assertTrue(twoWeeks.matches("2024-P\\d{2}"), twoWeeks);

        assertEquals("2024-03", method.invoke(generator, date, TimelinePeriod.MONTH));

        assertEquals("2024-Q1", method.invoke(generator, date, TimelinePeriod.QUARTER));
    }

    @Test
    void testFormatDateToPeriod_quarterBoundaries() throws Exception {
        Method method = privateMethod("formatDateToPeriod", Date.class, TimelinePeriod.class);

        String[] expectedQuarters = {"2024-Q1", "2024-Q2", "2024-Q3", "2024-Q4"};
        int[] months = {Calendar.JANUARY, Calendar.APRIL, Calendar.JULY, Calendar.OCTOBER};

        for (int i = 0; i < months.length; i++) {
            Calendar cal = Calendar.getInstance();
            cal.set(2024, months[i], 15, 12, 0, 0);
            assertEquals(expectedQuarters[i],
                    method.invoke(generator, cal.getTime(), TimelinePeriod.QUARTER));
        }
    }

    // --- filterTicketsByLabel ---------------------------------------------------------

    @Test
    void testFilterTicketsByLabel_filtersCorrectly() throws Exception {
        ITicket t1 = mockTicket(1.0, null, "bug", "critical");
        ITicket t2 = mockTicket(1.0, null, "feature");
        ITicket t3 = mockTicket(1.0, null);

        Method method = privateMethod("filterTicketsByLabel", List.class, String.class);
        @SuppressWarnings("unchecked")
        List<ITicket> result = (List<ITicket>) method.invoke(
                generator, Arrays.asList(t1, t2, t3), "bug");

        assertEquals(1, result.size());
        assertSame(t1, result.get(0));
    }

    @Test
    void testFilterTicketsByLabel_withNullLabelsAndException_returnsFalse() throws Exception {
        ITicket ticketWithNullLabels = mock(ITicket.class);
        when(ticketWithNullLabels.getTicketLabels()).thenReturn(null);

        ITicket ticketThrowing = mock(ITicket.class);
        when(ticketThrowing.getTicketLabels()).thenThrow(new RuntimeException("labels error"));

        Method method = privateMethod("filterTicketsByLabel", List.class, String.class);
        @SuppressWarnings("unchecked")
        List<ITicket> result = (List<ITicket>) method.invoke(
                generator, Arrays.asList(ticketWithNullLabels, ticketThrowing), "bug");

        assertTrue(result.isEmpty());
    }

    // --- countTicketsByLabelInPeriod / calculatePointsByLabelInPeriod ----------------------

    @Test
    void testCountTicketsByLabelInPeriod_countsIntersection() throws Exception {
        ITicket t1 = mockTicket(1.0, null, "bug");
        ITicket t2 = mockTicket(1.0, null, "bug");
        ITicket t3 = mockTicket(1.0, null, "feature");

        Map<String, List<ITicket>> ticketsByLabel = Map.of(
                "bug", Arrays.asList(t1, t2),
                "feature", Collections.singletonList(t3)
        );

        Method method = privateMethod("countTicketsByLabelInPeriod", List.class, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Integer> result = (Map<String, Integer>) method.invoke(
                generator, Arrays.asList(t1, t3), ticketsByLabel);

        assertEquals(1, result.get("bug"));
        assertEquals(1, result.get("feature"));
    }

    @Test
    void testCalculatePointsByLabelInPeriod_sumsIntersection() throws Exception {
        ITicket t1 = mockTicket(5.0, null, "bug");
        ITicket t2 = mockTicket(3.0, null, "bug");
        ITicket t3 = mockTicket(2.0, null, "feature");

        Map<String, List<ITicket>> ticketsByLabel = Map.of(
                "bug", Arrays.asList(t1, t2),
                "feature", Collections.singletonList(t3)
        );

        Method method = privateMethod("calculatePointsByLabelInPeriod", List.class, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Double> result = (Map<String, Double>) method.invoke(
                generator, Arrays.asList(t1, t3), ticketsByLabel);

        assertEquals(5.0, result.get("bug"));
        assertEquals(2.0, result.get("feature"));
    }

    // --- timeline tables include special categories and totals --------------------------

    @Test
    void testTimelineDynamics_withUnlabeledAndOtherTickets_includesSpecialCategoryColumns() throws Exception {
        ITicket bugTicket = mockTicket(5.0, "2024-01-10T00:00:00", "bug");
        ITicket unlabeledTicket = mockTicket(2.0, "2024-01-12T00:00:00");
        ITicket otherTicket = mockTicket(4.0, "2024-01-15T00:00:00", "enhancement");

        List<ITicket> tickets = Arrays.asList(bugTicket, unlabeledTicket, otherTicket);

        Method method = privateMethod("generateLabelTimelineDynamics",
                List.class, java.util.Set.class, TimelinePeriod.class, List.class, List.class);
        String result = (String) method.invoke(generator, tickets,
                new java.util.HashSet<>(Collections.singletonList("bug")), TimelinePeriod.MONTH,
                Collections.singletonList(unlabeledTicket), Collections.singletonList(otherTicket));

        assertNotNull(result);
        verify(tableGenerator, times(2)).generateTable(any(TableData.class));
    }
}
