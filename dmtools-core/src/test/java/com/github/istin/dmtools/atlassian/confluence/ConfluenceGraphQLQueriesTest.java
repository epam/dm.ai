package com.github.istin.dmtools.atlassian.confluence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfluenceGraphQLQueriesTest {

    @Test
    void testAdvancedSearchQueryNotNull() {
        assertNotNull(ConfluenceGraphQLQueries.ADVANCED_SEARCH_QUERY);
    }

    @Test
    void testAdvancedSearchQueryNotEmpty() {
        assertFalse(ConfluenceGraphQLQueries.ADVANCED_SEARCH_QUERY.isEmpty());
    }

    @Test
    void testAdvancedSearchQueryContainsKeyElements() {
        String query = ConfluenceGraphQLQueries.ADVANCED_SEARCH_QUERY;
        
        // Check for query structure
        assertTrue(query.contains("query AdvancedAGGSearchQuery"));
        assertTrue(query.contains("search {"));
        assertTrue(query.contains("search("));
        
        // Check for parameters
        assertTrue(query.contains("$experience: String!"));
        assertTrue(query.contains("$query: String!"));
        assertTrue(query.contains("$first: Int"));
        assertTrue(query.contains("$after: String"));
        assertTrue(query.contains("$filters: SearchFilterInput!"));
        assertTrue(query.contains("$isLivePagesEnabled: Boolean!"));
        
        // Check for fields
        assertTrue(query.contains("edges"));
        assertTrue(query.contains("node"));
        assertTrue(query.contains("id"));
        assertTrue(query.contains("title"));
        assertTrue(query.contains("type"));
        assertTrue(query.contains("url"));
        assertTrue(query.contains("entityId"));
        assertTrue(query.contains("excerpt"));
        assertTrue(query.contains("space {"));
        assertTrue(query.contains("confluenceEntity"));
        assertTrue(query.contains("pageInfo"));
        assertTrue(query.contains("endCursor"));
        assertTrue(query.contains("totalCount"));
    }

    @Test
    void testAdvancedSearchQueryStructure() {
        String query = ConfluenceGraphQLQueries.ADVANCED_SEARCH_QUERY;
        
        // Validate that the query has proper GraphQL structure
        assertTrue(query.trim().startsWith("query"), "Query should start with 'query' keyword");
        assertTrue(query.contains("{"));
        assertTrue(query.contains("}"));
        
        // Count opening and closing braces (should be balanced)
        long openingBraces = query.chars().filter(ch -> ch == '{').count();
        long closingBraces = query.chars().filter(ch -> ch == '}').count();
        assertEquals(openingBraces, closingBraces, "Opening and closing braces should be balanced");
    }

    @Test
    void testAdvancedSearchQueryContainsConfluenceSpecificFields() {
        String query = ConfluenceGraphQLQueries.ADVANCED_SEARCH_QUERY;
        
        // Confluence-specific fields
        assertTrue(query.contains("SearchConfluencePageBlogAttachment"));
        assertTrue(query.contains("ConfluencePage"));
        assertTrue(query.contains("ancestors"));
        assertTrue(query.contains("metadata"));
        assertTrue(query.contains("titleEmojiPublished"));
    }

    @Test
    void testAdvancedSearchQuerySupportsLivePages() {
        String query = ConfluenceGraphQLQueries.ADVANCED_SEARCH_QUERY;
        
        // Check for live pages feature flag
        assertTrue(query.contains("$isLivePagesEnabled: Boolean!"));
        assertTrue(query.contains("@include(if: $isLivePagesEnabled)"));
        assertTrue(query.contains("subtype"));
    }

    @Test
    void testAdvancedSearchQuerySupportsPagination() {
        String query = ConfluenceGraphQLQueries.ADVANCED_SEARCH_QUERY;
        
        // Pagination support
        assertTrue(query.contains("$first: Int"));
        assertTrue(query.contains("$after: String"));
        assertTrue(query.contains("pageInfo"));
        assertTrue(query.contains("endCursor"));
    }
}
