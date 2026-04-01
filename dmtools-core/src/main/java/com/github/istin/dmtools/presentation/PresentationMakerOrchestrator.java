// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.presentation;

import com.github.istin.dmtools.ai.agent.PresentationContentGeneratorAgent;
import com.github.istin.dmtools.ai.agent.PresentationSlideFormatterAgent;
import com.github.istin.dmtools.di.DaggerPresentationMakerOrchestratorComponent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.inject.Inject;
import java.util.List;

public class PresentationMakerOrchestrator {

    @AllArgsConstructor
    @Getter
    public static class RequestData {
        private String userRequest;
        private String additionalData;
    }

    @AllArgsConstructor
    @Getter
    public static class Params {
        private String topic;
        private String audience;
        private List<RequestData> requestDataList;
        private String presenterName;
        private String presenterTitle;
        private String summarySlideRequest;
    }

    @Inject
    PresentationContentGeneratorAgent contentGeneratorAgent;

    @Inject
    PresentationSlideFormatterAgent slideFormatterAgent;

    public PresentationMakerOrchestrator() {
        DaggerPresentationMakerOrchestratorComponent.create().inject(this);
    }

    public JSONObject createPresentation(Params params) throws Exception {
        // Create presentation structure
        JSONObject presentation = new JSONObject();
        presentation.put("title", params.getTopic());
        JSONArray formattedSlides = new JSONArray();

        // Add title slide
        JSONObject titleSlide = new JSONObject();
        titleSlide.put("type", "title");
        titleSlide.put("title", params.getTopic());
        titleSlide.put("subtitle", "Presentation for " + params.getAudience());
        titleSlide.put("presenter", params.getPresenterName());
        titleSlide.put("presenterTitle", params.getPresenterTitle());
        titleSlide.put("date", java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy").format(java.time.LocalDate.now()));
        formattedSlides.put(titleSlide);

        // Process each request data item and generate slides
        for (RequestData requestData : params.getRequestDataList()) {
            // Generate content for slides for this request data
            JSONArray slidesContent = contentGeneratorAgent.run(
                    new PresentationContentGeneratorAgent.Params(
                            params.getTopic(),
                            params.getAudience(),
                            requestData.getUserRequest(),
                            requestData.getAdditionalData()
                    )
            );
            try {
                formattedSlides.putAll(slideFormatterAgent.run(
                        new PresentationSlideFormatterAgent.Params(slidesContent)
                ));
            } catch (Exception e) {
                // Split slidesContent into two parts as it's too big for a single processing
                JSONArray firstHalf = new JSONArray();
                JSONArray secondHalf = new JSONArray();
                int halfSize = slidesContent.length() / 2;

                for (int i = 0; i < slidesContent.length(); i++) {
                    if (i < halfSize) {
                        firstHalf.put(slidesContent.get(i));
                    } else {
                        secondHalf.put(slidesContent.get(i));
                    }
                }

                // Process each half separately
                formattedSlides.putAll(slideFormatterAgent.run(
                        new PresentationSlideFormatterAgent.Params(firstHalf)
                ));
                formattedSlides.putAll(slideFormatterAgent.run(
                        new PresentationSlideFormatterAgent.Params(secondHalf)
                ));
            }
        }

        String summarySlideRequest = params.getSummarySlideRequest();
        if (summarySlideRequest != null && !summarySlideRequest.isEmpty()) {
            JSONArray slidesContent = contentGeneratorAgent.run(
                    new PresentationContentGeneratorAgent.Params(
                            params.getTopic(),
                            params.getAudience(),
                            summarySlideRequest,
                            formattedSlides.toString()
                    )
            );

            formattedSlides.putAll(slideFormatterAgent.run(
                    new PresentationSlideFormatterAgent.Params(slidesContent)
            ));
        }

        // Add conclusion slide if not already included
        boolean hasConclusion = false;
        for (int i = 0; i < formattedSlides.length(); i++) {
            if (formattedSlides.getJSONObject(i).optString("title", "").toLowerCase().contains("conclusion")) {
                hasConclusion = true;
                break;
            }
        }

        if (!hasConclusion) {
            JSONObject conclusionSlide = new JSONObject();
            conclusionSlide.put("type", "content");
            conclusionSlide.put("title", "Conclusion");
            conclusionSlide.put("subtitle", "Key Takeaways");

            JSONObject description = new JSONObject();
            description.put("title", "Summary");
            description.put("text", "Thank you for your attention");
            JSONArray bullets = new JSONArray();
            bullets.put("For more information, please contact us");
            description.put("bullets", bullets);

            conclusionSlide.put("description", description);
            conclusionSlide.put("content", "## Thank You\n\n* Questions?\n* Comments?\n* Feedback?");

            formattedSlides.put(conclusionSlide);
        }

        presentation.put("slides", formattedSlides);
        return presentation;
    }
}