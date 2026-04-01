// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.confluence;

public class ConfluenceGraphQLQueries {
    public static final String ADVANCED_SEARCH_QUERY = """
        query AdvancedAGGSearchQuery(
          $experience: String!,
          $analytics: SearchAnalyticsInput,
          $query: String!,
          $first: Int,
          $after: String,
          $filters: SearchFilterInput!,
          $experimentContext: SearchExperimentContextInput,
          $isLivePagesEnabled: Boolean!
        ) {
          search {
            search(
              experience: $experience
              analytics: $analytics
              query: $query
              first: $first
              after: $after
              filters: $filters
              experimentContext: $experimentContext
            ) {
              edges {
                node {
                  id
                  title
                  type
                  url
                  ... on SearchConfluencePageBlogAttachment {
                    entityId
                    excerpt
                    isVerified
                    lastModified
                    space {
                      id
                      spaceId
                      name
                      key
                      webUiLink
                    }
                    confluenceEntity {
                      ... on ConfluencePage {
                        id
                        type
                        subtype @include(if: $isLivePagesEnabled)
                        ancestors {
                          title
                          id
                          links {
                            webUi
                          }
                        }
                        metadata {
                          titleEmojiPublished {
                            id
                            key
                            value
                          }
                        }
                      }
                    }
                  }
                }
              }
              pageInfo {
                endCursor
              }
              totalCount
            }
          }
        }
    """;
}