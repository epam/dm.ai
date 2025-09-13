package com.github.istin.dmtools.job;

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
public class Params extends TrackerParams {

    public static final String CONFLUENCE_PAGES = "confluencePages";
    public static final String IS_CODE_AS_SOURCE = "isCodeAsSource";
    public static final String IS_CONFLUENCE_AS_SOURCE = "isConfluenceAsSource";
    public static final String IS_TRACKER_AS_SOURCE = "isTrackerAsSource";
    public static final String TRANSFORM_CONFLUENCE_PAGES_TO_MARKDOWN = "transformConfluencePagesToMarkdown";

    public static final String FILES_LIMIT = "filesLimit";
    public static final String CONFLUENCE_LIMIT = "confluenceLimit";
    public static final String TRACKER_LIMIT = "trackerLimit";

    public static final String FILES_ITERATIONS = "filesIterations";
    public static final String CONFLUENCE_ITERATIONS = "confluenceIterations";
    public static final String TRACKER_ITERATIONS = "trackerIterations";
    public static final String REQUEST_DECOMPOSITION_CHUNK_PROCESSING = "requestDecompositionChunkProcessing";
    public static final String POST_ACTION = "postJSAction";

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

    @SerializedName(FILES_LIMIT)
    private int filesLimit = 10;

    @SerializedName(CONFLUENCE_LIMIT)
    private int confluenceLimit = 10;

    @SerializedName(TRACKER_LIMIT)
    private int trackerLimit = 10;

    @SerializedName(FILES_ITERATIONS)
    private int filesIterations = 1;

    @SerializedName(CONFLUENCE_ITERATIONS)
    private int confluenceIterations = 1;

    @SerializedName(TRACKER_ITERATIONS)
    private int trackerIterations = 1;


    @SerializedName(SourceCodeConfig._KEY)
    private SourceCodeConfig[] sourceCodeConfig;

    @SerializedName(TrackerConfig._KEY)
    private TrackerConfig[] trackerConfig;

    @SerializedName(AIPromptConfig._KEY)
    private AIPromptConfig aiPromptConfig;

    public void setSourceCodeConfigs(SourceCodeConfig... sourceCodeConfig) {
        this.sourceCodeConfig = sourceCodeConfig;
    }

    public void setTrackerConfigs(TrackerConfig... trackerConfig) {
        this.trackerConfig = trackerConfig;
    }

    public void setConfluencePages(String... confluencePages) {
        this.confluencePages = confluencePages;
    }

}