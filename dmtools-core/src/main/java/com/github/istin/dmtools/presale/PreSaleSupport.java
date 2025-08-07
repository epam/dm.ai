package com.github.istin.dmtools.presale;

import com.github.istin.dmtools.ai.AI;
import com.github.istin.dmtools.ai.JAssistant;
import com.github.istin.dmtools.atlassian.confluence.BasicConfluence;
import com.github.istin.dmtools.atlassian.confluence.model.Content;
import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.documentation.DocumentationEditor;
import com.github.istin.dmtools.documentation.area.KeyAreaMapperViaConfluence;
import com.github.istin.dmtools.documentation.area.TicketDocumentationHistoryTrackerViaConfluence;
import com.github.istin.dmtools.job.AbstractJob;
import com.github.istin.dmtools.ai.dial.BasicDialAI;
import com.github.istin.dmtools.prompt.PromptManager;
import com.github.istin.dmtools.pdf.PdfAsTrackerClient;
import com.github.istin.dmtools.pdf.model.PdfPageAsTicket;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Set;

public class PreSaleSupport extends AbstractJob<PreSaleSupportParams, String> {

    @Override
    public String runJob(PreSaleSupportParams preSaleSupportParams) throws Exception {
        String confluenceRootPage = preSaleSupportParams.getConfluenceRootPage();
        String folderWithPdfAssets = preSaleSupportParams.getFolderWithPdfAssets();
        String prefix = "jai";
        PdfAsTrackerClient rfpInputTracker = new PdfAsTrackerClient(folderWithPdfAssets);
        BasicDialAI ai = new BasicDialAI();
        PromptManager promptManager = new PromptManager();

        JAssistant jAssistant = new JAssistant(rfpInputTracker, null, ai, promptManager);

        rfpInputTracker.searchAndPerform(ticket -> {
            List<? extends IAttachment> attachments = ticket.getAttachments();
            if (!attachments.isEmpty() && ticket.getSnapshotDescription() == null) {
                String pageSnapshot = jAssistant.combineTextAndImage(ticket.getDescription(), ticket.getPageSnapshot());
                rfpInputTracker.updatePageSnapshot(ticket, pageSnapshot);
            }

            //Identify key requirements, objectives, and constraints.
            jAssistant.identifyIsContentRelatedToRequirementsAndMarkViaLabel(prefix, ticket);
//                rfpInputTracker.deleteLabelInTicket(ticket, "jai_not_requirements");
//                rfpInputTracker.deleteLabelInTicket(ticket, "jai_requirements");

            //check if the content is related to Timeline
            jAssistant.identifyIsContentRelatedToTimelineAndMarkViaLabel(prefix, ticket);
            //check if the content is related to team setup or any other expenses
            jAssistant.identifyIsContentRelatedToTeamSetupAndMarkViaLabel(prefix, ticket);

            return false;
        }, null, new String[] {});

        BasicConfluence confluence = BasicConfluence.getInstance();
        TicketDocumentationHistoryTrackerViaConfluence ticketDocumentationHistoryTrackerViaConfluence = new TicketDocumentationHistoryTrackerViaConfluence(confluence);


        DocumentationEditor documentationEditor = new DocumentationEditor(jAssistant, rfpInputTracker, confluence, confluenceRootPage);


        Content rootContent = confluence.findContent(confluenceRootPage);

        //building project timeline
        rfpInputTracker.searchAndPerform(ticket -> {
            documentationEditor.extendDocumentationPageWithTicket(confluence, ticketDocumentationHistoryTrackerViaConfluence, ticket, confluenceRootPage + " Project Timeline", rootContent, new DocumentationEditor.DelegateToAI() {

                @Override
                public String askAI(String source) throws Exception {
                    return jAssistant.buildProjectTimeline(ticket, source);
                }
            });
            return false;
        }, "labels=" + prefix + "_" + JAssistant.LABEL_TIMELINE, new String[]{});

        //building Team Setup input and other expenses
        rfpInputTracker.searchAndPerform(ticket -> {
            documentationEditor.extendDocumentationPageWithTicket(confluence, ticketDocumentationHistoryTrackerViaConfluence, ticket, confluenceRootPage + " Team Setup and Expenses", rootContent, new DocumentationEditor.DelegateToAI() {

                @Override
                public String askAI(String source) throws Exception {
                    return jAssistant.buildTeamSetupAndLicenses(ticket, source);
                }
            });
            return false;
        }, "labels=" + prefix + "_" + JAssistant.LABEL_TEAM_SETUP, new String[]{});

        //building requirements overview
        String rootRequirementsPageName = confluenceRootPage + " Requirements";
        confluence.findOrCreate(rootRequirementsPageName, rootContent.getId(), "");

        JSONObject jsonObject = documentationEditor.buildExistingAreasStructureForConfluence(confluenceRootPage, rootRequirementsPageName);
        Set<String> keys = jsonObject.keySet();
        KeyAreaMapperViaConfluence ticketAreaMapper = new KeyAreaMapperViaConfluence(confluenceRootPage, rootRequirementsPageName, confluence);
        List<PdfPageAsTicket> dataInputTicketsWithRequirements = rfpInputTracker.searchAndPerform("labels=" + prefix + "_" + JAssistant.LABEL_REQUIREMENTS, new String[]{});
        if (keys.isEmpty()) {
            buildKeyAreas(rootRequirementsPageName, confluence, rfpInputTracker, documentationEditor, dataInputTicketsWithRequirements, ticketAreaMapper);
        }

        documentationEditor.buildDetailedPageWithRequirementsForInputData(dataInputTicketsWithRequirements, confluenceRootPage, rootRequirementsPageName, confluence, ticketAreaMapper, ticketDocumentationHistoryTrackerViaConfluence, false);



        //TODO Analyze the requirements to determine the most efficient technology stack (e.g., React Native, Flutter for cross-platform development, or native iOS and Android).
        //TODO Recommended technology stack with justifications.

        //TODO Assess the potential challenges and draft mitigation strategies.
        //TODO List of potential challenges with mitigation strategies.

        //TODO Develop a comprehensive project plan detailing the milestones, tasks, and timelines.
        //TODO Include considerations for development, testing, deployment, and post-launch support.
//                Deliverables:
//
//                High-level project timeline.
//                Milestones and deliverables schedule.
//                Resource allocation and team composition.

                /*
                 Budget Estimation
                    Action:

                    Collaborate with the finance team to draft a budget estimate covering development, testing, deployment, and potential contingencies.
                    Ensure the estimate aligns with standard industry practices and the complexity of the project.
                    Deliverables:

                    Detailed budget overview.
                    Cost breakdown by phases and activities.
                    7. Prepare the Proposal Document

                    Action:

                    Consolidate all the gathered information into a professional proposal document.
                    Address each point specifically requested in the RFP (technology stack, challenges, implementation recommendations, timeline, team composition, and budget).
                    Structure:

                    Executive Summary
                    Understanding of Requirements
                    Proposed Solution
                    Technology Stack
                    Functional Implementation
                    Project Plan and Timeline
                    Project Team Composition
                    Roles and Responsibilities
                    Budget Overview
                    Cost Breakdown
                    Risk Mitigation Strategies
                    Conclusion and Next Steps
                    8. Quality Assurance

                    Action:

                    Proofread the proposal document for errors and inconsistencies.
                    Ensure alignment with client requirements and expectations.
                 */
        return "Success";
    }

    private static void buildKeyAreas(String rootRequirementsPageName, BasicConfluence confluence, PdfAsTrackerClient rfpInputTracker, DocumentationEditor documentationEditor, List<PdfPageAsTicket> dataInputTicketsWithRequirements, KeyAreaMapperViaConfluence ticketAreaMapper) throws Exception {
        JSONArray draftFeatureAreas = documentationEditor.buildDraftFeatureAreasByDataInput(dataInputTicketsWithRequirements, null);
        draftFeatureAreas = documentationEditor.cleanFeatureAreas(draftFeatureAreas);
        System.out.println(draftFeatureAreas);
        JSONObject optimizedFeatureAreas = documentationEditor.createFeatureAreasTree(draftFeatureAreas);
        System.out.println(optimizedFeatureAreas);
        documentationEditor.buildConfluenceStructure(optimizedFeatureAreas, dataInputTicketsWithRequirements, rootRequirementsPageName, confluence, ticketAreaMapper);
    }

    @Override
    public AI getAi() {
        return null;
    }
}