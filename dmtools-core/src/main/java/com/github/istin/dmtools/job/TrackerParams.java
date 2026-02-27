package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.model.Metadata;
import com.google.gson.annotations.SerializedName;
import lombok.*;

import static com.github.istin.dmtools.job.Params.POST_ACTION;

@Getter
@Setter
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrackerParams implements CliPostComment {

    public static final String INPUT_JQL = "inputJql";
    public static final String INITIATOR = "initiator";
    public static final String TARGET_PROJECT = "targetProject";

    @SerializedName(INPUT_JQL)
    private String inputJql;

    @SerializedName(INITIATOR)
    private String initiator;

    @SerializedName(TARGET_PROJECT)
    private String targetProject;

    public static final String METADATA = "metadata";
    public static final String OPERATION_TYPE = "operationType";
    public static final String OUTPUT_TYPE = "outputType";
    public static final String FIELD_NAME = "fieldName";
    public static final String ATTACH_RESPONSE_AS_FILE = "attachResponseAsFile";

    @SerializedName(METADATA)
    private Metadata metadata;

    @SerializedName(ATTACH_RESPONSE_AS_FILE)
    private boolean attachResponseAsFile = false;

    public static final String TICKET_CONTEXT_DEPTH = "ticketContextDepth";
    public static final String CHUNKS_PROCESSING_TIMEOUT_IN_MINUTES = "chunksProcessingTimeout";

    public static final String PRE_ACTION = "preJSAction";

    @SerializedName(PRE_ACTION)
    private String preJSAction;

    @SerializedName(POST_ACTION)
    private String postJSAction;

    public enum OutputType {
        comment, field, creation, none
    }

    public enum OperationType {
        Replace, Append
    }

    @SerializedName(FIELD_NAME)
    private String fieldName;

    @SerializedName(OUTPUT_TYPE)
    private OutputType outputType = OutputType.comment;

    /**
     * Sets the output type.
     */
    public void setOutputType(OutputType outputType) {
        this.outputType = outputType;
    }

    /**
     * Gets the output type.
     */
    public OutputType getOutputType() {
        return outputType;
    }

    @SerializedName(OPERATION_TYPE)
    private OperationType operationType = OperationType.Append;

    @SerializedName(TICKET_CONTEXT_DEPTH)
    private int ticketContextDepth = 1;

    @SerializedName(CHUNKS_PROCESSING_TIMEOUT_IN_MINUTES)
    private long chunkProcessingTimeoutInMinutes = 0;

    public static final String CUSTOM_PARAMS = "customParams";

    @SerializedName(CUSTOM_PARAMS)
    private java.util.Map<String, Object> customParams;

    public static final String CI_RUN_URL = "ciRunUrl";

    @SerializedName(CI_RUN_URL)
    private String ciRunUrl;

    public static final String ALWAYS_POST_COMMENTS = "alwaysPostComments";

    @SerializedName(ALWAYS_POST_COMMENTS)
    private Boolean alwaysPostComments;

}
