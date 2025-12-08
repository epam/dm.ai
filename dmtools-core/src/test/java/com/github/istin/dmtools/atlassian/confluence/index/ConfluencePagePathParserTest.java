package com.github.istin.dmtools.atlassian.confluence.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConfluencePagePathParser.
 */
class ConfluencePagePathParserTest {

    // ==================== Simple Path Pattern Tests ====================

    @Test
    void testParseSimplePathDirectPageOnly() {
        // [SPACE]/pages/[pageId]/[PageName]
        ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse("MYSPACE/pages/1234512412/MyPage");

        assertEquals("MYSPACE", result.getSpaceKey());
        assertEquals("1234512412", result.getPageId());
        assertEquals("MyPage", result.getPageName());
        assertEquals(ConfluencePagePathParser.RetrievalDepth.PAGE_ONLY, result.getDepth());
        assertFalse(result.isSpaceWide());
    }

    @Test
    void testParseSimplePathWithImmediateChildren() {
        // [SPACE]/pages/[pageId]/[PageName]/*
        ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse("MYSPACE/pages/1234512412/MyPage/*");

        assertEquals("MYSPACE", result.getSpaceKey());
        assertEquals("1234512412", result.getPageId());
        assertEquals("MyPage", result.getPageName());
        assertEquals(ConfluencePagePathParser.RetrievalDepth.IMMEDIATE_CHILDREN, result.getDepth());
        assertFalse(result.isSpaceWide());
    }

    @Test
    void testParseSimplePathWithAllDescendants() {
        // [SPACE]/pages/[pageId]/[PageName]/**
        ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse("MYSPACE/pages/1234512412/MyPage/**");

        assertEquals("MYSPACE", result.getSpaceKey());
        assertEquals("1234512412", result.getPageId());
        assertEquals("MyPage", result.getPageName());
        assertEquals(ConfluencePagePathParser.RetrievalDepth.ALL_DESCENDANTS, result.getDepth());
        assertFalse(result.isSpaceWide());
    }

    @Test
    void testParseSpaceOnlyPattern() {
        // [SPACE]/**
        ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse("MYSPACE/**");

        assertEquals("MYSPACE", result.getSpaceKey());
        assertNull(result.getPageId());
        assertNull(result.getPageName());
        assertEquals(ConfluencePagePathParser.RetrievalDepth.ALL_SPACE_PAGES, result.getDepth());
        assertTrue(result.isSpaceWide());
    }

    @Test
    void testParseSimplePathWithoutPageName() {
        // [SPACE]/pages/[pageId]
        ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse("MYSPACE/pages/1234512412");

        assertEquals("MYSPACE", result.getSpaceKey());
        assertEquals("1234512412", result.getPageId());
        assertNull(result.getPageName());
        assertEquals(ConfluencePagePathParser.RetrievalDepth.PAGE_ONLY, result.getDepth());
    }

    // ==================== URL Pattern Tests ====================

    @Test
    void testParseUrlDirectPageOnly() {
        String url = "https://company.atlassian.net/wiki/spaces/MYSPACE/pages/1234512412/MyPage";
        ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse(url);

        assertEquals("MYSPACE", result.getSpaceKey());
        assertEquals("1234512412", result.getPageId());
        assertEquals("MyPage", result.getPageName());
        assertEquals(ConfluencePagePathParser.RetrievalDepth.PAGE_ONLY, result.getDepth());
    }

    @Test
    void testParseUrlWithImmediateChildren() {
        String url = "https://company.atlassian.net/wiki/spaces/MYSPACE/pages/1234512412/MyPage/*";
        ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse(url);

        assertEquals("MYSPACE", result.getSpaceKey());
        assertEquals("1234512412", result.getPageId());
        assertEquals("MyPage", result.getPageName());
        assertEquals(ConfluencePagePathParser.RetrievalDepth.IMMEDIATE_CHILDREN, result.getDepth());
    }

    @Test
    void testParseUrlWithAllDescendants() {
        String url = "https://company.atlassian.net/wiki/spaces/MYSPACE/pages/1234512412/MyPage/**";
        ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse(url);

        assertEquals("MYSPACE", result.getSpaceKey());
        assertEquals("1234512412", result.getPageId());
        assertEquals("MyPage", result.getPageName());
        assertEquals(ConfluencePagePathParser.RetrievalDepth.ALL_DESCENDANTS, result.getDepth());
    }

    @Test
    void testParseUrlSpaceWide() {
        String url = "https://company.atlassian.net/wiki/spaces/MYSPACE/**";
        ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse(url);

        assertEquals("MYSPACE", result.getSpaceKey());
        assertNull(result.getPageId());
        assertNull(result.getPageName());
        assertEquals(ConfluencePagePathParser.RetrievalDepth.ALL_SPACE_PAGES, result.getDepth());
        assertTrue(result.isSpaceWide());
    }

    @Test
    void testParseHttpUrl() {
        String url = "http://localhost:8080/wiki/spaces/TEST/pages/123/TestPage";
        ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse(url);

        assertEquals("TEST", result.getSpaceKey());
        assertEquals("123", result.getPageId());
        assertEquals("TestPage", result.getPageName());
    }

    // ==================== Special Characters in Page Names ====================

    @Test
    void testParsePageNameWithPlusSign() {
        // '+' in URL typically represents a space, but can also be literal
        String url = "https://company.atlassian.net/wiki/spaces/MYSPACE/pages/1235/My+Page+Name";
        ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse(url);

        assertEquals("MYSPACE", result.getSpaceKey());
        assertEquals("1235", result.getPageId());
        // '+' is decoded as space
        assertEquals("My Page Name", result.getPageName());
    }

    @Test
    void testParsePageNameWithEncodedPlusSign() {
        // '%2B' represents a literal '+'
        String url = "https://company.atlassian.net/wiki/spaces/MYSPACE/pages/1235/C%2B%2B+Programming";
        ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse(url);

        assertEquals("MYSPACE", result.getSpaceKey());
        assertEquals("1235", result.getPageId());
        assertEquals("C++ Programming", result.getPageName());
    }

    @Test
    void testParseSimplePathWithPlusSign() {
        String path = "MYSPACE/pages/1235/My+Page+Name";
        ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse(path);

        assertEquals("MYSPACE", result.getSpaceKey());
        assertEquals("1235", result.getPageId());
        assertEquals("My Page Name", result.getPageName());
    }

    @Test
    void testParsePageNameWithSpecialCharacters() {
        String url = "https://company.atlassian.net/wiki/spaces/MYSPACE/pages/1235/Page%20With%20Spaces%20%26%20Special";
        ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse(url);

        assertEquals("Page With Spaces & Special", result.getPageName());
    }

    // ==================== Various URL Formats ====================

    @Test
    void testParseRealWorldUrl1() {
        String url = "https://dmtools.atlassian.net/wiki/spaces/AINA/pages/6750209/Acceptance+Criteria";
        ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse(url);

        assertEquals("AINA", result.getSpaceKey());
        assertEquals("6750209", result.getPageId());
        assertEquals("Acceptance Criteria", result.getPageName());
    }

    @Test
    void testParseRealWorldUrlWithWildcard() {
        String url = "https://dmtools.atlassian.net/wiki/spaces/DEV/pages/12345/Development+Guide/**";
        ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse(url);

        assertEquals("DEV", result.getSpaceKey());
        assertEquals("12345", result.getPageId());
        assertEquals("Development Guide", result.getPageName());
        assertEquals(ConfluencePagePathParser.RetrievalDepth.ALL_DESCENDANTS, result.getDepth());
    }

    @Test
    void testParseUrlWithoutPageName() {
        String url = "https://company.atlassian.net/wiki/spaces/MYSPACE/pages/1234512412";
        ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse(url);

        assertEquals("MYSPACE", result.getSpaceKey());
        assertEquals("1234512412", result.getPageId());
        assertNull(result.getPageName());
    }

    // ==================== Invalid Pattern Tests ====================

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void testParseNullOrEmptyPattern(String pattern) {
        assertThrows(IllegalArgumentException.class, () -> ConfluencePagePathParser.parse(pattern));
    }

    @Test
    void testParseInvalidPattern() {
        assertThrows(IllegalArgumentException.class, () -> ConfluencePagePathParser.parse("invalid"));
    }

    @Test
    void testParseInvalidUrlMissingSpaces() {
        String url = "https://company.atlassian.net/wiki/pages/1234/MyPage";
        assertThrows(IllegalArgumentException.class, () -> ConfluencePagePathParser.parse(url));
    }

    @Test
    void testParseInvalidUrlMissingPages() {
        String url = "https://company.atlassian.net/wiki/spaces/MYSPACE/content/1234";
        assertThrows(IllegalArgumentException.class, () -> ConfluencePagePathParser.parse(url));
    }

    // ==================== Validation Tests ====================

    @ParameterizedTest
    @MethodSource("provideValidPatterns")
    void testIsValidPatternValid(String pattern) {
        assertTrue(ConfluencePagePathParser.isValidPattern(pattern),
                "Pattern should be valid: " + pattern);
    }

    @ParameterizedTest
    @MethodSource("provideInvalidPatterns")
    void testIsValidPatternInvalid(String pattern) {
        assertFalse(ConfluencePagePathParser.isValidPattern(pattern),
                "Pattern should be invalid: " + pattern);
    }

    private static Stream<String> provideValidPatterns() {
        return Stream.of(
                "MYSPACE/pages/1234/MyPage",
                "MYSPACE/pages/1234/MyPage/*",
                "MYSPACE/pages/1234/MyPage/**",
                "MYSPACE/**",
                "MYSPACE/pages/1234",
                "https://company.atlassian.net/wiki/spaces/MYSPACE/pages/1234/MyPage",
                "https://company.atlassian.net/wiki/spaces/MYSPACE/pages/1234/MyPage/*",
                "https://company.atlassian.net/wiki/spaces/MYSPACE/pages/1234/MyPage/**",
                "https://company.atlassian.net/wiki/spaces/MYSPACE/**",
                "http://localhost/wiki/spaces/TEST/pages/1/Page"
        );
    }

    private static Stream<String> provideInvalidPatterns() {
        return Stream.of(
                null,
                "",
                "   ",
                "invalid",
                "MYSPACE/",
                "MYSPACE/*",
                "/pages/1234/MyPage",
                "https://company.atlassian.net/wiki/pages/1234/MyPage"
        );
    }

    // ==================== Equality and HashCode Tests ====================

    @Test
    void testParsedPathEquality() {
        ConfluencePagePathParser.ParsedPath path1 = ConfluencePagePathParser.parse("MYSPACE/pages/1234/MyPage");
        ConfluencePagePathParser.ParsedPath path2 = ConfluencePagePathParser.parse("MYSPACE/pages/1234/MyPage");

        assertEquals(path1, path2);
        assertEquals(path1.hashCode(), path2.hashCode());
    }

    @Test
    void testParsedPathInequality() {
        ConfluencePagePathParser.ParsedPath path1 = ConfluencePagePathParser.parse("MYSPACE/pages/1234/MyPage");
        ConfluencePagePathParser.ParsedPath path2 = ConfluencePagePathParser.parse("MYSPACE/pages/1234/MyPage/*");

        assertNotEquals(path1, path2);
    }

    @Test
    void testParsedPathToString() {
        ConfluencePagePathParser.ParsedPath path = ConfluencePagePathParser.parse("MYSPACE/pages/1234/MyPage");
        String str = path.toString();

        assertTrue(str.contains("MYSPACE"));
        assertTrue(str.contains("1234"));
        assertTrue(str.contains("MyPage"));
        assertTrue(str.contains("PAGE_ONLY"));
    }

    // ==================== Edge Cases ====================

    @Test
    void testParseWithLeadingTrailingWhitespace() {
        ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse("  MYSPACE/pages/1234/MyPage  ");

        assertEquals("MYSPACE", result.getSpaceKey());
        assertEquals("1234", result.getPageId());
        assertEquals("MyPage", result.getPageName());
    }

    @Test
    void testParseVeryLongPageId() {
        String longPageId = "12345678901234567890";
        String pattern = "MYSPACE/pages/" + longPageId + "/MyPage";
        ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse(pattern);

        assertEquals(longPageId, result.getPageId());
    }

    @Test
    void testParseDifferentSpaceKeys() {
        String[] spaceKeys = {"A", "AB", "ABC", "MY_SPACE", "my-space", "MySpace123"};

        for (String spaceKey : spaceKeys) {
            String pattern = spaceKey + "/pages/1234/MyPage";
            ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse(pattern);
            assertEquals(spaceKey, result.getSpaceKey(),
                    "Failed for space key: " + spaceKey);
        }
    }

    @Test
    void testParseComplexPageName() {
        String url = "https://company.atlassian.net/wiki/spaces/MYSPACE/pages/1235/API%3A+REST+Services+%28v2%29";
        ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse(url);

        assertEquals("API: REST Services (v2)", result.getPageName());
    }

    // ==================== Test with different URL hosts ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "https://mycompany.atlassian.net/wiki/spaces/SPACE/pages/123/Page",
            "https://confluence.example.com/wiki/spaces/SPACE/pages/123/Page",
            "http://localhost:8080/wiki/spaces/SPACE/pages/123/Page",
            "https://192.168.1.1/wiki/spaces/SPACE/pages/123/Page"
    })
    void testParseVariousHosts(String url) {
        ConfluencePagePathParser.ParsedPath result = ConfluencePagePathParser.parse(url);

        assertEquals("SPACE", result.getSpaceKey());
        assertEquals("123", result.getPageId());
        assertEquals("Page", result.getPageName());
    }
}
