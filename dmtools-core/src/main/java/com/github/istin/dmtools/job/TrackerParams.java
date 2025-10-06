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
public class TrackerParams {

    public static final String INPUT_JQL = "inputJql";
    public static final String INITIATOR = "initiator";

    @SerializedName(INPUT_JQL)
    private String inputJql;

    @SerializedName(INITIATOR)
    private String initiator;

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

    @SerializedName(OPERATION_TYPE)
    private OperationType operationType = OperationType.Append;

    @SerializedName(TICKET_CONTEXT_DEPTH)
    private int ticketContextDepth = 1;

    @SerializedName(CHUNKS_PROCESSING_TIMEOUT_IN_MINUTES)
    private long chunkProcessingTimeoutInMinutes = 0;
}
