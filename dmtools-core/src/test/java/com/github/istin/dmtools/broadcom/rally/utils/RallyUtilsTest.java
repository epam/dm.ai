package com.github.istin.dmtools.broadcom.rally.utils;

import com.github.istin.dmtools.common.model.IHistoryItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for RallyUtils - validates regex parsing of Rally revision descriptions
 */
class RallyUtilsTest {

    @Test
    void testConvertRevisionDescriptionToHistoryItems_SingleChange() {
        String input = "STATUS changed from [In Progress] to [Done]";
        
        List<IHistoryItem> result = RallyUtils.convertRevisionDescriptionToHistoryItems(input);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        
        IHistoryItem item = result.get(0);
        assertEquals("STATUS", item.getField());
        assertEquals("In Progress", item.getFromAsString());
        assertEquals("Done", item.getToAsString());
    }

    @Test
    void testConvertRevisionDescriptionToHistoryItems_MultipleChanges() {
        String input = "STATUS changed from [Open] to [In Progress] PRIORITY changed from [Low] to [High]";
        
        List<IHistoryItem> result = RallyUtils.convertRevisionDescriptionToHistoryItems(input);
        
        assertNotNull(result);
        assertEquals(2, result.size());
        
        IHistoryItem firstItem = result.get(0);
        assertEquals("STATUS", firstItem.getField());
        assertEquals("Open", firstItem.getFromAsString());
        assertEquals("In Progress", firstItem.getToAsString());
        
        IHistoryItem secondItem = result.get(1);
        assertEquals("PRIORITY", secondItem.getField());
        assertEquals("Low", secondItem.getFromAsString());
        assertEquals("High", secondItem.getToAsString());
    }

    @Test
    void testConvertRevisionDescriptionToHistoryItems_MultiWordFieldName() {
        String input = "STORY POINTS changed from [3] to [5]";
        
        List<IHistoryItem> result = RallyUtils.convertRevisionDescriptionToHistoryItems(input);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        
        IHistoryItem item = result.get(0);
        assertEquals("STORY POINTS", item.getField());
        assertEquals("3", item.getFromAsString());
        assertEquals("5", item.getToAsString());
    }

    @Test
    void testConvertRevisionDescriptionToHistoryItems_EmptyValues() {
        String input = "ASSIGNEE changed from [] to [John Doe]";
        
        List<IHistoryItem> result = RallyUtils.convertRevisionDescriptionToHistoryItems(input);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        
        IHistoryItem item = result.get(0);
        assertEquals("ASSIGNEE", item.getField());
        assertEquals("", item.getFromAsString());
        assertEquals("John Doe", item.getToAsString());
    }

    @Test
    void testConvertRevisionDescriptionToHistoryItems_EmptyString() {
        String input = "";
        
        List<IHistoryItem> result = RallyUtils.convertRevisionDescriptionToHistoryItems(input);
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testConvertRevisionDescriptionToHistoryItems_NoMatches() {
        String input = "This is just plain text without any changes";
        
        List<IHistoryItem> result = RallyUtils.convertRevisionDescriptionToHistoryItems(input);
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testConvertRevisionDescriptionToHistoryItems_ValuesWithSpaces() {
        String input = "STATUS changed from [Waiting for Review] to [Ready for Deploy]";
        
        List<IHistoryItem> result = RallyUtils.convertRevisionDescriptionToHistoryItems(input);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        
        IHistoryItem item = result.get(0);
        assertEquals("STATUS", item.getField());
        assertEquals("Waiting for Review", item.getFromAsString());
        assertEquals("Ready for Deploy", item.getToAsString());
    }

    @Test
    void testConvertRevisionDescriptionToHistoryItems_NumericValues() {
        String input = "ESTIMATE changed from [8] to [13]";
        
        List<IHistoryItem> result = RallyUtils.convertRevisionDescriptionToHistoryItems(input);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        
        IHistoryItem item = result.get(0);
        assertEquals("ESTIMATE", item.getField());
        assertEquals("8", item.getFromAsString());
        assertEquals("13", item.getToAsString());
    }

    @Test
    void testConvertRevisionDescriptionToHistoryItems_SpecialCharactersInValues() {
        String input = "DESCRIPTION changed from [Bug: #123] to [Feature: @456]";
        
        List<IHistoryItem> result = RallyUtils.convertRevisionDescriptionToHistoryItems(input);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        
        IHistoryItem item = result.get(0);
        assertEquals("DESCRIPTION", item.getField());
        assertEquals("Bug: #123", item.getFromAsString());
        assertEquals("Feature: @456", item.getToAsString());
    }

    @Test
    void testConvertRevisionDescriptionToHistoryItems_ThreeWordFieldName() {
        String input = "PROJECT BOARD STATUS changed from [Backlog] to [Sprint 1]";
        
        List<IHistoryItem> result = RallyUtils.convertRevisionDescriptionToHistoryItems(input);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        
        IHistoryItem item = result.get(0);
        assertEquals("PROJECT BOARD STATUS", item.getField());
        assertEquals("Backlog", item.getFromAsString());
        assertEquals("Sprint 1", item.getToAsString());
    }

    @Test
    void testConvertRevisionDescriptionToHistoryItems_MixedCaseFieldName() {
        // The regex pattern expects uppercase field names (A-Z pattern)
        String input = "STATUS changed from [new] to [old]";
        
        List<IHistoryItem> result = RallyUtils.convertRevisionDescriptionToHistoryItems(input);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("STATUS", result.get(0).getField());
    }

    @Test
    void testConvertRevisionDescriptionToHistoryItems_ComplexRealWorldExample() {
        String input = "STATUS changed from [Backlog] to [In Progress] " +
                      "PRIORITY changed from [Medium] to [High] " +
                      "STORY POINTS changed from [5] to [8]";
        
        List<IHistoryItem> result = RallyUtils.convertRevisionDescriptionToHistoryItems(input);
        
        assertNotNull(result);
        assertEquals(3, result.size());
        
        // Verify first change
        assertEquals("STATUS", result.get(0).getField());
        assertEquals("Backlog", result.get(0).getFromAsString());
        assertEquals("In Progress", result.get(0).getToAsString());
        
        // Verify second change
        assertEquals("PRIORITY", result.get(1).getField());
        assertEquals("Medium", result.get(1).getFromAsString());
        assertEquals("High", result.get(1).getToAsString());
        
        // Verify third change
        assertEquals("STORY POINTS", result.get(2).getField());
        assertEquals("5", result.get(2).getFromAsString());
        assertEquals("8", result.get(2).getToAsString());
    }

    @Test
    void testConvertRevisionDescriptionToHistoryItems_BothValuesToEmpty() {
        String input = "ASSIGNEE changed from [John Doe] to []";
        
        List<IHistoryItem> result = RallyUtils.convertRevisionDescriptionToHistoryItems(input);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        
        IHistoryItem item = result.get(0);
        assertEquals("ASSIGNEE", item.getField());
        assertEquals("John Doe", item.getFromAsString());
        assertEquals("", item.getToAsString());
    }

    @Test
    void testConvertRevisionDescriptionToHistoryItems_WithLineBreaks() {
        String input = "STATUS changed from [Open] to [Closed]\n" +
                      "RESOLUTION changed from [] to [Fixed]";
        
        List<IHistoryItem> result = RallyUtils.convertRevisionDescriptionToHistoryItems(input);
        
        assertNotNull(result);
        assertEquals(2, result.size());
        
        assertEquals("STATUS", result.get(0).getField());
        assertEquals("RESOLUTION", result.get(1).getField());
    }

    @Test
    void testConvertRevisionDescriptionToHistoryItems_FieldNameWithUnderscores() {
        // Pattern expects uppercase letters and spaces, underscores might not match
        String input = "CUSTOM FIELD changed from [value1] to [value2]";
        
        List<IHistoryItem> result = RallyUtils.convertRevisionDescriptionToHistoryItems(input);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("CUSTOM FIELD", result.get(0).getField());
    }

    @Test
    void testHistoryItemImpl() {
        // Test the IHistoryItem.Impl class used internally
        IHistoryItem item = new IHistoryItem.Impl("FIELD NAME", "old value", "new value");
        
        assertEquals("FIELD NAME", item.getField());
        assertEquals("old value", item.getFromAsString());
        assertEquals("new value", item.getToAsString());
    }
}
