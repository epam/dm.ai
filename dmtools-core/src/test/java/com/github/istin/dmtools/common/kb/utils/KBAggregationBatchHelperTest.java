package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.model.AnalysisResult;
import com.github.istin.dmtools.common.kb.model.Answer;
import com.github.istin.dmtools.common.kb.model.Note;
import com.github.istin.dmtools.common.kb.model.Question;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class KBAggregationBatchHelperTest {

    private KBAggregationHelper aggregationHelper;
    private KBAggregationBatchHelper batchHelper;
    private Logger logger;
    private Path outputPath;

    @BeforeEach
    void setUp() {
        aggregationHelper = mock(KBAggregationHelper.class);
        batchHelper = new KBAggregationBatchHelper(aggregationHelper);
        logger = LogManager.getLogger(KBAggregationBatchHelperTest.class);
        outputPath = Path.of("/tmp");
    }

    @Test
    void aggregatesPeopleAndTopics() throws Exception {
        AnalysisResult result = new AnalysisResult();
        result.setQuestions(new ArrayList<>());
        result.setAnswers(new ArrayList<>());
        result.setNotes(new ArrayList<>());

        Question question = new Question();
        question.setAuthor("Alice");
        question.setTopics(new ArrayList<>(java.util.List.of("Mars")));
        result.getQuestions().add(question);

        Answer answer = new Answer();
        answer.setAuthor("Bob");
        answer.setTopics(new ArrayList<>(java.util.List.of("Rocket")));
        result.getAnswers().add(answer);

        Note note = new Note();
        note.setAuthor("Cara");
        note.setTopics(new ArrayList<>(java.util.List.of("Mars")));
        result.getNotes().add(note);

        batchHelper.aggregateBatch(result, outputPath, "extra", logger);

        verify(aggregationHelper).aggregatePerson("Alice", outputPath, "extra");
        verify(aggregationHelper).aggregatePerson("Bob", outputPath, "extra");
        verify(aggregationHelper).aggregatePerson("Cara", outputPath, "extra");
        verify(aggregationHelper).aggregateTopic("Mars", outputPath, "extra");
        verify(aggregationHelper).aggregateTopic("Rocket", outputPath, "extra");
    }
}
