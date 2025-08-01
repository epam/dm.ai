package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.model.Metadata;
import com.github.istin.dmtools.common.ai.config.AIPromptConfig;
import com.github.istin.dmtools.common.code.model.SourceCodeConfig;
import com.github.istin.dmtools.common.tracker.model.TrackerConfig;
import com.google.gson.annotations.SerializedName;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Params {

    public static final String INPUT_JQL = "inputJql";
    public static final String INITIATOR = "initiator";
    public static final String CONFLUENCE_PAGES = "confluencePages";
    public static final String IS_CODE_AS_SOURCE = "isCodeAsSource";
    public static final String IS_CONFLUENCE_AS_SOURCE = "isConfluenceAsSource";
    public static final String IS_TRACKER_AS_SOURCE = "isTrackerAsSource";
    public static final String TRANSFORM_CONFLUENCE_PAGES_TO_MARKDOWN = "transformConfluencePagesToMarkdown";
    public static final String TICKET_CONTEXT_DEPTH = "ticketContextDepth";
    public static final String CHUNKS_PROCESSING_TIMEOUT_IN_MINUTES = "chunksProcessingTimeout";
    public static final String FILES_LIMIT = "filesLimit";
    public static final String CONFLUENCE_LIMIT = "confluenceLimit";
    public static final String TRACKER_LIMIT = "trackerLimit";

    public static final String FILES_ITERATIONS = "filesIterations";
    public static final String CONFLUENCE_ITERATIONS = "confluenceIterations";
    public static final String TRACKER_ITERATIONS = "trackerIterations";

    public static final String METADATA = "metadata";
    public static final String OPERATION_TYPE = "operationType";
    public static final String OUTPUT_TYPE = "outputType";
    public static final String FIELD_NAME = "fieldName";
    public static final String ATTACH_RESPONSE_AS_FILE = "attachResponseAsFile";

    @SerializedName(INPUT_JQL)
    private String inputJql;

    @SerializedName(INITIATOR)
    private String initiator;

    @SerializedName(IS_CODE_AS_SOURCE)
    private boolean isCodeAsSource = false;

    @SerializedName(IS_CONFLUENCE_AS_SOURCE)
    private boolean isConfluenceAsSource = false;

    @SerializedName(IS_TRACKER_AS_SOURCE)
    private boolean isTrackerAsSource = false;
    @SerializedName(TRANSFORM_CONFLUENCE_PAGES_TO_MARKDOWN)
    private boolean transformConfluencePagesToMarkdown = true;

    @SerializedName(CONFLUENCE_PAGES)
    private String[] confluencePages;

    @SerializedName(TICKET_CONTEXT_DEPTH)
    private int ticketContextDepth = 1;

    @SerializedName(FILES_LIMIT)
    private int filesLimit = 10;

    @SerializedName(CONFLUENCE_LIMIT)
    private int confluenceLimit = 10;

    @SerializedName(TRACKER_LIMIT)
    private int trackerLimit = 10;

    @SerializedName(CHUNKS_PROCESSING_TIMEOUT_IN_MINUTES)
    private long chunkProcessingTimeoutInMinutes = 0;

    @SerializedName(FILES_ITERATIONS)
    private int filesIterations = 1;

    @SerializedName(CONFLUENCE_ITERATIONS)
    private int confluenceIterations = 1;

    @SerializedName(TRACKER_ITERATIONS)
    private int trackerIterations = 1;

    @SerializedName(METADATA)
    private Metadata metadata;

    @SerializedName(SourceCodeConfig._KEY)
    private SourceCodeConfig[] sourceCodeConfig;

    @SerializedName(TrackerConfig._KEY)
    private TrackerConfig[] trackerConfig;

    @SerializedName(AIPromptConfig._KEY)
    private AIPromptConfig aiPromptConfig;

    @SerializedName(ATTACH_RESPONSE_AS_FILE)
    private boolean attachResponseAsFile = true;

    public void setSourceCodeConfigs(SourceCodeConfig... sourceCodeConfig) {
        this.sourceCodeConfig = sourceCodeConfig;
    }

    public void setTrackerConfigs(TrackerConfig... trackerConfig) {
        this.trackerConfig = trackerConfig;
    }

    public void setAiPromptConfig(AIPromptConfig aiPromptConfig) {
        this.aiPromptConfig = aiPromptConfig;
    }

    public void setConfluencePages(String... confluencePages) {
        this.confluencePages = confluencePages;
    }

    public enum OutputType {
        comment, field, creation
    }

    public enum OperationType {
        Replace, Append
    }

    @SerializedName(FIELD_NAME)
    private String fieldName;

    @SerializedName(OUTPUT_TYPE)
    private OutputType outputType = OutputType.comment;

    @SerializedName(OPERATION_TYPE)
    private OperationType operationType = OperationType.Append;

}