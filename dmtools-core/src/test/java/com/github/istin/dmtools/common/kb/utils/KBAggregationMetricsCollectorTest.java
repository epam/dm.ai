package com.github.istin.dmtools.common.kb.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KBAggregationMetricsCollectorTest {

    @Test
    void recordsAggregations() {
        KBAggregationMetricsCollector collector = new KBAggregationMetricsCollector();
        collector.recordAggregation(100);
        collector.recordAggregation(200);
        collector.recordAggregation(50);

        assertEquals(3, collector.getAggregationCount());
        assertEquals(350, collector.getAggregationTotalMillis());
        assertEquals(200, collector.getAggregationMaxMillis());
        assertEquals(116, collector.getAggregationAverageMillis());
    }

    @Test
    void handlesNegativeDurations() {
        KBAggregationMetricsCollector collector = new KBAggregationMetricsCollector();
        collector.recordAggregation(-10);

        assertEquals(1, collector.getAggregationCount());
        assertEquals(0, collector.getAggregationTotalMillis());
        assertEquals(0, collector.getAggregationMaxMillis());
        assertEquals(0, collector.getAggregationAverageMillis());
    }

    @Test
    void handlesEmptyState() {
        KBAggregationMetricsCollector collector = new KBAggregationMetricsCollector();

        assertEquals(0, collector.getAggregationCount());
        assertEquals(0, collector.getAggregationTotalMillis());
        assertEquals(0, collector.getAggregationMaxMillis());
        assertEquals(0, collector.getAggregationAverageMillis());
    }
}
