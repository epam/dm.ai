// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.formula;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates metric formulas using GraalJS.
 * Supports ${Metric Label} placeholders which are replaced with numeric values.
 */
public final class FormulaEvaluator {

    private static final Logger logger = LogManager.getLogger(FormulaEvaluator.class);
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private static final ThreadLocal<Context> JS_CONTEXT = ThreadLocal.withInitial(() ->
        Context.newBuilder("js")
            .allowAllAccess(false)
            .build()
    );

    private FormulaEvaluator() {
    }

    public static double evaluate(String formula, Map<String, Double> metricValues) {
        if (formula == null || formula.trim().isEmpty()) {
            return 0.0;
        }

        String expr = replaceVariables(formula, metricValues);
        try {
            Value value = JS_CONTEXT.get().eval("js", expr);
            if (value == null || !value.isNumber()) {
                return 0.0;
            }
            double result = value.asDouble();
            if (Double.isNaN(result) || Double.isInfinite(result)) {
                return 0.0;
            }
            return result;
        } catch (Exception e) {
            logger.debug("Failed to evaluate formula '{}': {}", formula, e.getMessage());
            return 0.0;
        }
    }

    private static String replaceVariables(String formula, Map<String, Double> metricValues) {
        Matcher matcher = VAR_PATTERN.matcher(formula);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String metricName = matcher.group(1);
            double value = metricValues.getOrDefault(metricName, 0.0);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                value = 0.0;
            }
            matcher.appendReplacement(sb, String.valueOf(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
