package com.github.istin.dmtools.common.kb.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VTTUtils - WebVTT format transformation utility.
 */
public class VTTUtilsTest {

    @Test
    public void testTransformVTT_BasicFormat() {
        String vttContent = """
                WEBVTT
                
                f7cbbd91-4f2a-493b-8fa8-02520ca1323e/277-0
                00:00:17.013 --> 00:00:17.213
                <v John Smith>Hello everyone.</v>
                
                f7cbbd91-4f2a-493b-8fa8-02520ca1323e/326-0
                00:00:20.173 --> 00:00:25.306
                <v Jane Doe>Today,
                please feel free to ask any questions.</v>
                """;
        
        String result = VTTUtils.transformVTT(vttContent);
        
        assertNotNull(result);
        assertTrue(result.contains("[00:00:17] John Smith: Hello everyone."));
        assertTrue(result.contains("[00:00:20] Jane Doe: Today, please feel free to ask any questions."));
        assertFalse(result.contains("WEBVTT"));
        assertFalse(result.contains("f7cbbd91"));
    }

    @Test
    public void testTransformVTT_WithDate() {
        String vttContent = """
                WEBVTT
                
                abc123/1-0
                00:01:00.000 --> 00:01:05.000
                <v Alice>This is a test.</v>
                """;
        
        String result = VTTUtils.transformVTT(vttContent, "2025-10-24");
        
        assertNotNull(result);
        assertTrue(result.startsWith("Date: 2025-10-24"));
        assertTrue(result.contains("[00:01:00] Alice: This is a test."));
    }

    @Test
    public void testTransformVTT_ConsecutiveSameSpeaker() {
        String vttContent = """
                WEBVTT
                
                id1/1-0
                00:00:10.000 --> 00:00:12.000
                <v Bob>First sentence.</v>
                
                id1/2-0
                00:00:12.500 --> 00:00:15.000
                <v Bob>Second sentence.</v>
                
                id1/3-0
                00:00:16.000 --> 00:00:18.000
                <v Bob>Third sentence.</v>
                """;
        
        String result = VTTUtils.transformVTT(vttContent);
        
        assertNotNull(result);
        // Should group consecutive same speaker
        assertTrue(result.contains("[00:00:10] Bob: First sentence. Second sentence. Third sentence."));
        // Should only appear once, not three times
        assertEquals(1, result.split("\\[00:00:10\\] Bob:").length - 1);
    }

    @Test
    public void testTransformVTT_MultipleSpeakers() {
        String vttContent = """
                WEBVTT
                
                id/1-0
                00:00:05.000 --> 00:00:07.000
                <v Speaker A>Question from A.</v>
                
                id/2-0
                00:00:08.000 --> 00:00:10.000
                <v Speaker B>Answer from B.</v>
                
                id/3-0
                00:00:11.000 --> 00:00:13.000
                <v Speaker A>Follow-up from A.</v>
                """;
        
        String result = VTTUtils.transformVTT(vttContent);
        
        assertNotNull(result);
        assertTrue(result.contains("[00:00:05] Speaker A: Question from A."));
        assertTrue(result.contains("[00:00:08] Speaker B: Answer from B."));
        assertTrue(result.contains("[00:00:11] Speaker A: Follow-up from A."));
    }

    @Test
    public void testTransformVTT_MultilineText() {
        String vttContent = """
                WEBVTT
                
                id/1-0
                00:00:30.000 --> 00:00:35.000
                <v Presenter>This is a longer statement
                that spans multiple lines
                in the VTT file.</v>
                """;
        
        String result = VTTUtils.transformVTT(vttContent);
        
        assertNotNull(result);
        assertTrue(result.contains("[00:00:30] Presenter: This is a longer statement that spans multiple lines in the VTT file."));
    }

    @Test
    public void testTransformVTT_EmptyContent() {
        String result = VTTUtils.transformVTT("");
        
        assertNotNull(result);
        assertEquals("", result);
    }

    @Test
    public void testTransformVTT_NullContent() {
        String result = VTTUtils.transformVTT(null);
        
        assertNotNull(result);
        assertEquals("", result);
    }

    @Test
    public void testTransformVTT_NoValidEntries() {
        String vttContent = """
                WEBVTT
                
                This is just random text
                without proper VTT format
                """;
        
        String result = VTTUtils.transformVTT(vttContent);
        
        assertNotNull(result);
        // Should return original content if parsing fails
        assertTrue(result.contains("This is just random text"));
    }

    @Test
    public void testTransformVTT_TimestampFormatting() {
        String vttContent = """
                WEBVTT
                
                id/1-0
                01:23:45.678 --> 01:23:50.123
                <v TimeTest>Testing timestamp format.</v>
                """;
        
        String result = VTTUtils.transformVTT(vttContent);
        
        assertNotNull(result);
        // Should format timestamp without milliseconds
        assertTrue(result.contains("[01:23:45] TimeTest: Testing timestamp format."));
        assertFalse(result.contains(".678"));
    }

    @Test
    public void testTransformVTT_SpecialCharactersInSpeaker() {
        String vttContent = """
                WEBVTT
                
                id/1-0
                00:00:10.000 --> 00:00:12.000
                <v Dr. Smith-Jones>Medical advice here.</v>
                
                id/2-0
                00:00:15.000 --> 00:00:17.000
                <v O'Brien>Response with apostrophe.</v>
                """;
        
        String result = VTTUtils.transformVTT(vttContent);
        
        assertNotNull(result);
        assertTrue(result.contains("Dr. Smith-Jones: Medical advice here."));
        assertTrue(result.contains("O'Brien: Response with apostrophe."));
    }

    @Test
    public void testTransformVTT_WithDateAndMultipleSpeakers() {
        String vttContent = """
                WEBVTT
                
                id/1-0
                00:00:01.000 --> 00:00:03.000
                <v Host>Welcome to the meeting.</v>
                
                id/2-0
                00:00:04.000 --> 00:00:06.000
                <v Guest>Thank you for having me.</v>
                """;
        
        String result = VTTUtils.transformVTT(vttContent, "2025-12-31");
        
        assertNotNull(result);
        assertTrue(result.startsWith("Date: 2025-12-31"));
        assertTrue(result.contains("[00:00:01] Host: Welcome to the meeting."));
        assertTrue(result.contains("[00:00:04] Guest: Thank you for having me."));
    }

    @Test
    public void testIsVTTFormat_WithWebVTTHeader() {
        String vttContent = """
                WEBVTT
                
                id/1-0
                00:00:10.000 --> 00:00:12.000
                <v Speaker>Text here.</v>
                """;
        
        assertTrue(VTTUtils.isVTTFormat(vttContent));
    }

    @Test
    public void testIsVTTFormat_WithTimestamp() {
        String vttContent = "00:00:10.000 --> 00:00:12.000\n<v Speaker>Text.</v>";
        
        assertTrue(VTTUtils.isVTTFormat(vttContent));
    }

    @Test
    public void testIsVTTFormat_WithSpeakerTag() {
        String vttContent = "<v John Doe>Some content here.</v>";
        
        assertTrue(VTTUtils.isVTTFormat(vttContent));
    }

    @Test
    public void testIsVTTFormat_NotVTT() {
        String plainText = "This is just regular text without VTT formatting.";
        
        assertFalse(VTTUtils.isVTTFormat(plainText));
    }

    @Test
    public void testIsVTTFormat_EmptyString() {
        assertFalse(VTTUtils.isVTTFormat(""));
    }

    @Test
    public void testIsVTTFormat_NullString() {
        assertFalse(VTTUtils.isVTTFormat(null));
    }

    @Test
    public void testTransformVTT_RealWorldExample() {
        String vttContent = """
                WEBVTT
                
                f7cbbd91-4f2a-493b-8fa8-02520ca1323e/277-0
                00:00:17.013 --> 00:00:17.213
                <v Uladzimir Klyshevich>5.</v>
                
                f7cbbd91-4f2a-493b-8fa8-02520ca1323e/326-0
                00:00:20.173 --> 00:00:25.306
                <v Ira Skrypnik>Today,
                please feel free to ask any questions in</v>
                
                f7cbbd91-4f2a-493b-8fa8-02520ca1323e/326-1
                00:00:25.306 --> 00:00:27.380
                <v Ira Skrypnik>the chat anytime.</v>
                """;
        
        String result = VTTUtils.transformVTT(vttContent, "2025-10-24");
        
        assertNotNull(result);
        assertTrue(result.startsWith("Date: 2025-10-24"));
        assertTrue(result.contains("[00:00:17] Uladzimir Klyshevich: 5."));
        assertTrue(result.contains("[00:00:20] Ira Skrypnik: Today, please feel free to ask any questions in the chat anytime."));
        
        // Should group consecutive Ira Skrypnik entries
        assertEquals(1, result.split("\\[00:00:20\\] Ira Skrypnik:").length - 1);
    }

    @Test
    public void testTransformVTT_VeryShortTimestamps() {
        String vttContent = """
                WEBVTT
                
                id/1-0
                00:00:01.100 --> 00:00:01.200
                <v Fast>One.</v>
                
                id/2-0
                00:00:01.300 --> 00:00:01.400
                <v Fast>Two.</v>
                
                id/3-0
                00:00:01.500 --> 00:00:01.600
                <v Fast>Three.</v>
                """;
        
        String result = VTTUtils.transformVTT(vttContent);
        
        assertNotNull(result);
        // Should group all Fast speaker entries
        assertTrue(result.contains("[00:00:01] Fast: One. Two. Three."));
    }
}

