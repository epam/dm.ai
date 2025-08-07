package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.presentation.JSPresentationMakerBridge;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

public class DataToPresentationScriptGeneratorAgentTest {

    private DataToPresentationScriptGeneratorAgent dataToPresentationScriptGeneratorAgent;
    private UserRequestToPresentationScriptParamsAgent userRequestToPresentationScriptParamsAgent;

    @BeforeEach
    void setUp() {
        // The agent should handle its own Dial and PromptManager initialization internally
        // (likely in AbstractSimpleAgent or its own constructor)
        dataToPresentationScriptGeneratorAgent = new DataToPresentationScriptGeneratorAgent();
        userRequestToPresentationScriptParamsAgent = new UserRequestToPresentationScriptParamsAgent();
    }

    @Test
    void testOfPipeline() throws Exception {
        String userRequest = "Hey team, I need to create a presentation analyzing our recent training program feedback. Can you help me build slides that show:\n" +
                "\n" +
                "Overview Dashboard - Overall satisfaction trends and key metrics\n" +
                "Satisfaction Breakdown - Charts showing ratings across different categories (Content, Instructor, Materials, Logistics)\n" +
                "Participant Insights - Word cloud or themes from the qualitative feedback\n" +
                "Improvement Areas - Top suggestions and action items\n" +
                "Recommendations - Next steps based on the data\n" +
                "The presentation is for the Learning & Development committee meeting next Tuesday. I want to focus on data-driven insights rather than just raw numbers. Can you make it visually engaging with charts and highlight the most actionable feedback?\n" +
                "\n" +
                "Also, please include a slide comparing our scores to industry benchmarks if possible, and maybe a timeline showing how we can implement the top 3 suggested improvements.\n" +
                "\n" +
                "Target audience is L&D leadership and program managers - they'll want concrete next steps, not just analysis.";
        String applicationContext = "\"ID\",\"Start Time\",\"End Time\",\"Respondent\",\"Email\",\"Overall Score\",\"Content Quality\",\"Instructor Performance\",\"Materials\",\"Logistics\",\"Would Recommend\",\"What did you like most?\",\"Suggestions for improvement\"\n" +
                "\"1\",\"8/22/2024 14:15\",\"8/22/2024 14:22\",\"anonymous\",,\"8\",\"Very satisfied\",\"Neither satisfied nor dissatisfied\",\"Somewhat satisfied\",\"Neither satisfied nor dissatisfied\",\"Very satisfied\",\"The practical exercises were valuable and the real-world examples resonated well, though some theoretical concepts need better explanation. Good mix of experienced participants.\",\n" +
                "\"2\",\"8/22/2024 14:18\",\"8/22/2024 14:23\",\"anonymous\",,\"9\",\"Somewhat satisfied\",\"Somewhat satisfied\",\"Neither satisfied nor dissatisfied\",\"Neither satisfied nor dissatisfied\",\"Somewhat satisfied\",\"hands-on approach and interactive sessions\",\"we've implemented similar training internally\"\n" +
                "\"3\",\"8/22/2024 14:15\",\"8/22/2024 14:25\",\"anonymous\",,\"8\",\"Neither satisfied nor dissatisfied\",\"Somewhat satisfied\",\"Somewhat satisfied\",\"Neither satisfied nor dissatisfied\",\"Somewhat satisfied\",\"Well-structured curriculum for the target skill level, supportive learning environment\",\"The facilitator does excellent work, but some senior team leads who could share valuable insights weren't present.\"\n" +
                "\"4\",\"8/22/2024 14:15\",\"8/22/2024 14:26\",\"anonymous\",,\"9\",\"Very satisfied\",\"Somewhat satisfied\",\"Neither satisfied nor dissatisfied\",\"Somewhat dissatisfied\",\"Very satisfied\",\"Engaging delivery style, clear learning objectives, excellent use of case studies.\",\"Encourage more peer-to-peer learning instead of just lecture format. This is skills development, not information broadcast. Could boost group participation since the same people always contribute.\"\n" +
                "\"5\",\"8/22/2024 14:27\",\"8/22/2024 14:28\",\"anonymous\",,\"7\",\"Very satisfied\",\"Somewhat satisfied\",\"Neither satisfied nor dissatisfied\",\"Neither satisfied nor dissatisfied\",\"Somewhat satisfied\",,\n" +
                "\"6\",\"8/22/2024 14:27\",\"8/22/2024 14:30\",\"anonymous\",,\"9\",\"Somewhat satisfied\",\"Somewhat satisfied\",\"Very satisfied\",\"Somewhat dissatisfied\",\"Very satisfied\",\"Networking with industry professionals\",\n" +
                "\"7\",\"8/22/2024 14:21\",\"8/22/2024 14:30\",\"anonymous\",,\"9\",\"Very satisfied\",\"Somewhat satisfied\",\"Somewhat satisfied\",\"Neither satisfied nor dissatisfied\",\"Very satisfied\",,\n" +
                "\"8\",\"8/22/2024 14:26\",\"8/22/2024 14:30\",\"anonymous\",,\"9\",\"Very satisfied\",\"Very satisfied\",\"Very satisfied\",\"Very satisfied\",\"Very satisfied\",\"Consistent skill-building progression and clear certification pathway\",\"Add virtual reality simulation components.\"\n" +
                "\"9\",\"8/22/2024 18:25\",\"8/22/2024 18:27\",\"anonymous\",,\"6\",\"Neither satisfied nor dissatisfied\",\"Somewhat dissatisfied\",\"Neither satisfied nor dissatisfied\",\"Somewhat dissatisfied\",\"Somewhat satisfied\",,\"Verify all equipment functionality before sessions\"\n" +
                "\"10\",\"8/23/2024 09:08\",\"8/23/2024 09:15\",\"anonymous\",,\"8\",\"Somewhat satisfied\",\"Somewhat satisfied\",\"Somewhat satisfied\",\"Neither satisfied nor dissatisfied\",\"Somewhat satisfied\",\"Builds confidence in applying new methodologies, creates connections with like-minded professionals facing similar challenges.\",\"Include expert practitioners who can demonstrate advanced techniques, and invite department heads to share implementation strategies\"\n" +
                "\"11\",\"8/23/2024 10:05\",\"8/23/2024 10:16\",\"anonymous\",,\"8\",\"Very satisfied\",\"Somewhat satisfied\",\"Somewhat satisfied\",\"Neither satisfied nor dissatisfied\",\"Very satisfied\",,\n" +
                "\"12\",\"9/4/2024 13:35\",\"9/4/2024 13:37\",\"anonymous\",,\"9\",\"Very satisfied\",\"Very satisfied\",\"Very satisfied\",\"Very satisfied\",\"Very satisfied\",,\n" +
                "\"13\",\"9/4/2024 15:56\",\"9/4/2024 15:58\",\"anonymous\",,\"8\",\"Very satisfied\",\"Very satisfied\",\"Very satisfied\",\"Somewhat satisfied\",\"Somewhat satisfied\",\"Comprehensive skill coverage and practical application opportunities\",\"Need more information on advanced certification tracks and industry partnership programs\"\n" +
                "\"14\",\"9/4/2024 16:07\",\"9/4/2024 16:07\",\"anonymous\",,\"8\",\"Very satisfied\",\"Somewhat satisfied\",\"Somewhat satisfied\",\"Somewhat satisfied\",\"Very satisfied\",,";
        JSONObject scriptParams = userRequestToPresentationScriptParamsAgent.run(new UserRequestToPresentationScriptParamsAgent.Params(
                userRequest + "\n\n" + applicationContext,
                null // Assuming no files are needed for this test
        ));
        String javascript = dataToPresentationScriptGeneratorAgent.run(new DataToPresentationScriptGeneratorAgent.DataToPresentationParams(
                        scriptParams.optString("task") + userRequest + "\n\n" + applicationContext,
                        scriptParams.optString("additionalRequirements"),
                        null // Assuming no files are needed for this test
                )
        );

        // Step 1: Configure the JSPresentationMakerBridge
        JSONObject config = new JSONObject();
        config.put("clientName", "CsvAnalysisTest");
        // We pass the resource path, JSPresentationMakerBridge knows how to load it from the classpath
        config.put("jsScript", javascript);

        // Step 2: Instantiate the bridge
        JSPresentationMakerBridge presentationMaker = new JSPresentationMakerBridge(config);

        // Step 3: Execute the script and generate the presentation file
        // The script is self-contained, so we pass an empty JSON for params.
        File presentationFile = presentationMaker.createPresentationFile("{}", "CSV Feedback Analysis");
    }


}