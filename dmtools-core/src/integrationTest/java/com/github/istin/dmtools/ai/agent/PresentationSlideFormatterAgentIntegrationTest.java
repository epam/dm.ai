package com.github.istin.dmtools.ai.agent;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

public class PresentationSlideFormatterAgentIntegrationTest {

    @Test
    public void testFormatContentSlide() throws Exception {
        // Arrange
        PresentationSlideFormatterAgent agent = new PresentationSlideFormatterAgent();

        JSONObject slideContent = new JSONObject();
        slideContent.put("title", "Key Business Strategies");
        slideContent.put("content", "Effective business strategies include market penetration, market development, product development, and diversification. Each strategy has specific applications and risk profiles.");
        slideContent.put("slideType", "content");
        slideContent.put("notes", "Explain each strategy with examples slide needs to be 'content' type");

        PresentationSlideFormatterAgent.Params params = new PresentationSlideFormatterAgent.Params(new JSONArray().put(slideContent));

        // Act
        JSONArray formattedSlides = agent.run(params);

        // Assert
        assertNotNull("Formatted slides should not be null", formattedSlides);
        assertEquals("Should return exactly one slide", 1, formattedSlides.length());

        JSONObject formattedSlide = formattedSlides.getJSONObject(0);
        assertEquals("Slide type should be content", "content", formattedSlide.getString("type"));
        assertEquals("Slide title should match input", "Key Business Strategies", formattedSlide.getString("title"));
        assertTrue("Slide should have a subtitle", formattedSlide.has("subtitle"));

        // Check description object
        assertTrue("Slide should have a description", formattedSlide.has("description"));
        JSONObject description = formattedSlide.getJSONObject("description");
        assertTrue("Description should have a title", description.has("title"));
        assertTrue("Description should have text", description.has("text"));
        assertTrue("Description should have bullets", description.has("bullets"));

        // Check content formatting
        assertTrue("Slide should have content", formattedSlide.has("content"));
        String content = formattedSlide.getString("content");
        assertTrue("Content should use markdown formatting",
                content.contains("##") || content.contains("*") || content.contains("-") || content.contains("1."));
    }

    @Test
    public void testFormatBarChartSlide() throws Exception {
        // Arrange
        PresentationSlideFormatterAgent agent = new PresentationSlideFormatterAgent();

        JSONObject slideContent = new JSONObject();
        slideContent.put("title", "Quarterly Sales Performance");
        slideContent.put("content", "Analysis of quarterly sales performance across different product lines.");
        slideContent.put("slideType", "bar-chart");

        JSONObject data = new JSONObject();
        JSONArray labels = new JSONArray();
        labels.put("Q1").put("Q2").put("Q3").put("Q4");
        data.put("labels", labels);

        JSONArray datasets = new JSONArray();
        JSONObject dataset1 = new JSONObject();
        dataset1.put("label", "Product A");
        JSONArray dataValues1 = new JSONArray();
        dataValues1.put(120).put(150).put(180).put(210);
        dataset1.put("data", dataValues1);

        JSONObject dataset2 = new JSONObject();
        dataset2.put("label", "Product B");
        JSONArray dataValues2 = new JSONArray();
        dataValues2.put(85).put(100).put(130).put(155);
        dataset2.put("data", dataValues2);

        datasets.put(dataset1).put(dataset2);
        data.put("datasets", datasets);

        slideContent.put("data", data);
        slideContent.put("notes", "Highlight growth trend in Q3-Q4");

        PresentationSlideFormatterAgent.Params params = new PresentationSlideFormatterAgent.Params(new JSONArray().put(slideContent));

        // Act
        JSONArray formattedSlides = agent.run(params);

        // Assert
        assertNotNull("Formatted slides should not be null", formattedSlides);
        assertEquals("Should return exactly one slide", 1, formattedSlides.length());

        JSONObject formattedSlide = formattedSlides.getJSONObject(0);
        assertEquals("Slide type should be bar-chart", "bar-chart", formattedSlide.getString("type"));
        assertEquals("Slide title should match input", "Quarterly Sales Performance", formattedSlide.getString("title"));
        assertTrue("Slide should have a subtitle", formattedSlide.has("subtitle"));

        // Check description object
        assertTrue("Slide should have a description", formattedSlide.has("description"));

        // Check chart data
        assertTrue("Slide should have chartData", formattedSlide.has("chartData"));
        JSONObject chartData = formattedSlide.getJSONObject("chartData");
        assertTrue("Chart data should have labels", chartData.has("labels"));
        assertTrue("Chart data should have datasets", chartData.has("datasets"));

        JSONArray chartLabels = chartData.getJSONArray("labels");
        assertEquals("Chart should have 4 labels", 4, chartLabels.length());

        JSONArray chartDatasets = chartData.getJSONArray("datasets");
        assertEquals("Chart should have 2 datasets", 2, chartDatasets.length());

        // Check that colors were added
        JSONObject firstDataset = chartDatasets.getJSONObject(0);
        assertTrue("Dataset should have backgroundColor", firstDataset.has("backgroundColor"));
        assertTrue("Dataset should have borderColor", firstDataset.has("borderColor"));
    }

    @Test
    public void testFormatTableSlide() throws Exception {
        // Arrange
        PresentationSlideFormatterAgent agent = new PresentationSlideFormatterAgent();

        JSONObject slideContent = new JSONObject();
        slideContent.put("title", "Market Comparison");
        slideContent.put("content", "Comparison of key metrics across different market segments.");
        slideContent.put("slideType", "table");

        JSONObject data = new JSONObject();
        JSONArray headers = new JSONArray();
        headers.put("Segment").put("Market Size").put("Growth Rate").put("Profit Margin");
        data.put("headers", headers);

        JSONArray rows = new JSONArray();
        JSONArray row1 = new JSONArray();
        row1.put("Enterprise").put("$5.2B").put("12%").put("35%");
        JSONArray row2 = new JSONArray();
        row2.put("SMB").put("$2.8B").put("18%").put("28%");
        JSONArray row3 = new JSONArray();
        row3.put("Consumer").put("$8.4B").put("7%").put("22%");

        rows.put(row1).put(row2).put(row3);
        data.put("rows", rows);

        slideContent.put("data", data);
        slideContent.put("notes", "SMB shows highest growth potential as table");

        PresentationSlideFormatterAgent.Params params = new PresentationSlideFormatterAgent.Params(new JSONArray().put(slideContent));

        // Act
        JSONArray formattedSlides = agent.run(params);

        // Assert
        assertNotNull("Formatted slides should not be null", formattedSlides);
        assertEquals("Should return exactly one slide", 1, formattedSlides.length());

        JSONObject formattedSlide = formattedSlides.getJSONObject(0);
        assertEquals("Slide type should be table", "table", formattedSlide.getString("type"));
        assertEquals("Slide title should match input", "Market Comparison", formattedSlide.getString("title"));
        assertTrue("Slide should have a subtitle", formattedSlide.has("subtitle"));

        // Check description object
        assertTrue("Slide should have a description", formattedSlide.has("description"));

        // Check table data
        assertTrue("Slide should have tableData", formattedSlide.has("tableData"));
        JSONObject tableData = formattedSlide.getJSONObject("tableData");
        assertTrue("Table data should have headers", tableData.has("headers"));
        assertTrue("Table data should have rows", tableData.has("rows"));

        JSONArray tableHeaders = tableData.getJSONArray("headers");
        assertEquals("Table should have 4 headers", 4, tableHeaders.length());

        JSONArray tableRows = tableData.getJSONArray("rows");
        assertEquals("Table should have 3 rows", 3, tableRows.length());
    }

    @Test
    public void testFormatPieChartSlide() throws Exception {
        // Arrange
        PresentationSlideFormatterAgent agent = new PresentationSlideFormatterAgent();

        JSONObject slideContent = new JSONObject();
        slideContent.put("title", "Revenue Distribution");
        slideContent.put("content", "Breakdown of revenue sources for fiscal year 2023.");
        slideContent.put("slideType", "pie-chart");

        JSONObject data = new JSONObject();
        JSONArray labels = new JSONArray();
        labels.put("Product Sales").put("Services").put("Licensing").put("Other");
        data.put("labels", labels);

        JSONArray dataValues = new JSONArray();
        dataValues.put(65).put(20).put(10).put(5);
        data.put("data", dataValues);

        slideContent.put("data", data);
        slideContent.put("notes", "Product sales remain the dominant revenue source");

        PresentationSlideFormatterAgent.Params params = new PresentationSlideFormatterAgent.Params(new JSONArray().put(slideContent));

        // Act
        JSONArray formattedSlides = agent.run(params);

        // Assert
        assertNotNull("Formatted slides should not be null", formattedSlides);
        assertEquals("Should return exactly one slide", 1, formattedSlides.length());

        JSONObject formattedSlide = formattedSlides.getJSONObject(0);
        assertEquals("Slide type should be pie-chart", "pie-chart", formattedSlide.getString("type"));
        assertEquals("Slide title should match input", "Revenue Distribution", formattedSlide.getString("title"));
        assertTrue("Slide should have a subtitle", formattedSlide.has("subtitle"));

        // Check description object
        assertTrue("Slide should have a description", formattedSlide.has("description"));

        // Check chart data
        assertTrue("Slide should have chartData", formattedSlide.has("chartData"));
        JSONObject chartData = formattedSlide.getJSONObject("chartData");
        assertTrue("Chart data should have labels", chartData.has("labels"));
        assertTrue("Chart data should have data", chartData.has("data"));
        assertTrue("Chart data should have backgroundColor", chartData.has("backgroundColor"));

        JSONArray chartLabels = chartData.getJSONArray("labels");
        assertEquals("Chart should have 4 labels", 4, chartLabels.length());

        JSONArray chartDataValues = chartData.getJSONArray("data");
        assertEquals("Chart should have 4 data values", 4, chartDataValues.length());

        // Check for additional pie chart elements
        assertTrue("Pie chart should have insights", formattedSlide.has("insights"));
        assertTrue("Pie chart should have metrics", formattedSlide.has("metrics"));

        JSONArray metrics = formattedSlide.getJSONArray("metrics");
        assertTrue("Should have at least one metric", metrics.length() > 0);

        JSONObject firstMetric = metrics.getJSONObject(0);
        assertTrue("Metric should have value", firstMetric.has("value"));
        assertTrue("Metric should have label", firstMetric.has("label"));

        // Verify pie chart data sums to approximately 100%
        int sum = 0;
        for (int i = 0; i < chartDataValues.length(); i++) {
            sum += chartDataValues.getInt(i);
        }
        assertTrue("Pie chart data should sum to approximately 100%", Math.abs(sum - 100) <= 5);
    }

    @Test
    public void testHandleUnknownSlideType() throws Exception {
        // Arrange
        PresentationSlideFormatterAgent agent = new PresentationSlideFormatterAgent();

        JSONObject slideContent = new JSONObject();
        slideContent.put("title", "Miscellaneous Information");
        slideContent.put("content", "Various information that doesn't fit a specific slide type.");
        slideContent.put("slideType", "unknown"); // Intentionally using an unknown type
        slideContent.put("notes", "Format appropriately. Needs to have title and subtitle");

        PresentationSlideFormatterAgent.Params params = new PresentationSlideFormatterAgent.Params(new JSONArray().put(slideContent));

        // Act
        JSONArray formattedSlides = agent.run(params);

        // Assert
        assertNotNull("Formatted slides should not be null", formattedSlides);
        assertEquals("Should return exactly one slide", 1, formattedSlides.length());

        JSONObject formattedSlide = formattedSlides.getJSONObject(0);
        assertTrue("Slide should have a type", formattedSlide.has("type"));

        // The agent should default to a content slide for unknown types
        String slideType = formattedSlide.getString("type");
        assertTrue("Unknown slide type should be converted to a known type",
                slideType.equals("content") || slideType.equals("title") ||
                        slideType.equals("table") || slideType.equals("bar-chart") ||
                        slideType.equals("pie-chart") || slideType.equals("image"));

        assertEquals("Slide title should match input", "Miscellaneous Information", formattedSlide.getString("title"));
        assertTrue("Slide should have a subtitle", formattedSlide.has("subtitle"));
    }

    @Test
    public void testMultipleSlides() throws Exception {
        // Arrange
        PresentationSlideFormatterAgent agent = new PresentationSlideFormatterAgent();

        // Create an array of slide content
        JSONArray slidesContent = new JSONArray();

        // First slide - title slide
        JSONObject slide1 = new JSONObject();
        slide1.put("title", "Annual Report 2023");
        slide1.put("content", "Financial and operational overview");
        slide1.put("slideType", "title");
        slide1.put("presenter", "Jane Doe");
        slide1.put("presenterTitle", "CEO");
        slidesContent.put(slide1);

        // Second slide - content slide
        JSONObject slide2 = new JSONObject();
        slide2.put("title", "Key Highlights");
        slide2.put("content", "Revenue growth of 25%, Market expansion to 3 new countries, Launch of 2 new product lines");
        slide2.put("slideType", "content");
        slidesContent.put(slide2);

        PresentationSlideFormatterAgent.Params params = new PresentationSlideFormatterAgent.Params(slidesContent);

        // Act
        JSONArray formattedSlides = agent.run(params);

        // Assert
        assertNotNull("Formatted slides should not be null", formattedSlides);
        assertEquals("Should return exactly two slides", 2, formattedSlides.length());

        // Check first slide
        JSONObject formattedSlide1 = formattedSlides.getJSONObject(0);
        assertEquals("First slide type should be title", "title", formattedSlide1.getString("type"));
        assertEquals("First slide title should match input", "Annual Report 2023", formattedSlide1.getString("title"));
        assertTrue("First slide should have presenter info", formattedSlide1.has("presenter"));
        assertEquals("Presenter name should match input", "Jane Doe", formattedSlide1.getString("presenter"));

        // Check second slide
        JSONObject formattedSlide2 = formattedSlides.getJSONObject(1);
        assertEquals("Second slide type should be content", "content", formattedSlide2.getString("type"));
        assertEquals("Second slide title should match input", "Key Highlights", formattedSlide2.getString("title"));
        assertTrue("Second slide should have content", formattedSlide2.has("content"));
    }

    @Test
    public void testPieChartPercentages() throws Exception {
        // Arrange
        PresentationSlideFormatterAgent agent = new PresentationSlideFormatterAgent();

        JSONObject slideContent = new JSONObject();
        slideContent.put("title", "Market Share");
        slideContent.put("content", "Distribution of market share among competitors");
        slideContent.put("slideType", "pie-chart");

        JSONObject data = new JSONObject();
        JSONArray labels = new JSONArray();
        labels.put("Company A").put("Company B").put("Company C").put("Others");
        data.put("labels", labels);

        // Intentionally using values that don't sum to 100
        JSONArray dataValues = new JSONArray();
        dataValues.put(42).put(28).put(15).put(10);
        data.put("data", dataValues);

        slideContent.put("data", data);

        PresentationSlideFormatterAgent.Params params = new PresentationSlideFormatterAgent.Params(new JSONArray().put(slideContent));

        // Act
        JSONArray formattedSlides = agent.run(params);

        // Assert
        assertNotNull("Formatted slides should not be null", formattedSlides);
        assertEquals("Should return exactly one slide", 1, formattedSlides.length());

        JSONObject formattedSlide = formattedSlides.getJSONObject(0);
        assertEquals("Slide type should be pie-chart", "pie-chart", formattedSlide.getString("type"));

        JSONObject chartData = formattedSlide.getJSONObject("chartData");
        JSONArray chartDataValues = chartData.getJSONArray("data");

        // Verify pie chart data sums to approximately 100%
        int sum = 0;
        for (int i = 0; i < chartDataValues.length(); i++) {
            sum += chartDataValues.getInt(i);
        }
        assertTrue("Pie chart data should sum to approximately 100%", Math.abs(sum - 100) <= 5);
    }
}