package com.github.istin.dmtools.di;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.agent.*;
import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.atlassian.jira.BasicJiraClient;
import com.github.istin.dmtools.context.ContextOrchestrator;
import com.github.istin.dmtools.presentation.PresentationMakerOrchestrator;
import com.github.istin.dmtools.prompt.IPromptTemplateReader;
import com.github.istin.dmtools.search.ConfluenceSearchOrchestrator;
import com.github.istin.dmtools.search.TrackerSearchOrchestrator;
import dagger.Module;
import dagger.Provides;
import java.io.IOException;

@Module
public class AIAgentsModule {

    @Provides
    SourceImpactAssessmentAgent provideSourceImpactAssessmentAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        return new SourceImpactAssessmentAgent(ai, promptTemplateReader);
    }

    @Provides
    KeywordGeneratorAgent provideKeywordGeneratorAgent() {
        return new KeywordGeneratorAgent();
    }

    @Provides
    SummaryContextAgent provideSummaryContextAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        return new SummaryContextAgent(ai, promptTemplateReader);
    }

    @Provides
    SnippetExtensionAgent provideSnippetExtensionAgent() {
        return new SnippetExtensionAgent();
    }

    @Provides
    RequestDecompositionAgent provideRequestSimplifierAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        return new RequestDecompositionAgent(ai, promptTemplateReader);
    }

    @Provides
    TestCaseGeneratorAgent provideTestCaseGeneratorAgent() {
        return new TestCaseGeneratorAgent();
    }

    @Provides
    RelatedTestCasesAgent provideRelatedTestCasesAgent() {
        return new RelatedTestCasesAgent();
    }

    @Provides
    RelatedTestCaseAgent provideRelatedTestCaseAgent() {
        return new RelatedTestCaseAgent();
    }

    @Provides
    TeamAssistantAgent provideTeamAssistantAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        return new TeamAssistantAgent(ai, promptTemplateReader);
    }

    @Provides
    SearchResultsAssessmentAgent provideSearchResultsAssessmentAgent() {
        return new SearchResultsAssessmentAgent();
    }

    @Provides
    TestCaseVisualizerAgent provideTestCaseVisualizerAgent() {
        return new TestCaseVisualizerAgent();
    }

    @Provides
    TaskExecutionAgent provideTaskExecutionAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        return new TaskExecutionAgent(ai, promptTemplateReader);
    }

    @Provides
    TaskProgressAgent provideTaskProgressAgent(AI ai, IPromptTemplateReader promptTemplateReader) {
        return new TaskProgressAgent(ai, promptTemplateReader);
    }

    @Provides
    ContentMergeAgent provideContentMergeAgent() {
        return new ContentMergeAgent();
    }

    @Provides
    ConfluenceSearchOrchestrator provideConfluenceSearchOrchestrator() {
        try {
            return new ConfluenceSearchOrchestrator(BasicConfluence.getInstance());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Provides
    TrackerSearchOrchestrator provideTrackerSearchOrchestrator() {
        try {
            return new TrackerSearchOrchestrator(BasicJiraClient.getInstance());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Provides
    ContextOrchestrator provideContextOrchestrator(SummaryContextAgent summaryContextAgent, ContentMergeAgent contentMergeAgent) {
        return new ContextOrchestrator(summaryContextAgent, contentMergeAgent);
    }

    @Provides
    PresentationMakerOrchestrator providePresentationMakerOrchestrator() {
        return new PresentationMakerOrchestrator();
    }

    @Provides
    BusinessAreaAssessmentAgent provideBusinessAreaAssessmentAgent() {
        return new BusinessAreaAssessmentAgent();
    }

    @Provides
    ToolSelectorAgent provideToolSelectorAgent() {
        return new ToolSelectorAgent();
    }
}
