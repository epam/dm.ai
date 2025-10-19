package com.github.istin.dmtools.common.kb.utils;

import com.github.istin.dmtools.common.kb.model.Answer;
import com.github.istin.dmtools.common.kb.model.Note;
import com.github.istin.dmtools.common.kb.model.Question;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KBFileParserTest {
    
    private KBFileParser parser;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        parser = new KBFileParser();
    }
    
    @Test
    void testExtractAuthor() {
        String content = "---\nauthor: \"John Doe\"\ndate: \"2025-01-01\"\n---\nContent";
        assertEquals("John Doe", parser.extractAuthor(content));
    }
    
    @Test
    void testExtractAuthorWithoutQuotes() {
        String content = "---\nauthor: Jane Smith\ndate: 2025-01-01\n---\nContent";
        assertEquals("Jane Smith", parser.extractAuthor(content));
    }
    
    @Test
    void testExtractAuthorNotFound() {
        String content = "---\ndate: 2025-01-01\n---\nContent";
        assertNull(parser.extractAuthor(content));
    }
    
    @Test
    void testExtractDate() {
        String content = "---\nauthor: John\ndate: \"2025-01-15\"\n---\nContent";
        assertEquals("2025-01-15", parser.extractDate(content));
    }
    
    @Test
    void testExtractDateLong() {
        String content = "---\ndate: \"2025-01-15T10:30:00Z\"\n---\nContent";
        assertEquals("2025-01-15", parser.extractDate(content));
    }
    
    @Test
    void testExtractDateNotFound() {
        String content = "---\nauthor: John\n---\nContent";
        assertEquals("", parser.extractDate(content));
    }
    
    @Test
    void testExtractArea() {
        String content = "---\narea: \"Technology/AI\"\n---\nContent";
        assertEquals("Technology/AI", parser.extractArea(content));
    }
    
    @Test
    void testExtractAreaNotFound() {
        String content = "---\nauthor: John\n---\nContent";
        assertNull(parser.extractArea(content));
    }
    
    @Test
    void testExtractQuality() {
        String content = "---\nquality: 0.85\n---\nContent";
        assertEquals(0.85, parser.extractQuality(content), 0.001);
    }
    
    @Test
    void testExtractQualityNotFound() {
        String content = "---\nauthor: John\n---\nContent";
        assertEquals(0.0, parser.extractQuality(content), 0.001);
    }
    
    @Test
    void testExtractTopics() {
        String content = "---\ntopics: [\"Docker\", \"Kubernetes\", \"DevOps\"]\n---\nContent";
        List<String> topics = parser.extractTopics(content);
        
        assertEquals(3, topics.size());
        assertEquals("Docker", topics.get(0));
        assertEquals("Kubernetes", topics.get(1));
        assertEquals("DevOps", topics.get(2));
    }
    
    @Test
    void testExtractTopicsWithoutQuotes() {
        String content = "---\ntopics: [Docker, Kubernetes]\n---\nContent";
        List<String> topics = parser.extractTopics(content);
        
        assertEquals(2, topics.size());
        assertEquals("Docker", topics.get(0));
        assertEquals("Kubernetes", topics.get(1));
    }
    
    @Test
    void testExtractTopicsEmpty() {
        String content = "---\nauthor: John\n---\nContent";
        List<String> topics = parser.extractTopics(content);
        assertTrue(topics.isEmpty());
    }
    
    @Test
    void testExtractTags() {
        String content = "---\ntags:\n  - #important\n  - #urgent\n  - technical\n---\nContent";
        List<String> tags = parser.extractTags(content);
        
        assertEquals(3, tags.size());
        assertEquals("important", tags.get(0));
        assertEquals("urgent", tags.get(1));
        assertEquals("technical", tags.get(2));
    }
    
    @Test
    void testExtractTagsEmpty() {
        String content = "---\nauthor: John\n---\nContent";
        List<String> tags = parser.extractTags(content);
        assertTrue(tags.isEmpty());
    }
    
    @Test
    void testExtractText() {
        String content = "---\nauthor: John\n---\n# Question: q_0001\n\n**Question:** q_0001\n\nWhat is Docker?\n\n**Asked by:** John";
        String text = parser.extractText(content);
        
        assertTrue(text.contains("What is Docker?"));
        assertFalse(text.startsWith("#"));
        assertFalse(text.startsWith("**Question:**"));
    }
    
    @Test
    void testExtractAnsweredBy() {
        String content = "**Answer:** [[../../answers/a_0005|a_0005]]";
        assertEquals("a_0005", parser.extractAnsweredBy(content));
    }
    
    @Test
    void testExtractAnsweredByNotFound() {
        String content = "Some content without answer link";
        assertEquals("", parser.extractAnsweredBy(content));
    }
    
    @Test
    void testExtractAnswersQuestion() {
        String content = "**Question:** [[../../questions/q_0010|q_0010]]";
        assertEquals("q_0010", parser.extractAnswersQuestion(content));
    }
    
    @Test
    void testExtractAnswersQuestionNotFound() {
        String content = "Some content without question link";
        assertEquals("", parser.extractAnswersQuestion(content));
    }
    
    @Test
    void testExtractQuestionText() {
        String content = "---\n---\n# Question: q_0001\n\nWhat is the meaning of life?\n\n**Asked by:** Alice";
        String questionText = parser.extractQuestionText(content);
        
        assertEquals("What is the meaning of life?", questionText);
    }
    
    @Test
    void testParseQuestionFromFile() throws IOException {
        // Create test question file
        Path questionFile = tempDir.resolve("q_0001.md");
        String content = """
                ---
                author: "Alice"
                date: "2025-01-15"
                area: "Philosophy"
                topics: ["Life", "Meaning"]
                tags:
                  - #deep
                  - #philosophical
                answered: false
                ---
                # Question: q_0001
                
                What is the meaning of life?
                
                **Asked by:** Alice
                """;
        Files.writeString(questionFile, content);
        
        Question question = parser.parseQuestionFromFile(questionFile);
        
        assertNotNull(question);
        assertEquals("q_0001", question.getId());
        assertEquals("Alice", question.getAuthor());
        assertEquals("2025-01-15", question.getDate());
        assertEquals("Philosophy", question.getArea());
        assertEquals(2, question.getTopics().size());
        assertEquals("Life", question.getTopics().get(0));
        assertEquals(2, question.getTags().size());
        assertEquals("deep", question.getTags().get(0));
        assertTrue(question.getText().contains("What is the meaning of life?"));
    }
    
    @Test
    void testParseAnswerFromFile() throws IOException {
        // Create test answer file
        Path answerFile = tempDir.resolve("a_0001.md");
        String content = """
                ---
                author: "Bob"
                date: "2025-01-16"
                area: "Philosophy"
                topics: ["Life"]
                quality: 0.9
                ---
                # Answer: a_0001
                
                **Question:** [[../../questions/q_0001|q_0001]]
                
                The meaning of life is 42.
                
                **Answered by:** Bob
                """;
        Files.writeString(answerFile, content);
        
        Answer answer = parser.parseAnswerFromFile(answerFile);
        
        assertNotNull(answer);
        assertEquals("a_0001", answer.getId());
        assertEquals("Bob", answer.getAuthor());
        assertEquals("2025-01-16", answer.getDate());
        assertEquals("Philosophy", answer.getArea());
        assertEquals(0.9, answer.getQuality(), 0.001);
        assertEquals("q_0001", answer.getAnswersQuestion());
        assertTrue(answer.getText().contains("The meaning of life is 42"));
    }
    
    @Test
    void testParseNoteFromFile() throws IOException {
        // Create test note file
        Path noteFile = tempDir.resolve("n_0001.md");
        String content = """
                ---
                author: "Charlie"
                date: "2025-01-17"
                area: "Technology"
                topics: ["Docker"]
                ---
                # Note: n_0001
                
                Docker is a containerization platform.
                
                **Noted by:** Charlie
                """;
        Files.writeString(noteFile, content);
        
        Note note = parser.parseNoteFromFile(noteFile);
        
        assertNotNull(note);
        assertEquals("n_0001", note.getId());
        assertEquals("Charlie", note.getAuthor());
        assertEquals("2025-01-17", note.getDate());
        assertEquals("Technology", note.getArea());
        assertEquals(1, note.getTopics().size());
        assertTrue(note.getText().contains("Docker is a containerization platform"));
    }
    
    @Test
    void testParseQuestionFromNonExistentFile() {
        Path nonExistent = tempDir.resolve("does-not-exist.md");
        Question result = parser.parseQuestionFromFile(nonExistent);
        assertNull(result);
    }
}

