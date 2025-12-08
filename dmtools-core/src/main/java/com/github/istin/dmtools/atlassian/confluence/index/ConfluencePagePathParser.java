package com.github.istin.dmtools.atlassian.confluence.index;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parser for Confluence page path patterns.
 * Supports multiple path/URL formats for retrieving page content:
 * <ul>
 *   <li>{@code [SPACE]/pages/[pageId]/[PageName]} - direct page only</li>
 *   <li>{@code [SPACE]/pages/[pageId]/[PageName]/*} - direct page + immediate children</li>
 *   <li>{@code [SPACE]/pages/[pageId]/[PageName]/**} - direct page + all descendants</li>
 *   <li>{@code [SPACE]/**} - all pages in space</li>
 *   <li>Full URL: {@code https://company.atlassian.net/wiki/spaces/[space]/pages/[pageId]/[page]}</li>
 * </ul>
 * Page names can contain '+' characters which are URL-decoded.
 */
public class ConfluencePagePathParser {

    /**
     * Pattern for full Confluence URLs.
     * Matches: https://company.atlassian.net/wiki/spaces/[space]/pages/[pageId]/[pageName]
     */
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[^/]+/wiki/spaces/([^/]+)/pages/(\\d+)(?:/([^/*]+))?(/\\*{1,2})?$"
    );

    /**
     * Pattern for simple path format: [SPACE]/pages/[pageId]/[pageName]
     */
    private static final Pattern SIMPLE_PATH_PATTERN = Pattern.compile(
            "^([^/]+)/pages/(\\d+)(?:/([^/*]+))?(/\\*{1,2})?$"
    );

    /**
     * Pattern for space-only: [SPACE]/**
     */
    private static final Pattern SPACE_ONLY_PATTERN = Pattern.compile(
            "^([^/]+)/\\*{2}$"
    );

    /**
     * Represents the depth of retrieval for child pages.
     */
    public enum RetrievalDepth {
        /** Only the specified page */
        PAGE_ONLY,
        /** Page and immediate children */
        IMMEDIATE_CHILDREN,
        /** Page and all descendants recursively */
        ALL_DESCENDANTS,
        /** All pages in a space */
        ALL_SPACE_PAGES
    }

    /**
     * Parsed result from a page path pattern.
     */
    public static class ParsedPath {
        private final String spaceKey;
        private final String pageId;
        private final String pageName;
        private final RetrievalDepth depth;

        public ParsedPath(String spaceKey, String pageId, String pageName, RetrievalDepth depth) {
            this.spaceKey = spaceKey;
            this.pageId = pageId;
            this.pageName = pageName;
            this.depth = depth;
        }

        public String getSpaceKey() {
            return spaceKey;
        }

        public String getPageId() {
            return pageId;
        }

        public String getPageName() {
            return pageName;
        }

        public RetrievalDepth getDepth() {
            return depth;
        }

        /**
         * @return true if this represents a space-wide retrieval (no specific page)
         */
        public boolean isSpaceWide() {
            return depth == RetrievalDepth.ALL_SPACE_PAGES && pageId == null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ParsedPath that = (ParsedPath) o;
            return Objects.equals(spaceKey, that.spaceKey) &&
                    Objects.equals(pageId, that.pageId) &&
                    Objects.equals(pageName, that.pageName) &&
                    depth == that.depth;
        }

        @Override
        public int hashCode() {
            return Objects.hash(spaceKey, pageId, pageName, depth);
        }

        @Override
        public String toString() {
            return "ParsedPath{" +
                    "spaceKey='" + spaceKey + '\'' +
                    ", pageId='" + pageId + '\'' +
                    ", pageName='" + pageName + '\'' +
                    ", depth=" + depth +
                    '}';
        }
    }

    /**
     * Parses a path pattern or URL and returns the parsed result.
     *
     * @param pattern the path pattern or URL to parse
     * @return the parsed path information
     * @throws IllegalArgumentException if the pattern is invalid
     */
    public static ParsedPath parse(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            throw new IllegalArgumentException("Pattern cannot be null or empty");
        }

        pattern = pattern.trim();

        // Try to parse as URL first
        if (pattern.startsWith("http://") || pattern.startsWith("https://")) {
            return parseUrl(pattern);
        }

        // Try space-only pattern: [SPACE]/**
        Matcher spaceOnlyMatcher = SPACE_ONLY_PATTERN.matcher(pattern);
        if (spaceOnlyMatcher.matches()) {
            String spaceKey = spaceOnlyMatcher.group(1);
            return new ParsedPath(spaceKey, null, null, RetrievalDepth.ALL_SPACE_PAGES);
        }

        // Try simple path pattern: [SPACE]/pages/[pageId]/[pageName]
        Matcher simplePathMatcher = SIMPLE_PATH_PATTERN.matcher(pattern);
        if (simplePathMatcher.matches()) {
            String spaceKey = simplePathMatcher.group(1);
            String pageId = simplePathMatcher.group(2);
            String pageName = decodePageName(simplePathMatcher.group(3));
            String wildcard = simplePathMatcher.group(4);
            RetrievalDepth depth = determineDepth(wildcard);
            return new ParsedPath(spaceKey, pageId, pageName, depth);
        }

        throw new IllegalArgumentException("Invalid pattern format: " + pattern);
    }

    /**
     * Parses a full Confluence URL.
     *
     * @param urlString the URL to parse
     * @return the parsed path information
     * @throws IllegalArgumentException if the URL is invalid
     */
    private static ParsedPath parseUrl(String urlString) {
        try {
            // First, check for wildcard suffix before parsing URL
            String wildcard = extractWildcardSuffix(urlString);
            
            // Remove wildcard from URL for parsing
            String cleanUrlString = urlString;
            if (wildcard != null) {
                cleanUrlString = urlString.substring(0, urlString.length() - wildcard.length());
            }
            
            URL url = new URL(cleanUrlString);
            String path = url.getPath();
            
            // Extract path segments
            List<String> segments = Arrays.stream(path.split("/"))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            // Expected format: wiki/spaces/[SPACE]/pages/[pageId]/[pageName]
            if (segments.isEmpty()) {
                throw new IllegalArgumentException("Invalid URL format: empty path");
            }

            int spacesIndex = segments.indexOf("spaces");
            int pagesIndex = segments.indexOf("pages");

            // Handle URL format with /wiki/spaces/
            if (spacesIndex >= 0 && spacesIndex + 1 < segments.size()) {
                String spaceKey = segments.get(spacesIndex + 1);
                
                // Check if it's a space-wide pattern (no pages segment or just space with /**)
                if (pagesIndex < 0 && "/**".equals(wildcard)) {
                    return new ParsedPath(spaceKey, null, null, RetrievalDepth.ALL_SPACE_PAGES);
                }
                
                // Check for pages segment
                if (pagesIndex >= 0 && pagesIndex + 1 < segments.size()) {
                    String pageId = segments.get(pagesIndex + 1);
                    String pageName = null;
                    if (pagesIndex + 2 < segments.size()) {
                        pageName = decodePageName(segments.get(pagesIndex + 2));
                    }
                    
                    // Determine depth based on wildcard
                    RetrievalDepth depth = determineDepth(wildcard);
                    
                    return new ParsedPath(spaceKey, pageId, pageName, depth);
                }
                
                throw new IllegalArgumentException("Invalid URL format: missing pages segment");
            }

            throw new IllegalArgumentException("Invalid URL format: missing spaces segment");
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Malformed URL: " + urlString, e);
        }
    }

    /**
     * Extracts wildcard suffix from a URL or path string.
     *
     * @param pattern the pattern to extract from
     * @return the wildcard suffix ("/*" or "/**") or null if none
     */
    private static String extractWildcardSuffix(String pattern) {
        if (pattern.endsWith("/**")) {
            return "/**";
        } else if (pattern.endsWith("/*")) {
            return "/*";
        }
        return null;
    }

    /**
     * Determines the retrieval depth based on wildcard suffix.
     *
     * @param wildcard the wildcard suffix ("/*" or "/**") or null
     * @return the appropriate retrieval depth
     */
    private static RetrievalDepth determineDepth(String wildcard) {
        if (wildcard == null) {
            return RetrievalDepth.PAGE_ONLY;
        }
        if ("/**".equals(wildcard)) {
            return RetrievalDepth.ALL_DESCENDANTS;
        }
        if ("/*".equals(wildcard)) {
            return RetrievalDepth.IMMEDIATE_CHILDREN;
        }
        return RetrievalDepth.PAGE_ONLY;
    }

    /**
     * Decodes a page name from URL encoding, handling '+' as spaces.
     *
     * @param pageName the encoded page name
     * @return the decoded page name, or null if input is null
     */
    private static String decodePageName(String pageName) {
        if (pageName == null) {
            return null;
        }
        // Handle URL encoding and '+' characters
        // Note: '+' in URLs can mean either literal '+' or space (form encoding)
        // We decode '%2B' to '+' and keep '+' as '+' (or decode to space based on context)
        try {
            return URLDecoder.decode(pageName, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // If decoding fails due to invalid escape sequences, return as-is
            return pageName;
        }
    }

    /**
     * Validates if the given pattern is a valid page path pattern.
     *
     * @param pattern the pattern to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidPattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return false;
        }
        try {
            parse(pattern);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
