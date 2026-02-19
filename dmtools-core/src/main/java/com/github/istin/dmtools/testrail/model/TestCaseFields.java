package com.github.istin.dmtools.testrail.model;

import com.github.istin.dmtools.common.model.JSONModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * TestRail test case fields wrapper.
 * Maps TestRail API fields to structured Java objects.
 */
public class TestCaseFields extends JSONModel {

    public static final String ID = "id";
    public static final String TITLE = "title";
    public static final String SECTION_ID = "section_id";
    public static final String TEMPLATE_ID = "template_id";
    public static final String TYPE_ID = "type_id";
    public static final String PRIORITY_ID = "priority_id";
    public static final String MILESTONE_ID = "milestone_id";
    public static final String REFS = "refs";
    public static final String CREATED_BY = "created_by";
    public static final String CREATED_ON = "created_on";
    public static final String UPDATED_BY = "updated_by";
    public static final String UPDATED_ON = "updated_on";
    public static final String ESTIMATE = "estimate";
    public static final String SUITE_ID = "suite_id";
    public static final String DISPLAY_ORDER = "display_order";

    // Custom fields
    public static final String CUSTOM_PRECONDS = "custom_preconds";
    public static final String CUSTOM_STEPS = "custom_steps";
    public static final String CUSTOM_EXPECTED = "custom_expected";
    public static final String CUSTOM_STEPS_SEPARATED = "custom_steps_separated";
    public static final String CUSTOM_MISSION = "custom_mission";
    public static final String CUSTOM_GOALS = "custom_goals";

    public TestCaseFields() {
        super();
    }

    public TestCaseFields(String json) throws JSONException {
        super(json);
    }

    public TestCaseFields(JSONObject json) {
        super(json);
    }

    public Integer getId() {
        return getInt(ID);
    }

    public String getTitle() {
        return getString(TITLE);
    }

    public Integer getSectionId() {
        return getInt(SECTION_ID);
    }

    public Integer getTemplateId() {
        return getInt(TEMPLATE_ID);
    }

    public Integer getTypeId() {
        return getInt(TYPE_ID);
    }

    public Integer getPriorityId() {
        return getInt(PRIORITY_ID);
    }

    public Integer getMilestoneId() {
        return getInt(MILESTONE_ID);
    }

    public String getRefs() {
        return getString(REFS);
    }

    public Integer getCreatedBy() {
        return getInt(CREATED_BY);
    }

    public Date getCreatedOn() {
        Long timestamp = getLong(CREATED_ON);
        if (timestamp != null) {
            return new Date(timestamp * 1000L); // TestRail uses seconds
        }
        return null;
    }

    public Integer getUpdatedBy() {
        return getInt(UPDATED_BY);
    }

    public Date getUpdatedOn() {
        Long timestamp = getLong(UPDATED_ON);
        if (timestamp != null) {
            return new Date(timestamp * 1000L); // TestRail uses seconds
        }
        return null;
    }

    public Long getUpdatedAsMillis() {
        Long timestamp = getLong(UPDATED_ON);
        if (timestamp != null) {
            return timestamp * 1000L;
        }
        return null;
    }

    public String getEstimate() {
        return getString(ESTIMATE);
    }

    public Integer getSuiteId() {
        return getInt(SUITE_ID);
    }

    public Integer getDisplayOrder() {
        return getInt(DISPLAY_ORDER);
    }

    // Custom fields
    public String getCustomPreconds() {
        return getString(CUSTOM_PRECONDS);
    }

    public String getCustomSteps() {
        return getString(CUSTOM_STEPS);
    }

    public String getCustomExpected() {
        return getString(CUSTOM_EXPECTED);
    }

    public List<TestStep> getCustomStepsSeparated() {
        JSONArray stepsArray = getJSONArray(CUSTOM_STEPS_SEPARATED);
        if (stepsArray == null) {
            return null;
        }

        List<TestStep> steps = new ArrayList<>();
        for (int i = 0; i < stepsArray.length(); i++) {
            JSONObject stepObj = stepsArray.optJSONObject(i);
            if (stepObj != null) {
                steps.add(new TestStep(stepObj));
            }
        }
        return steps;
    }

    public String getCustomMission() {
        return getString(CUSTOM_MISSION);
    }

    public String getCustomGoals() {
        return getString(CUSTOM_GOALS);
    }

    /**
     * Get description combining all text fields.
     * TestRail doesn't have a single "description" field.
     */
    public String getDescription() {
        StringBuilder description = new StringBuilder();

        String preconds = getCustomPreconds();
        if (preconds != null && !preconds.isEmpty()) {
            description.append("Preconditions:\n").append(preconds).append("\n\n");
        }

        String steps = getCustomSteps();
        if (steps != null && !steps.isEmpty()) {
            description.append("Steps:\n").append(steps).append("\n\n");
        }

        List<TestStep> stepsSeparated = getCustomStepsSeparated();
        if (stepsSeparated != null && !stepsSeparated.isEmpty()) {
            description.append("Steps:\n");
            for (int i = 0; i < stepsSeparated.size(); i++) {
                TestStep step = stepsSeparated.get(i);
                description.append((i + 1)).append(". ").append(step.getContent()).append("\n");
                if (step.getExpected() != null) {
                    description.append("   Expected: ").append(step.getExpected()).append("\n");
                }
            }
            description.append("\n");
        }

        String expected = getCustomExpected();
        if (expected != null && !expected.isEmpty()) {
            description.append("Expected Result:\n").append(expected).append("\n\n");
        }

        String mission = getCustomMission();
        if (mission != null && !mission.isEmpty()) {
            description.append("Mission:\n").append(mission).append("\n\n");
        }

        String goals = getCustomGoals();
        if (goals != null && !goals.isEmpty()) {
            description.append("Goals:\n").append(goals).append("\n");
        }

        return description.toString().trim();
    }

    /**
     * TestRail test step model for custom_steps_separated field.
     */
    public static class TestStep extends JSONModel {

        public TestStep() {
            super();
        }

        public TestStep(JSONObject json) {
            super(json);
        }

        public String getContent() {
            return getString("content");
        }

        public String getExpected() {
            return getString("expected");
        }

        public void setContent(String content) {
            set("content", content);
        }

        public void setExpected(String expected) {
            set("expected", expected);
        }
    }
}
