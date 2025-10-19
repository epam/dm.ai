package com.github.istin.dmtools.common.kb.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Detailed contributions for a person profile
 */
@Data
public class PersonContributions {
    private List<ContributionItem> questions = new ArrayList<>();
    private List<ContributionItem> answers = new ArrayList<>();
    private List<ContributionItem> notes = new ArrayList<>();
    private List<TopicContribution> topics = new ArrayList<>();
    
    @Data
    public static class ContributionItem {
        private String id;
        private String topic;
        private String date;
        
        public ContributionItem(String id, String topic, String date) {
            this.id = id;
            this.topic = topic;
            this.date = date;
        }
    }
    
    @Data
    public static class TopicContribution {
        private String topicId;
        private int count;
        
        public TopicContribution(String topicId, int count) {
            this.topicId = topicId;
            this.count = count;
        }
    }
}

