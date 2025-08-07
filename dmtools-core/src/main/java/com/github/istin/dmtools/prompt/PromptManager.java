package com.github.istin.dmtools.prompt;

import com.github.istin.dmtools.prompt.input.*;
import com.github.istin.dmtools.prompt.input.*;
import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.DefaultConfiguration;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public class PromptManager implements IPromptTemplateReader {

    public String checkPotentiallyEffectedFilesForTicket(CodeGeneration input) throws IOException, TemplateException {
        return stringFromTemplate(input, "developer_check_potentially_effected_files");
    }

    public String checkPotentiallyRelatedCommitsToTicket(CodeGeneration input) throws IOException, TemplateException {
        return stringFromTemplate(input, "developer_check_potentially_related_commits");
    }

    public String checkTaskTechnicalOrProduct(TicketBasedPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "po_check_story_technical_or_product");
    }

    public String checkStoryAreas(BAStoryAreaPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "ba_recognize_story_areas");
    }

    public String whatIsFeatureAreaOfStory(TextInputPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "ba_what_is_feature_area_of_story");
    }

    public String whatIsFeatureAreasOfDataInput(TextInputPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "ba_what_is_feature_areas_of_data_input");
    }

    public String createFeatureAreasTree(InputPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "ba_create_feature_areas_tree");
    }

    public String cleanFeatureAreas(InputPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "ba_clean_feature_areas");
    }

    public String buildDetailedPageWithRequirementsForInputData(NiceLookingDocumentationPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "ba_nice_looking_documentation_with_technical_details");
    }

    public String buildNiceLookingDocumentation(NiceLookingDocumentationPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "ba_nice_looking_documentation");
    }

    public String buildDorBasedOnExistingStories(NiceLookingDocumentationPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "ba_dor_based_on_existing_stories");
    }

    public String buildProjectTimelinePage(NiceLookingDocumentationPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "pm_build_project_timeline_page");
    }

    public String buildTeamSetupAndLicensesPage(NiceLookingDocumentationPrompt niceLookingDocumentationPrompt) throws TemplateException, IOException {
        return stringFromTemplate(niceLookingDocumentationPrompt, "pm_build_team_setup_and_licenses_page");
    }

    public String buildNiceLookingDocumentationWithTechnicalDetails(NiceLookingDocumentationPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "ba_nice_looking_documentation_with_technical_details");
    }

    public String checkSimilarTickets(SimilarStoriesPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "ba_find_similar_tickets");
    }

    public String estimateStory(SimilarStoriesPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "developer_estimate_story");
    }

    public String validateSimilarStory(SimilarStoriesPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "developer_validate_similar_story");
    }

    public String validateTestCaseRelatedToStory(TicketBasedPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "qa_validate_testcase_related_to_story");
    }

    public String validateTestCasesAreRelatedToStory(TicketBasedPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "qa_validate_testcases_are_related_to_story");
    }

    public String requestGenerateCodeForTicket(CodeGeneration input) throws IOException, TemplateException {
        return stringFromTemplate(input, "developer_generate_code");
    }

    public String requestTestGeneration(TestGeneration input) throws IOException, TemplateException {
        return stringFromTemplate(input, "developer_generate_test");
    }

    public String convertToHTML(InputPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "convert_to_html");
    }

    public String generateUserStoriesAsHTML(TicketBasedPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "ba_story_generation_html");
    }

    public String generateUserStoriesAsJSONArray(TicketBasedPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "ba_story_generation_json");
    }

    public String updateUserStoriesAsHTML(TicketBasedPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "ba_story_update_html");
    }

    public String updateUserStoriesAsJSONArray(TicketBasedPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "ba_story_update_json");
    }

    public String requestTestCasesForStoryAsHTML(TicketBasedPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "qa_story_test_cases_generation_html");
    }

    public String requestTestCasesForStoryAsJSONArray(TicketBasedPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "qa_story_test_cases_generation_json");
    }

    public String requestNiceLookingStoryInGherkinStyleAndPotentialQuestionsToPO(TicketBasedPrompt input) throws IOException, TemplateException {
        return stringFromTemplate(input, "ba_nice_looking_story_in_gherkin_style_and_potential_questions_to_po");
    }

    public String requestDeveloperStoryPullRequestReview(PullRequestReview input) throws IOException, TemplateException {
        return stringFromTemplate(input, "developer_story_pull_request_review");
    }

    public String requestDeveloperBugPullRequestReview(PullRequestReview input) throws IOException, TemplateException {
        return stringFromTemplate(input, "developer_bug_pull_request_review");
    }

    public String validatePotentiallyEffectedFile(TicketFilePrompt ticketFilePrompt) throws TemplateException, IOException {
        return stringFromTemplate(ticketFilePrompt, "developer_validate_potentially_effected_file");
    }

    private String stringFromTemplate(Object input, String template) throws IOException, TemplateException {
        return stringFromTemplate(this, input, template);
    }

    public static String stringFromTemplate(Object o, Object input, String template) throws IOException, TemplateException {
        Configurator.initialize(new DefaultConfiguration());

        Configuration cfg = new Configuration(Configuration.VERSION_2_3_27);
        cfg.setLocalizedLookup(false);
        cfg.setTemplateLoader(new ClassTemplateLoader(o.getClass().getClassLoader(), "/ftl"));


        Template temp;
        try {
            // First, try to load the .md file
            temp = cfg.getTemplate("prompts/" + template + ".md");
        } catch (IOException e) {
            // If .md file is not found, try to load the .xml file
            try {
                temp = cfg.getTemplate("prompts/" + template + ".xml");
            } catch (IOException ex) {
                // If both .md and .xml files are not found, throw an exception
                throw new IOException("Template file not found: " + template + " (neither .md nor .xml)", ex);
            }
        }


        Writer out = new StringWriter();
        temp.process(input, out);
        return out.toString();
    }


    public String isContentRelatedToRequirements(TicketBasedPrompt ticketBasedPrompt) throws TemplateException, IOException {
        return stringFromTemplate(ticketBasedPrompt, "ba_check_is_ticket_related_to_requirements");
    }

    public String isContentRelatedToTimeline(TicketBasedPrompt ticketBasedPrompt) throws TemplateException, IOException {
        return stringFromTemplate(ticketBasedPrompt, "pm_check_is_ticket_related_to_timeline");
    }

    public String isContentRelatedToTeamSetup(TicketBasedPrompt ticketBasedPrompt) throws TemplateException, IOException {
        return stringFromTemplate(ticketBasedPrompt, "pm_check_is_ticket_related_to_team_setup");
    }

    public String combineTextAndImage(InputPrompt inputPrompt) throws TemplateException, IOException {
        return stringFromTemplate(inputPrompt, "vision_combine_details_from_text_and_image");
    }


    public String saCreateSolutionForTicket(MultiTicketsPrompt multiTicketsPrompt) throws TemplateException, IOException{
        return stringFromTemplate(multiTicketsPrompt, "sa_write_solution_for_ticket");
    }

    public String baCollectRequirementsForTicket(MultiTicketsPrompt multiTicketsPrompt) throws TemplateException, IOException{
        return stringFromTemplate(multiTicketsPrompt, "ba_collect_requirements_for_ticket");
    }

    public String baBuildJqlForRequirementsSearching(MultiTicketsPrompt multiTicketsPrompt) throws TemplateException, IOException{
        return stringFromTemplate(multiTicketsPrompt, "ba_build_jql_for_requirements_searching");
    }

    public String baIsTicketRelatedToContent(MultiTicketsPrompt multiTicketsPrompt) throws TemplateException, IOException{
        return stringFromTemplate(multiTicketsPrompt, "ba_check_is_ticket_related_to_content");
    }

    public String createDiagrams(MultiTicketsPrompt multiTicketsPrompt) throws TemplateException, IOException{
        return stringFromTemplate(multiTicketsPrompt, "role_create_diagrams");
    }

    public String makeDailyScrumReportOfUserWork(ScrumDailyPrompt scrumDailyPrompt) throws TemplateException, IOException {
        return stringFromTemplate(scrumDailyPrompt, "scrum_daily_report");
    }

    @Override
    public String read(String promptName, PromptContext context) throws Exception {
        // Create a proper data model that includes both global and args
        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("global", context.getGlobal());
        dataModel.put("args", context.getArgs());
        return stringFromTemplate(this, dataModel, promptName);
    }
}
