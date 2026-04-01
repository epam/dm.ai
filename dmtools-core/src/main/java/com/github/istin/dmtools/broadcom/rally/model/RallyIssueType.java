// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.broadcom.rally.model;

import com.github.istin.dmtools.common.utils.StringUtils;

import java.util.Arrays;
import java.util.List;

public class RallyIssueType {
    public static final String BY_TYPES = " _byTypes=";
    public static String DEFECT_SUITE = "DefectSuite";
    public static String PORTFOLIO_ITEM_INITIATIVE = "PortfolioItem/Initiative";
    public static String PORTFOLIO_ITEM_FEATURE = "PortfolioItem/Feature";
    public static String PORTFOLIO_ITEM_EPIC = "PortfolioItem/Epic";
    public static String TEST_CASE = "TestCase";
    public static String TASK = "Task";
    public static String HIERARCHICAL_REQUIREMENT = "HierarchicalRequirement";
    public static String DEFECT = "Defect";
    public static String TEST_SET = "TestSet";

    public static class QueryAndTypes {

        private String query;

        private List<String> types;

        public String getQuery() {
            return query;
        }

        public List<String> getTypes() {
            return types;
        }

    }

    public static String queryFilterByType(String query, String ... types) {
        return query + BY_TYPES + StringUtils.concatenate(",", types);
    }

    public static QueryAndTypes parseQuery(String query) {
        QueryAndTypes queryAndTypes = new QueryAndTypes();
        String[] split = query.split(BY_TYPES);
        queryAndTypes.query = split[0];
        if (split.length > 1) {
            queryAndTypes.types = Arrays.asList(split[1].split(","));
        }
        return queryAndTypes;
    }
}
