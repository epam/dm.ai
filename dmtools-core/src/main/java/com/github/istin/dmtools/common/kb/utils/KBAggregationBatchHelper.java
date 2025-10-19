package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.model.AnalysisResult;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Performs batch aggregation for people and topics using KBAggregationHelper.
 */
public class KBAggregationBatchHelper {

    private final KBAggregationHelper aggregationHelper;

    public KBAggregationBatchHelper(KBAggregationHelper aggregationHelper) {
        this.aggregationHelper = aggregationHelper;
    }

    public void aggregateBatch(AnalysisResult analysisResult,
                               Path outputPath,
                               String extraInstructions,
                               Logger logger) throws Exception {
        Set<String> people = collectPeople(analysisResult);
        for (String person : people) {
            aggregationHelper.aggregatePerson(person, outputPath, extraInstructions);
        }

        Set<String> topics = collectTopics(analysisResult);
        for (String topic : topics) {
            aggregationHelper.aggregateTopic(topic, outputPath, extraInstructions);
        }

        if (logger != null) {
            logger.info("Aggregated {} people and {} topics", people.size(), topics.size());
        }
    }

    private Set<String> collectPeople(AnalysisResult analysisResult) {
        Set<String> people = new LinkedHashSet<>();
        analysisResult.getQuestions().forEach(q -> people.add(q.getAuthor()));
        analysisResult.getAnswers().forEach(a -> people.add(a.getAuthor()));
        analysisResult.getNotes().forEach(n -> {
            if (n.getAuthor() != null) {
                people.add(n.getAuthor());
            }
        });
        people.remove(null);
        people.remove("");
        return people;
    }

    private Set<String> collectTopics(AnalysisResult analysisResult) {
        Set<String> topics = new LinkedHashSet<>();
        analysisResult.getQuestions().forEach(q -> {
            if (q.getTopics() != null) {
                topics.addAll(q.getTopics());
            }
        });
        analysisResult.getAnswers().forEach(a -> {
            if (a.getTopics() != null) {
                topics.addAll(a.getTopics());
            }
        });
        analysisResult.getNotes().forEach(n -> {
            if (n.getTopics() != null) {
                topics.addAll(n.getTopics());
            }
        });
        return topics;
    }
}
