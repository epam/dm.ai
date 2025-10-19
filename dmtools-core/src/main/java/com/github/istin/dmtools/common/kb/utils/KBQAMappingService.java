package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.agent.KBQuestionAnswerMappingAgent;
import com.github.istin.dmtools.common.kb.model.*;
import com.github.istin.dmtools.common.kb.params.QAMappingParams;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Applies AI-based question-answer mapping to link new answers/notes to existing questions.
 */
public class KBQAMappingService {

    private final KBQuestionAnswerMappingAgent qaMappingAgent;

    public KBQAMappingService(KBQuestionAnswerMappingAgent qaMappingAgent) {
        this.qaMappingAgent = qaMappingAgent;
    }

    public void applyMapping(AnalysisResult analysisResult,
                             KBContext context,
                             String extraInstructions,
                             Logger logger) throws Exception {
        if (context.getExistingQuestions().isEmpty()) {
            if (logger != null) {
                logger.info("No existing questions for Q→A mapping, skipping");
            }
            return;
        }

        List<QAMappingParams.AnswerLike> newAnswers = collectAnswerLikes(analysisResult);
        if (newAnswers.isEmpty()) {
            if (logger != null) {
                logger.info("No new unmapped answers/notes for Q→A mapping");
            }
            return;
        }

        List<KBContext.QuestionSummary> relevantQuestions = filterRelevantQuestions(newAnswers, context);
        if (relevantQuestions.isEmpty()) {
            if (logger != null) {
                logger.info("No relevant questions found for Q→A mapping");
            }
            return;
        }

        if (logger != null) {
            long unanswered = relevantQuestions.stream().filter(q -> !q.isAnswered()).count();
            logger.info("Running Q→A mapping: {} new answers/notes × {} relevant questions ({} unanswered)",
                    newAnswers.size(), relevantQuestions.size(), unanswered);
        }

        QAMappingParams params = new QAMappingParams();
        params.setNewAnswers(newAnswers);
        params.setExistingQuestions(relevantQuestions);
        params.setExtraInstructions(extraInstructions);

        QAMappingResult mappingResult = qaMappingAgent.run(params);
        applyResult(mappingResult, analysisResult, logger);
    }

    private List<QAMappingParams.AnswerLike> collectAnswerLikes(AnalysisResult analysisResult) {
        List<QAMappingParams.AnswerLike> newAnswers = new ArrayList<>();
        for (Answer answer : analysisResult.getAnswers()) {
            if (answer.getAnswersQuestion() == null || answer.getAnswersQuestion().isEmpty()) {
                newAnswers.add(QAMappingParams.AnswerLike.fromAnswer(answer));
            }
        }
        for (Note note : analysisResult.getNotes()) {
            newAnswers.add(QAMappingParams.AnswerLike.fromNote(note));
        }
        return newAnswers;
    }

    private List<KBContext.QuestionSummary> filterRelevantQuestions(List<QAMappingParams.AnswerLike> answers,
                                                                    KBContext context) {
        Set<String> areas = answers.stream()
                .map(QAMappingParams.AnswerLike::getArea)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> topics = answers.stream()
                .flatMap(a -> a.getTopics() != null ? a.getTopics().stream() : Stream.empty())
                .collect(Collectors.toSet());

        return context.getExistingQuestions().stream()
                .filter(q -> areas.contains(q.getArea()) ||
                        (q.getArea() != null && topics.stream().anyMatch(t -> q.getArea().contains(t))))
                .sorted(Comparator.comparing(KBContext.QuestionSummary::isAnswered))
                .collect(Collectors.toList());
    }

    private void applyResult(QAMappingResult mappingResult,
                             AnalysisResult analysisResult,
                             Logger logger) {
        for (QAMappingResult.Mapping mapping : mappingResult.getMappings()) {
            if (mapping.getConfidence() < 0.6) {
                if (logger != null) {
                    logger.debug("Skipping low-confidence mapping: {} → {} ({})",
                            mapping.getAnswerId(), mapping.getQuestionId(), mapping.getConfidence());
                }
                continue;
            }

            String answerId = mapping.getAnswerId();
            String questionId = mapping.getQuestionId();

            if (answerId.startsWith("a_")) {
                analysisResult.getAnswers().stream()
                        .filter(a -> a.getId().equals(answerId))
                        .findFirst()
                        .ifPresent(a -> {
                            a.setAnswersQuestion(questionId);
                            if (logger != null) {
                                logger.info("Mapped answer {} to question {} (confidence: {})",
                                        answerId, questionId, mapping.getConfidence());
                            }
                        });
            } else if (answerId.startsWith("n_")) {
                Optional<Note> noteOpt = analysisResult.getNotes().stream()
                        .filter(n -> n.getId().equals(answerId))
                        .findFirst();
                if (noteOpt.isPresent()) {
                    Note note = noteOpt.get();
                    analysisResult.getNotes().remove(note);

                    Answer newAnswer = new Answer();
                    newAnswer.setId(answerId.replace("n_", "a_"));
                    newAnswer.setAuthor(note.getAuthor());
                    newAnswer.setText(note.getText());
                    newAnswer.setDate(note.getDate());
                    newAnswer.setArea(note.getArea());
                    newAnswer.setTopics(note.getTopics());
                    newAnswer.setTags(note.getTags());
                    newAnswer.setAnswersQuestion(questionId);
                    newAnswer.setQuality(mapping.getConfidence());
                    newAnswer.setLinks(note.getLinks());
                    analysisResult.getAnswers().add(newAnswer);

                    if (logger != null) {
                        logger.info("Converted note {} to answer and mapped to question {} (confidence: {})",
                                answerId, questionId, mapping.getConfidence());
                    }
                }
            }
        }
    }
}
