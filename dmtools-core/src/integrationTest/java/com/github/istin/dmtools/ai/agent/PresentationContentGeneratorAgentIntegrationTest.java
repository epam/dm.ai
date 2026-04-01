// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.agent;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class PresentationContentGeneratorAgentIntegrationTest {

    @Test
    public void testGenerateContentFromSalesData() throws Exception {
        // Arrange
        PresentationContentGeneratorAgent agent = new PresentationContentGeneratorAgent();
        PresentationContentGeneratorAgent.Params params = new PresentationContentGeneratorAgent.Params(
                "Q4 Sales Performance",
                "Executive Team",
                "Create a presentation showing our Q4 sales performance with focus on regional breakdown and product categories. Include the year-over-year comparison.",
                "Regional Sales Q4 2023:\n" +
                        "- North America: $2.4M (up 12% YoY)\n" +
                        "- Europe: $1.8M (up 8% YoY)\n" +
                        "- Asia: $1.2M (up 15% YoY)\n" +
                        "- Rest of World: $0.6M (down 3% YoY)\n" +
                        "\n" +
                        "Product Category Sales Q4 2023:\n" +
                        "- Electronics: $3.1M (42% of total)\n" +
                        "- Home Goods: $1.5M (25% of total)\n" +
                        "- Apparel: $0.9M (15% of total)\n" +
                        "- Other: $0.5M (18% of total)\n" +
                        "\n" +
                        "Key Achievements:\n" +
                        "- Launched 3 new product lines in Electronics\n" +
                        "- Expanded to 2 new European markets\n" +
                        "- Reduced shipping costs by 7%"
        );

        // Act
        JSONArray slidesContent = agent.run(params);

        // Assert
        assertNotNull("Slides content should not be null", slidesContent);
        assertTrue("Should generate appropriate number of slides", slidesContent.length() >= 4);

        // Check that each slide has the required properties
        for (int i = 0; i < slidesContent.length(); i++) {
            JSONObject slide = slidesContent.getJSONObject(i);
            assertTrue("Slide should have a title", slide.has("title"));
            assertTrue("Slide should have content", slide.has("content"));
            assertTrue("Slide should have a slideType", slide.has("slideType"));
        }

        // Check for specific content based on the provided data
        boolean hasRegionalBreakdown = false;
        boolean hasProductCategories = false;
        boolean hasYoyComparison = false;
        boolean hasKeyAchievements = false;

        for (int i = 0; i < slidesContent.length(); i++) {
            JSONObject slide = slidesContent.getJSONObject(i);
            String title = slide.getString("title").toLowerCase();
            String content = slide.getString("content").toLowerCase();

            if (title.contains("regional") || content.contains("north america") ||
                    content.contains("europe") || content.contains("asia")) {
                hasRegionalBreakdown = true;

                // If this is a data visualization slide, verify the data matches input
                if (slide.getString("slideType").contains("chart") && slide.has("data")) {
                    JSONObject data = slide.getJSONObject("data");
                    if (data.has("labels")) {
                        JSONArray labels = data.getJSONArray("labels");
                        boolean hasNorthAmerica = false;
                        boolean hasEurope = false;
                        boolean hasAsia = false;

                        for (int j = 0; j < labels.length(); j++) {
                            String label = labels.getString(j).toLowerCase();
                            if (label.contains("north america")) hasNorthAmerica = true;
                            if (label.contains("europe")) hasEurope = true;
                            if (label.contains("asia")) hasAsia = true;
                        }

                        assertTrue("Regional data should include North America", hasNorthAmerica);
                        assertTrue("Regional data should include Europe", hasEurope);
                        assertTrue("Regional data should include Asia", hasAsia);
                    }
                }
            }

            if (title.contains("product") || content.contains("electronics") ||
                    content.contains("home goods") || content.contains("apparel")) {
                hasProductCategories = true;

                // Verify product category data if present
                if (slide.getString("slideType").contains("chart") && slide.has("data")) {
                    JSONObject data = slide.getJSONObject("data");
                    if (data.has("labels")) {
                        JSONArray labels = data.getJSONArray("labels");
                        boolean hasElectronics = false;
                        boolean hasHomeGoods = false;

                        for (int j = 0; j < labels.length(); j++) {
                            String label = labels.getString(j).toLowerCase();
                            if (label.contains("electronics")) hasElectronics = true;
                            if (label.contains("home goods") || label.contains("home")) hasHomeGoods = true;
                        }

                        assertTrue("Product data should include Electronics", hasElectronics);
                        assertTrue("Product data should include Home Goods", hasHomeGoods);
                    }
                }
            }

            if (content.contains("yoy") || content.contains("year") || content.contains("12%") ||
                    content.contains("8%") || content.contains("15%") || content.contains("-3%") ||
                    content.contains("down 3%")) {
                hasYoyComparison = true;
            }

            if (title.contains("achievement") || content.contains("new product lines") ||
                    content.contains("european markets") || content.contains("shipping costs")) {
                hasKeyAchievements = true;
            }
        }

        assertTrue("Should include regional breakdown", hasRegionalBreakdown);
        assertTrue("Should include product categories", hasProductCategories);
        assertTrue("Should include year-over-year comparison", hasYoyComparison);
        assertTrue("Should include key achievements", hasKeyAchievements);
    }

    @Test
    public void testGenerateContentFromSustainabilityData() throws Exception {
        // Arrange
        PresentationContentGeneratorAgent agent = new PresentationContentGeneratorAgent();
        PresentationContentGeneratorAgent.Params params = new PresentationContentGeneratorAgent.Params(
                "Annual Sustainability Report",
                "Shareholders",
                "Create a presentation for our annual sustainability report focusing on our carbon footprint reduction, water conservation efforts, and community initiatives.",
                "Carbon Footprint:\n" +
                        "- 2023 Total Emissions: 45,000 metric tons CO2e (15% reduction from 2022)\n" +
                        "- Scope 1 emissions: 12,000 metric tons (direct)\n" +
                        "- Scope 2 emissions: 28,000 metric tons (indirect)\n" +
                        "- Scope 3 emissions: 5,000 metric tons (supply chain)\n" +
                        "\n" +
                        "Water Conservation:\n" +
                        "- Total water usage: 1.2 million gallons (down 8% from 2022)\n" +
                        "- Water recycling: 350,000 gallons (29% of total usage)\n" +
                        "- Implemented 5 new water-saving technologies in manufacturing\n" +
                        "\n" +
                        "Community Initiatives:\n" +
                        "- $1.2M invested in local community projects\n" +
                        "- 3,500 employee volunteer hours\n" +
                        "- Supported 12 environmental education programs in local schools\n" +
                        "- Planted 15,000 trees in partnership with reforestation projects"
        );

        // Act
        JSONArray slidesContent = agent.run(params);

        // Assert
        assertNotNull("Slides content should not be null", slidesContent);
        assertTrue("Should generate appropriate number of slides", slidesContent.length() >= 5);

        // Check for specific content based on the provided data
        boolean hasCarbonFootprint = false;
        boolean hasWaterConservation = false;
        boolean hasCommunityInitiatives = false;
        boolean hasEmissionsData = false;
        boolean hasWaterUsageData = false;

        for (int i = 0; i < slidesContent.length(); i++) {
            JSONObject slide = slidesContent.getJSONObject(i);
            String title = slide.getString("title").toLowerCase();
            String content = slide.getString("content").toLowerCase();

            if (title.contains("carbon") || title.contains("emissions") ||
                    content.contains("carbon") || content.contains("emissions") ||
                    content.contains("co2")) {
                hasCarbonFootprint = true;

                // Check for specific emissions data
                if (content.contains("45,000") || content.contains("15%") ||
                        content.contains("scope 1") || content.contains("scope 2") ||
                        content.contains("scope 3")) {
                    hasEmissionsData = true;
                }
            }

            if (title.contains("water") || content.contains("water") ||
                    content.contains("1.2 million") || content.contains("350,000")) {
                hasWaterConservation = true;

                // Check for specific water usage data
                if (content.contains("1.2 million") || content.contains("8%") ||
                        content.contains("350,000") || content.contains("29%") ||
                        content.contains("recycling")) {
                    hasWaterUsageData = true;
                }
            }

            if (title.contains("community") || content.contains("community") ||
                    content.contains("$1.2m") || content.contains("volunteer") ||
                    content.contains("education") || content.contains("trees")) {
                hasCommunityInitiatives = true;
            }
        }

        assertTrue("Should include carbon footprint information", hasCarbonFootprint);
        assertTrue("Should include water conservation information", hasWaterConservation);
        assertTrue("Should include community initiatives information", hasCommunityInitiatives);
        assertTrue("Should include specific emissions data", hasEmissionsData);
        assertTrue("Should include specific water usage data", hasWaterUsageData);
    }

    @Test
    public void testGenerateContentWithMinimalData() throws Exception {
        // Arrange
        PresentationContentGeneratorAgent agent = new PresentationContentGeneratorAgent();
        PresentationContentGeneratorAgent.Params params = new PresentationContentGeneratorAgent.Params(
                "Project Status Update",
                "Development Team",
                "Create a brief status update on the Alpha project focusing on completed tasks and next steps.",
                "Completed Tasks:\n" +
                        "- Database migration (100%)\n" +
                        "- API redesign (100%)\n" +
                        "- Frontend components (75%)\n" +
                        "\n" +
                        "Next Steps:\n" +
                        "- Complete remaining frontend components\n" +
                        "- Integration testing\n" +
                        "- User acceptance testing"
        );

        // Act
        JSONArray slidesContent = agent.run(params);

        // Assert
        assertNotNull("Slides content should not be null", slidesContent);
        assertTrue("Should generate appropriate number of slides", slidesContent.length() >= 2);

        // Check for specific content based on the provided data
        boolean hasCompletedTasks = false;
        boolean hasNextSteps = false;
        boolean hasAccurateProgress = false;

        for (int i = 0; i < slidesContent.length(); i++) {
            JSONObject slide = slidesContent.getJSONObject(i);
            String title = slide.getString("title").toLowerCase();
            String content = slide.getString("content").toLowerCase();

            if (title.contains("completed") || title.contains("progress") ||
                    content.contains("database migration") || content.contains("api redesign") ||
                    content.contains("frontend components")) {
                hasCompletedTasks = true;

                // Check for accurate progress reporting
                if (content.contains("100%") && content.contains("75%")) {
                    hasAccurateProgress = true;
                }
            }

            if (title.contains("next") || title.contains("upcoming") ||
                    content.contains("integration testing") || content.contains("user acceptance")) {
                hasNextSteps = true;
            }
        }

        assertTrue("Should include completed tasks", hasCompletedTasks);
        assertTrue("Should include next steps", hasNextSteps);
        assertTrue("Should include accurate progress percentages", hasAccurateProgress);

        // Verify no fabricated data
        boolean hasFabricatedData = false;

        for (int i = 0; i < slidesContent.length(); i++) {
            JSONObject slide = slidesContent.getJSONObject(i);
            String content = slide.getString("content").toLowerCase();

            // Check for data that wasn't in the input
            if (content.contains("budget") || content.contains("timeline") ||
                    content.contains("resources") || content.contains("risk")) {
                // These topics weren't mentioned in the input data
                hasFabricatedData = true;
                break;
            }
        }

        assertFalse("Should not include fabricated data", hasFabricatedData);
    }
}