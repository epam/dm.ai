package com.github.istin.dmtools.common.kb;

import com.github.istin.dmtools.common.kb.model.PersonContributions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KBStructureBuilder person profile generation
 * Focus: Testing contributions display (links vs simple counts)
 */
class KBStructureBuilderPersonProfileTest {

    @TempDir
    Path tempDir;

    private KBStructureBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new KBStructureBuilder();
    }

    /**
     * Test that person profile shows LINKS when contributions are provided
     */
    @Test
    void testBuildPersonProfile_WithContributions_ShowsLinks() throws IOException {
        // GIVEN: Person contributions with questions, answers, and notes
        PersonContributions contributions = new PersonContributions();
        contributions.getQuestions().add(new PersonContributions.ContributionItem("q_0001", "ai", "2025-10-24T10:00:00Z"));
        contributions.getQuestions().add(new PersonContributions.ContributionItem("q_0002", "platform", "2025-10-24T11:00:00Z"));
        contributions.getAnswers().add(new PersonContributions.ContributionItem("a_0001", "ai", "2025-10-24T12:00:00Z"));
        contributions.getNotes().add(new PersonContributions.ContributionItem("n_0001", "development", "2025-10-24T13:00:00Z"));
        
        // WHEN: Build person profile with contributions
        builder.buildPersonProfile("John Smith", tempDir, "source1", 2, 1, 1, contributions);
        
        // THEN: Profile should show LINKS, not just counts
        Path profileFile = tempDir.resolve("people/John_Smith/John_Smith.md");
        assertTrue(Files.exists(profileFile), "Profile file should exist");
        
        String content = Files.readString(profileFile);
        
        // Should have "## Questions Asked" header (not "- Questions asked: 2")
        assertTrue(content.contains("## Questions Asked"), "Should have 'Questions Asked' section header");
        assertFalse(content.contains("- Questions asked: 2"), "Should NOT have simple count text");
        
        // Should have links to actual questions
        assertTrue(content.contains("[[../../questions/q_0001|q_0001]]"), "Should link to q_0001");
        assertTrue(content.contains("[[../../questions/q_0002|q_0002]]"), "Should link to q_0002");
        assertTrue(content.contains("2025-10-24T10:00:00Z"), "Should show question date");
        
        // Should have "## Answers Provided" header
        assertTrue(content.contains("## Answers Provided"), "Should have 'Answers Provided' section header");
        assertFalse(content.contains("- Answers provided: 1"), "Should NOT have simple answer count");
        assertTrue(content.contains("[[../../answers/a_0001|a_0001]]"), "Should link to a_0001");
        
        // Should have "## Notes Contributed" header
        assertTrue(content.contains("## Notes Contributed"), "Should have 'Notes Contributed' section header");
        assertFalse(content.contains("- Notes contributed: 1"), "Should NOT have simple note count");
        assertTrue(content.contains("[[../../notes/n_0001|n_0001]]"), "Should link to n_0001");
    }

    /**
     * Test that person profile shows SIMPLE COUNTS when contributions are NOT provided (fallback)
     */
    @Test
    void testBuildPersonProfile_WithoutContributions_ShowsCounts() throws IOException {
        // GIVEN: No contributions (null)
        
        // WHEN: Build person profile without contributions
        builder.buildPersonProfile("Jane Doe", tempDir, "source1", 3, 2, 1, null);
        
        // THEN: Profile should show simple counts (fallback behavior)
        Path profileFile = tempDir.resolve("people/Jane_Doe/Jane_Doe.md");
        assertTrue(Files.exists(profileFile), "Profile file should exist");
        
        String content = Files.readString(profileFile);
        
        // Should have simple count lines
        assertTrue(content.contains("- Questions asked: 3"), "Should show simple question count");
        assertTrue(content.contains("- Answers provided: 2"), "Should show simple answer count");
        assertTrue(content.contains("- Notes contributed: 1"), "Should show simple note count");
        
        // Should NOT have section headers or links
        assertFalse(content.contains("## Questions Asked"), "Should NOT have section headers");
        assertFalse(content.contains("[[../../questions/"), "Should NOT have question links");
        assertFalse(content.contains("[[../../answers/"), "Should NOT have answer links");
        assertFalse(content.contains("[[../../notes/"), "Should NOT have note links");
    }

    /**
     * Test that person profile with EMPTY contributions shows nothing (no sections)
     */
    @Test
    void testBuildPersonProfile_WithEmptyContributions_ShowsNoSections() throws IOException {
        // GIVEN: Contributions object but all lists are empty
        PersonContributions contributions = new PersonContributions();
        // All lists (questions, answers, notes) are empty by default
        
        // WHEN: Build person profile with empty contributions
        builder.buildPersonProfile("Bob Johnson", tempDir, "source1", 0, 0, 0, contributions);
        
        // THEN: Profile should have AUTO_GENERATED section but no content inside
        Path profileFile = tempDir.resolve("people/Bob_Johnson/Bob_Johnson.md");
        assertTrue(Files.exists(profileFile), "Profile file should exist");
        
        String content = Files.readString(profileFile);
        
        // Should have AUTO_GENERATED markers
        assertTrue(content.contains("<!-- AUTO_GENERATED_START -->"), "Should have start marker");
        assertTrue(content.contains("<!-- AUTO_GENERATED_END -->"), "Should have end marker");
        
        // Should NOT have any section headers (all lists empty)
        assertFalse(content.contains("## Questions Asked"), "Should NOT have Questions section (empty)");
        assertFalse(content.contains("## Answers Provided"), "Should NOT have Answers section (empty)");
        assertFalse(content.contains("## Notes Contributed"), "Should NOT have Notes section (empty)");
        
        // Should NOT have simple counts either (not in fallback mode)
        assertFalse(content.contains("- Questions asked:"), "Should NOT have simple counts");
        assertFalse(content.contains("- Answers provided:"), "Should NOT have simple counts");
        assertFalse(content.contains("- Notes contributed:"), "Should NOT have simple counts");
    }

    /**
     * Test that person profile with only questions shows only questions section
     */
    @Test
    void testBuildPersonProfile_OnlyQuestions_ShowsOnlyQuestionsSection() throws IOException {
        // GIVEN: Contributions with only questions
        PersonContributions contributions = new PersonContributions();
        contributions.getQuestions().add(new PersonContributions.ContributionItem("q_0001", "ai", "2025-10-24T10:00:00Z"));
        // answers and notes are empty
        
        // WHEN: Build person profile
        builder.buildPersonProfile("Alice", tempDir, "source1", 1, 0, 0, contributions);
        
        // THEN: Should show only Questions Asked section
        Path profileFile = tempDir.resolve("people/Alice/Alice.md");
        String content = Files.readString(profileFile);
        
        assertTrue(content.contains("## Questions Asked"), "Should have Questions Asked section");
        assertTrue(content.contains("[[../../questions/q_0001|q_0001]]"), "Should link to question");
        
        // Should NOT show empty sections
        assertFalse(content.contains("## Answers Provided"), "Should NOT show empty Answers section");
        assertFalse(content.contains("## Notes Contributed"), "Should NOT show empty Notes section");
    }

    /**
     * Test that updatePersonFile uses contributions when provided
     */
    @Test
    void testUpdatePersonFile_WithContributions_UpdatesLinks() throws IOException {
        // GIVEN: Existing person file with simple counts
        builder.buildPersonProfile("Charlie", tempDir, "source1", 1, 0, 0, null);
        Path profileFile = tempDir.resolve("people/Charlie/Charlie.md");
        String initialContent = Files.readString(profileFile);
        assertTrue(initialContent.contains("- Questions asked: 1"), "Initial file should have simple count");
        
        // WHEN: Update with contributions
        PersonContributions contributions = new PersonContributions();
        contributions.getQuestions().add(new PersonContributions.ContributionItem("q_0001", "ai", "2025-10-24T10:00:00Z"));
        contributions.getQuestions().add(new PersonContributions.ContributionItem("q_0002", "platform", "2025-10-24T11:00:00Z"));
        
        builder.buildPersonProfile("Charlie", tempDir, "source2", 2, 0, 0, contributions);
        
        // THEN: File should now have links instead of simple counts
        String updatedContent = Files.readString(profileFile);
        
        assertTrue(updatedContent.contains("## Questions Asked"), "Should have section header after update");
        assertTrue(updatedContent.contains("[[../../questions/q_0001|q_0001]]"), "Should have q_0001 link");
        assertTrue(updatedContent.contains("[[../../questions/q_0002|q_0002]]"), "Should have q_0002 link");
        
        // Should NOT have simple counts anymore
        assertFalse(updatedContent.contains("- Questions asked: 1"), "Should NOT have old simple count");
        assertFalse(updatedContent.contains("- Questions asked: 2"), "Should NOT have simple count format");
    }

    /**
     * Test that contributions with topics show topics section
     */
    @Test
    void testBuildPersonProfile_WithTopics_ShowsTopicsSection() throws IOException {
        // GIVEN: Contributions with topics
        PersonContributions contributions = new PersonContributions();
        contributions.getTopics().add(new PersonContributions.TopicContribution("ai-agent-design", 5));
        contributions.getTopics().add(new PersonContributions.TopicContribution("jira-integration", 2));
        
        // WHEN: Build person profile
        builder.buildPersonProfile("David", tempDir, "source1", 0, 0, 0, contributions);
        
        // THEN: Should show Topics section with contribution counts
        Path profileFile = tempDir.resolve("people/David/David.md");
        String content = Files.readString(profileFile);
        
        assertTrue(content.contains("## Topics"), "Should have Topics section");
        assertTrue(content.contains("[[../../topics/ai-agent-design|ai-agent-design]]"), "Should link to topic");
        assertTrue(content.contains("5 contributions"), "Should show plural 'contributions'");
        assertTrue(content.contains("[[../../topics/jira-integration|jira-integration]]"), "Should link to topic");
        assertTrue(content.contains("2 contributions"), "Should show plural for 2");
    }

    /**
     * Test that contribution with single topic shows singular form
     */
    @Test
    void testBuildPersonProfile_WithSingleTopicContribution_ShowsSingular() throws IOException {
        // GIVEN: Contributions with single contribution to a topic
        PersonContributions contributions = new PersonContributions();
        contributions.getTopics().add(new PersonContributions.TopicContribution("test-topic", 1));
        
        // WHEN: Build person profile
        builder.buildPersonProfile("Eve", tempDir, "source1", 0, 0, 0, contributions);
        
        // THEN: Should show singular "contribution" (not "contributions")
        Path profileFile = tempDir.resolve("people/Eve/Eve.md");
        String content = Files.readString(profileFile);
        
        assertTrue(content.contains("1 contribution"), "Should show singular 'contribution'");
        assertFalse(content.matches(".*1\\s+contributions.*"), "Should NOT show '1 contributions' (plural)");
    }

    /**
     * Test that frontmatter is correctly populated
     */
    @Test
    void testBuildPersonProfile_Frontmatter_CorrectlyPopulated() throws IOException {
        // GIVEN: Person with contributions
        PersonContributions contributions = new PersonContributions();
        contributions.getQuestions().add(new PersonContributions.ContributionItem("q_0001", "ai", "2025-10-24T10:00:00Z"));
        
        // WHEN: Build person profile
        builder.buildPersonProfile("Frank Miller", tempDir, "source1", 1, 0, 0, contributions);
        
        // THEN: Frontmatter should be correct
        Path profileFile = tempDir.resolve("people/Frank_Miller/Frank_Miller.md");
        String content = Files.readString(profileFile);
        
        // Check frontmatter fields
        assertTrue(content.contains("id: \"Frank_Miller\""), "Should have normalized id");
        assertTrue(content.contains("name: \"Frank Miller\""), "Should have original name (not normalized)");
        assertTrue(content.contains("type: \"person\""), "Should have type");
        assertTrue(content.contains("sources: [\"source1\"]"), "Should have sources");
        assertTrue(content.contains("questionsAsked: 1"), "Should have question count");
        assertTrue(content.contains("answersProvided: 0"), "Should have answer count");
        assertTrue(content.contains("notesContributed: 0"), "Should have note count");
        assertTrue(content.contains("tags: [\"#person\", \"#source_source1\"]"), "Should have tags");
    }

    /**
     * Test that contribution items are sorted by ID
     */
    @Test
    void testBuildPersonProfile_ContributionsSorted_ByIdNumber() throws IOException {
        // GIVEN: Contributions in random order
        PersonContributions contributions = new PersonContributions();
        contributions.getQuestions().add(new PersonContributions.ContributionItem("q_0005", "ai", "2025-10-24T10:00:00Z"));
        contributions.getQuestions().add(new PersonContributions.ContributionItem("q_0002", "ai", "2025-10-24T11:00:00Z"));
        contributions.getQuestions().add(new PersonContributions.ContributionItem("q_0010", "ai", "2025-10-24T12:00:00Z"));
        contributions.getQuestions().add(new PersonContributions.ContributionItem("q_0001", "ai", "2025-10-24T13:00:00Z"));
        
        // WHEN: Build person profile
        builder.buildPersonProfile("Grace", tempDir, "source1", 4, 0, 0, contributions);
        
        // THEN: Questions should be sorted by ID number (q_0001, q_0002, q_0005, q_0010)
        Path profileFile = tempDir.resolve("people/Grace/Grace.md");
        String content = Files.readString(profileFile);
        
        // Find positions of each question link
        int pos_q0001 = content.indexOf("q_0001");
        int pos_q0002 = content.indexOf("q_0002");
        int pos_q0005 = content.indexOf("q_0005");
        int pos_q0010 = content.indexOf("q_0010");
        
        // Verify order
        assertTrue(pos_q0001 < pos_q0002, "q_0001 should come before q_0002");
        assertTrue(pos_q0002 < pos_q0005, "q_0002 should come before q_0005");
        assertTrue(pos_q0005 < pos_q0010, "q_0005 should come before q_0010");
    }

    /**
     * Regression test: Verify that without contributions (null), fallback works correctly
     * This is the old behavior that should still work for backwards compatibility
     */
    @Test
    void testBuildPersonProfile_NullContributions_FallbackToSimpleCounts() throws IOException {
        // GIVEN: Explicitly null contributions (old code path)
        
        // WHEN: Build person profile with null contributions
        builder.buildPersonProfile("Old Style User", tempDir, "source1", 5, 3, 2, null);
        
        // THEN: Should use fallback simple count format
        Path profileFile = tempDir.resolve("people/Old_Style_User/Old_Style_User.md");
        String content = Files.readString(profileFile);
        
        // Verify fallback format
        assertTrue(content.contains("- Questions asked: 5"), "Fallback: should show simple count");
        assertTrue(content.contains("- Answers provided: 3"), "Fallback: should show simple count");
        assertTrue(content.contains("- Notes contributed: 2"), "Fallback: should show simple count");
        
        // Should NOT have detailed sections
        assertFalse(content.contains("## Questions Asked"), "Fallback: should NOT have section headers");
        assertFalse(content.contains("[[../../questions/"), "Fallback: should NOT have links");
    }
}

