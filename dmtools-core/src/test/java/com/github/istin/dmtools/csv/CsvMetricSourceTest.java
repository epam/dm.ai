// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.csv;

import com.github.istin.dmtools.report.model.KeyTime;
import com.github.istin.dmtools.team.IEmployees;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Calendar;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CsvMetricSourceTest {

    @Mock
    private IEmployees employees;

    @BeforeEach
    void setUp() {
        when(employees.contains(anyString())).thenReturn(true);
        when(employees.transformName(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void testPerformSourceCollection_totalTokens() throws Exception {
        CsvMetricSource source = new CsvMetricSource(
            employees, "/Test_Csv.csv", "Name", "Date", "Total Tokens", 1.0, null, null
        );

        List<KeyTime> result = source.performSourceCollection(true, "Tokens");

        // Rows with valid date AND valid Total Tokens: rows 0-3, 6 (5 entries)
        // Row 4 = invalid date (skipped), Row 5 = empty Total Tokens (skipped)
        assertEquals(5, result.size());

        // First row: Alice, 79997 tokens
        assertEquals("Alice", result.get(0).getWho());
        assertEquals(79997.0, result.get(0).getWeight(), 0.01);

        // Second row: Bob, 46664 tokens
        assertEquals("Bob", result.get(1).getWho());
        assertEquals(46664.0, result.get(1).getWeight(), 0.01);

        // Third row: Alice, 8642877 tokens
        assertEquals("Alice", result.get(2).getWho());
        assertEquals(8642877.0, result.get(2).getWeight(), 0.01);

        // Fourth row: Bob, 397279 tokens
        assertEquals("Bob", result.get(3).getWho());
        assertEquals(397279.0, result.get(3).getWeight(), 0.01);

        // Fifth row: Charlie, 50000 tokens (row 6 in CSV, valid Total Tokens but "bad" Cost)
        assertEquals("Charlie", result.get(4).getWho());
        assertEquals(50000.0, result.get(4).getWeight(), 0.01);
    }

    @Test
    void testPerformSourceCollection_cost() throws Exception {
        CsvMetricSource source = new CsvMetricSource(
            employees, "/Test_Csv.csv", "Name", "Date", "Cost", 1.0, null, null
        );

        List<KeyTime> result = source.performSourceCollection(true, "Cost");

        // Row 6 has "bad" cost -> skipped, Row 4 invalid date -> skipped, Row 5 has "0.00" -> valid
        assertEquals(5, result.size());

        assertEquals(0.04, result.get(0).getWeight(), 0.001);
        assertEquals(0.02, result.get(1).getWeight(), 0.001);
        assertEquals(5.22, result.get(2).getWeight(), 0.001);
        assertEquals(0.19, result.get(3).getWeight(), 0.001);
        assertEquals(0.00, result.get(4).getWeight(), 0.001);
    }

    @Test
    void testPerformSourceCollection_defaultWho() throws Exception {
        // No whoColumn, use defaultWho
        CsvMetricSource source = new CsvMetricSource(
            employees, "/Test_Csv.csv", null, "Date", "Total Tokens", 1.0, "Default Person", null
        );

        List<KeyTime> result = source.performSourceCollection(true, "Tokens");

        assertFalse(result.isEmpty());
        for (KeyTime kt : result) {
            assertEquals("Default Person", kt.getWho());
        }
    }

    @Test
    void testPerformSourceCollection_notPersonalized() throws Exception {
        CsvMetricSource source = new CsvMetricSource(
            employees, "/Test_Csv.csv", "Name", "Date", "Total Tokens", 1.0, null, null
        );

        List<KeyTime> result = source.performSourceCollection(false, "MetricLabel");

        assertFalse(result.isEmpty());
        for (KeyTime kt : result) {
            assertEquals("MetricLabel", kt.getWho());
        }
    }

    @Test
    void testPerformSourceCollection_weightMultiplier() throws Exception {
        CsvMetricSource source = new CsvMetricSource(
            employees, "/Test_Csv.csv", "Name", "Date", "Cost", 100.0, null, null
        );

        List<KeyTime> result = source.performSourceCollection(true, "Cost");

        // First row cost = 0.04 * 100 = 4.0
        assertEquals(4.0, result.get(0).getWeight(), 0.01);
    }

    @Test
    void testPerformSourceCollection_datesParsedCorrectly() throws Exception {
        CsvMetricSource source = new CsvMetricSource(
            employees, "/Test_Csv.csv", "Name", "Date", "Total Tokens", 1.0, null, null
        );

        List<KeyTime> result = source.performSourceCollection(true, "Tokens");

        // First entry: 2026-02-07
        Calendar cal = result.get(0).getWhen();
        assertEquals(2026, cal.get(Calendar.YEAR));
        assertEquals(Calendar.FEBRUARY, cal.get(Calendar.MONTH));
        assertEquals(7, cal.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    void testPerformSourceCollection_summaryIncludesOtherColumns() throws Exception {
        CsvMetricSource source = new CsvMetricSource(
            employees, "/Test_Csv.csv", "Name", "Date", "Total Tokens", 1.0, null, null
        );

        List<KeyTime> result = source.performSourceCollection(true, "Tokens");

        // Summary should include Kind, Model, Cost (not Date, Name, Total Tokens)
        String summary = result.get(0).getSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Kind:"));
        assertTrue(summary.contains("Model:"));
    }

    @Test
    void testPerformSourceCollection_fileNotFound() throws Exception {
        CsvMetricSource source = new CsvMetricSource(
            employees, "/nonexistent.csv", null, "Date", "Value", 1.0, null, null
        );

        List<KeyTime> result = source.performSourceCollection(true, "Test");

        assertTrue(result.isEmpty());
    }

    @Test
    void testParseCsvLine_quotedValues() {
        String[] result = CsvMetricSource.parseCsvLine("\"hello\",\"world\",\"123\"");
        assertEquals(3, result.length);
        assertEquals("hello", result[0]);
        assertEquals("world", result[1]);
        assertEquals("123", result[2]);
    }

    @Test
    void testParseCsvLine_commaInQuotes() {
        String[] result = CsvMetricSource.parseCsvLine("\"hello, world\",\"123\"");
        assertEquals(2, result.length);
        assertEquals("hello, world", result[0]);
        assertEquals("123", result[1]);
    }

    @Test
    void testParseNumber_quotedNumber() {
        assertEquals(79997.0, CsvMetricSource.parseNumber("79997"), 0.01);
        assertEquals(0.04, CsvMetricSource.parseNumber("0.04"), 0.001);
        assertNull(CsvMetricSource.parseNumber("NaN"));
        assertNull(CsvMetricSource.parseNumber("N/A"));
        assertNull(CsvMetricSource.parseNumber("bad"));
        assertNull(CsvMetricSource.parseNumber(""));
        assertNull(CsvMetricSource.parseNumber(null));
    }

    @Test
    void testParseDate_isoFormat() {
        Calendar cal = CsvMetricSource.parseDate("2026-02-07T13:29:37.369Z");
        assertNotNull(cal);
        assertEquals(2026, cal.get(Calendar.YEAR));
        assertEquals(Calendar.FEBRUARY, cal.get(Calendar.MONTH));
        assertEquals(7, cal.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    void testParseDate_simpleFormat() {
        Calendar cal = CsvMetricSource.parseDate("2026-02-07");
        assertNotNull(cal);
        assertEquals(2026, cal.get(Calendar.YEAR));
    }

    @Test
    void testParseDate_invalid() {
        assertNull(CsvMetricSource.parseDate("not-a-date"));
        assertNull(CsvMetricSource.parseDate(""));
        assertNull(CsvMetricSource.parseDate(null));
    }
}
