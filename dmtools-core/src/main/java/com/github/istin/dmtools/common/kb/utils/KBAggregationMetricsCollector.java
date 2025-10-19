package com.github.istin.dmtools.common.kb.utils;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple metrics collector for aggregation timing statistics.
 */
public class KBAggregationMetricsCollector {

    private final AtomicLong aggregationCount = new AtomicLong();
    private final AtomicLong aggregationTotalMillis = new AtomicLong();
    private final AtomicLong aggregationMaxMillis = new AtomicLong();

    public void recordAggregation(long durationMillis) {
        if (durationMillis < 0) {
            durationMillis = 0;
        }
        aggregationCount.incrementAndGet();
        aggregationTotalMillis.addAndGet(durationMillis);
        final long finalDuration = durationMillis;
        aggregationMaxMillis.getAndUpdate(current -> Math.max(current, finalDuration));
    }

    public long getAggregationCount() {
        return aggregationCount.get();
    }

    public long getAggregationTotalMillis() {
        return aggregationTotalMillis.get();
    }

    public long getAggregationMaxMillis() {
        return aggregationMaxMillis.get();
    }

    public long getAggregationAverageMillis() {
        long count = aggregationCount.get();
        return count == 0 ? 0 : aggregationTotalMillis.get() / count;
    }
}
