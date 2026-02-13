package com.github.istin.dmtools.reporting.datasource;

import com.github.istin.dmtools.metrics.Metric;
import com.github.istin.dmtools.metrics.source.SourceCollector;
import com.github.istin.dmtools.report.model.KeyTime;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

public class FigmaDataSource extends DataSource {

    @Override
    public void performMetricCollection(Metric metric, KeyTimeCollector collector) throws Exception {
        SourceCollector sourceCollector = metric.getSourceCollector();
        if (sourceCollector == null) {
            return;
        }

        List<KeyTime> keyTimes = sourceCollector.performSourceCollection(
            metric.isPersonalized(),
            metric.getName()
        );

        for (KeyTime kt : keyTimes) {
            JSONObject metadata = new JSONObject();
            metadata.put("key", kt.getKey());
            metadata.put("who", kt.getWho());
            if (kt.getSummary() != null) {
                metadata.put("summary", kt.getSummary());
            }
            if (kt.getLink() != null) {
                metadata.put("link", kt.getLink());
            }
            collector.collect(Arrays.asList(kt), metadata, kt.getKey());
        }
    }

    @Override
    public JSONObject extractRawMetadata(Object item) {
        return new JSONObject();
    }

    @Override
    public String getSourceName() {
        return "figma";
    }
}
