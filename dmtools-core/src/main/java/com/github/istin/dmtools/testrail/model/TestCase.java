package com.github.istin.dmtools.testrail.model;

import com.github.istin.dmtools.atlassian.jira.model.Fields;
import com.github.istin.dmtools.atlassian.jira.model.Resolution;
import com.github.istin.dmtools.common.model.IAttachment;
import com.github.istin.dmtools.common.model.ITicket;
import com.github.istin.dmtools.common.model.IUser;
import com.github.istin.dmtools.common.model.JSONModel;
import com.github.istin.dmtools.common.timeline.ReportIteration;
import com.github.istin.dmtools.common.tracker.model.Status;
import com.github.istin.dmtools.common.utils.LLMOptimizedJson;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * TestRail test case model implementing ITicket interface.
 * Provides compatibility with dmtools tracking system.
 */
public class TestCase extends JSONModel implements ITicket {

    private static final Set<String> BLACKLISTED_FIELDS = Set.of(
            "created_by", "updated_by", "display_order", "suite_id"
    );

    private final String basePath;
    private String llmFormatted;

    public TestCase(String basePath) {
        super();
        this.basePath = basePath;
    }

    public TestCase(String basePath, String json) throws JSONException {
        super(json);
        this.basePath = basePath;
    }

    public TestCase(String basePath, JSONObject json) {
        super(json);
        this.basePath = basePath;
    }

    @Override
    public String getKey() {
        Integer id = getInt("id");
        return id != null ? "C" + id : null;
    }

    @Override
    public String getTicketKey() {
        return getKey();
    }

    @Override
    public String getStatus() throws IOException {
        // TestRail test cases don't have a status field
        // They have results that have statuses
        // For now, return "Active" for all cases
        return "Active";
    }

    @Override
    public Status getStatusModel() throws IOException {
        return new Status(new JSONObject()
                .put("name", "Active")
                .put("statusCategory", new JSONObject().put("key", "active")));
    }

    @Override
    public String getIssueType() throws IOException {
        return "Test Case";
    }

    @Override
    public String getPriority() throws IOException {
        Integer priorityId = getInt("priority_id");
        if (priorityId == null) {
            return null;
        }
        // TestRail priority mapping: 1=Low, 2=Medium, 3=High, 4=Critical
        switch (priorityId) {
            case 1: return "Low";
            case 2: return "Medium";
            case 3: return "High";
            case 4: return "Critical";
            default: return "Medium";
        }
    }

    @Override
    public TicketPriority getPriorityAsEnum() {
        try {
            return TicketPriority.byName(getPriority());
        } catch (IOException e) {
            return TicketPriority.NotSet;
        }
    }

    @Override
    public String getTicketTitle() throws IOException {
        return getString("title");
    }

    @Override
    public String getTicketDescription() {
        return getTestCaseFields().getDescription();
    }

    @Override
    public String getTicketDependenciesDescription() {
        return null;
    }

    @Override
    public String getTicketLink() {
        if (basePath == null) {
            return null;
        }
        Integer id = getInt("id");
        if (id == null) {
            return null;
        }
        // TestRail URL format: https://example.testrail.com/index.php?/cases/view/{id}
        return basePath + "/index.php?/cases/view/" + id;
    }

    @Override
    public Date getCreated() {
        return getTestCaseFields().getCreatedOn();
    }

    @Override
    public JSONObject getFieldsAsJSON() {
        return getJSONObject();
    }

    @Override
    public Long getUpdatedAsMillis() {
        return getTestCaseFields().getUpdatedAsMillis();
    }

    @Override
    public IUser getCreator() {
        if (!getJSONObject().has("created_by")) {
            return null;
        }
        int createdBy = getInt("created_by");
        return new TestRailUser(createdBy);
    }

    @Override
    public Resolution getResolution() {
        return null;
    }

    @Override
    public JSONArray getTicketLabels() {
        // TestRail labels are objects with {id, title, created_by, created_on}
        JSONArray labelsArray = getJSONArray("labels");
        if (labelsArray == null || labelsArray.length() == 0) {
            return new JSONArray();
        }
        // Return array of label title strings for compatibility with ITicket interface
        JSONArray labelTitles = new JSONArray();
        for (int i = 0; i < labelsArray.length(); i++) {
            JSONObject labelObj = labelsArray.optJSONObject(i);
            if (labelObj != null && labelObj.has("title")) {
                labelTitles.put(labelObj.getString("title"));
            }
        }
        return labelTitles;
    }

    @Override
    public Fields getFields() {
        // TestRail doesn't use Jira Fields model
        // Return null to force use of getFieldsAsJSON()
        return null;
    }

    public TestCaseFields getTestCaseFields() {
        return new TestCaseFields(getJSONObject());
    }

    @Override
    public ReportIteration getIteration() {
        Integer milestoneId = getInt("milestone_id");
        if (milestoneId == null) {
            return null;
        }
        // Would need to fetch milestone details from API
        return null;
    }

    @Override
    public List<? extends ReportIteration> getIterations() {
        return List.of();
    }

    @Override
    public double getProgress() throws IOException {
        // Test cases don't have progress
        return 0;
    }

    @Override
    public List<? extends IAttachment> getAttachments() {
        return List.of();
    }

    @Override
    public String toText() throws IOException {
        if (llmFormatted == null) {
            llmFormatted = LLMOptimizedJson.formatWellFormed(toString(), BLACKLISTED_FIELDS);
        }
        return llmFormatted;
    }

    @Override
    public double getWeight() {
        // Could use estimate field
        String estimate = getString("estimate");
        if (estimate != null && !estimate.isEmpty()) {
            try {
                return Double.parseDouble(estimate);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    @Override
    public String getFieldValueAsString(String fieldName) {
        return getString(fieldName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestCase)) return false;
        TestCase testCase = (TestCase) o;
        return getKey().equals(testCase.getKey());
    }

    @Override
    public int hashCode() {
        return getKey().hashCode();
    }

    @Override
    public String toString() {
        return getJSONObject().toString();
    }

    /**
     * Simple TestRail user model.
     */
    private static class TestRailUser implements IUser {
        private final Integer userId;

        public TestRailUser(Integer userId) {
            this.userId = userId;
        }

        @Override
        public String getID() {
            return String.valueOf(userId);
        }

        @Override
        public String getFullName() {
            return "User " + userId;
        }

        @Override
        public String getEmailAddress() {
            return null; // TestRail user ID doesn't provide email directly
        }
    }
}
