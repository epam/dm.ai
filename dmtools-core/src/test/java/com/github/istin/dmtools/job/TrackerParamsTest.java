package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.model.Metadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrackerParamsTest {

    private TrackerParams trackerParams;

    @BeforeEach
    void setUp() {
        trackerParams = new TrackerParams();
    }

    @Test
    void testDefaultConstructor() {
        assertNotNull(trackerParams);
        assertNull(trackerParams.getInputJql());
        assertNull(trackerParams.getInitiator());
        assertEquals(TrackerParams.OutputType.comment, trackerParams.getOutputType());
        assertEquals(TrackerParams.OperationType.Append, trackerParams.getOperationType());
        assertEquals(1, trackerParams.getTicketContextDepth());
        assertEquals(0, trackerParams.getChunkProcessingTimeoutInMinutes());
        assertFalse(trackerParams.isAttachResponseAsFile());
        assertNull(trackerParams.getCiRunUrl());
    }

    @Test
    void testAllArgsConstructor() {
        Metadata metadata = new Metadata();
        java.util.Map<String, Object> customParams = new java.util.HashMap<>();
        customParams.put("key", "value");
        TrackerParams params = new TrackerParams(
            "project = TEST",
            "user@example.com",
            "TARGET",
            metadata,
            true,
            "preAction.js",
            "postAction.js",
            "customField",
            TrackerParams.OutputType.field,
            TrackerParams.OperationType.Replace,
            2,
            30,
            customParams,
            "https://ci.example.com/runs/42",
            true
        );

        assertEquals("project = TEST", params.getInputJql());
        assertEquals("user@example.com", params.getInitiator());
        assertEquals("TARGET", params.getTargetProject());
        assertEquals(metadata, params.getMetadata());
        assertTrue(params.isAttachResponseAsFile());
        assertEquals("preAction.js", params.getPreJSAction());
        assertEquals("postAction.js", params.getPostJSAction());
        assertEquals("customField", params.getFieldName());
        assertEquals(TrackerParams.OutputType.field, params.getOutputType());
        assertEquals(TrackerParams.OperationType.Replace, params.getOperationType());
        assertEquals(2, params.getTicketContextDepth());
        assertEquals(30, params.getChunkProcessingTimeoutInMinutes());
        assertEquals(customParams, params.getCustomParams());
        assertEquals("https://ci.example.com/runs/42", params.getCiRunUrl());
        assertTrue(params.getAlwaysPostComments());
    }

    @Test
    void testCustomParams() {
        java.util.Map<String, Object> customParams = new java.util.HashMap<>();
        customParams.put("test", 123);
        trackerParams.setCustomParams(customParams);
        assertEquals(customParams, trackerParams.getCustomParams());
    }

    @Test
    void testSettersAndGetters() {
        trackerParams.setInputJql("status = Open");
        assertEquals("status = Open", trackerParams.getInputJql());

        trackerParams.setInitiator("admin@test.com");
        assertEquals("admin@test.com", trackerParams.getInitiator());

        trackerParams.setTargetProject("TARGET");
        assertEquals("TARGET", trackerParams.getTargetProject());

        Metadata metadata = new Metadata();
        trackerParams.setMetadata(metadata);
        assertEquals(metadata, trackerParams.getMetadata());

        trackerParams.setAttachResponseAsFile(true);
        assertTrue(trackerParams.isAttachResponseAsFile());

        trackerParams.setPreJSAction("preAction.js");
        assertEquals("preAction.js", trackerParams.getPreJSAction());

        trackerParams.setPostJSAction("action.js");
        assertEquals("action.js", trackerParams.getPostJSAction());

        trackerParams.setFieldName("description");
        assertEquals("description", trackerParams.getFieldName());

        trackerParams.setOutputType(TrackerParams.OutputType.creation);
        assertEquals(TrackerParams.OutputType.creation, trackerParams.getOutputType());

        trackerParams.setOperationType(TrackerParams.OperationType.Replace);
        assertEquals(TrackerParams.OperationType.Replace, trackerParams.getOperationType());

        trackerParams.setTicketContextDepth(3);
        assertEquals(3, trackerParams.getTicketContextDepth());

        trackerParams.setChunkProcessingTimeoutInMinutes(60);
        assertEquals(60, trackerParams.getChunkProcessingTimeoutInMinutes());
    }

    @Test
    void testOutputTypeEnum() {
        assertEquals(TrackerParams.OutputType.comment, TrackerParams.OutputType.valueOf("comment"));
        assertEquals(TrackerParams.OutputType.field, TrackerParams.OutputType.valueOf("field"));
        assertEquals(TrackerParams.OutputType.creation, TrackerParams.OutputType.valueOf("creation"));
        assertEquals(TrackerParams.OutputType.none, TrackerParams.OutputType.valueOf("none"));
    }

    @Test
    void testOperationTypeEnum() {
        assertEquals(TrackerParams.OperationType.Replace, TrackerParams.OperationType.valueOf("Replace"));
        assertEquals(TrackerParams.OperationType.Append, TrackerParams.OperationType.valueOf("Append"));
    }

    @Test
    void testConstants() {
        assertEquals("inputJql", TrackerParams.INPUT_JQL);
        assertEquals("initiator", TrackerParams.INITIATOR);
        assertEquals("metadata", TrackerParams.METADATA);
        assertEquals("operationType", TrackerParams.OPERATION_TYPE);
        assertEquals("outputType", TrackerParams.OUTPUT_TYPE);
        assertEquals("fieldName", TrackerParams.FIELD_NAME);
        assertEquals("attachResponseAsFile", TrackerParams.ATTACH_RESPONSE_AS_FILE);
        assertEquals("ticketContextDepth", TrackerParams.TICKET_CONTEXT_DEPTH);
        assertEquals("chunksProcessingTimeout", TrackerParams.CHUNKS_PROCESSING_TIMEOUT_IN_MINUTES);
        assertEquals("preJSAction", TrackerParams.PRE_ACTION);
        assertEquals("ciRunUrl", TrackerParams.CI_RUN_URL);
    }

    @Test
    void testCiRunUrl_setterAndGetter() {
        trackerParams.setCiRunUrl("https://ci.example.com/runs/42");
        assertEquals("https://ci.example.com/runs/42", trackerParams.getCiRunUrl());
    }

    @Test
    void testCiRunUrl_deserializedFromJson() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String json = "{\"ciRunUrl\":\"https://ci.example.com/runs/99\"}";
        TrackerParams params = gson.fromJson(json, TrackerParams.class);
        assertEquals("https://ci.example.com/runs/99", params.getCiRunUrl());
    }

    @Test
    void testAllOutputTypes() {
        TrackerParams.OutputType[] types = TrackerParams.OutputType.values();
        assertEquals(4, types.length);
        assertTrue(java.util.Arrays.asList(types).contains(TrackerParams.OutputType.comment));
        assertTrue(java.util.Arrays.asList(types).contains(TrackerParams.OutputType.field));
        assertTrue(java.util.Arrays.asList(types).contains(TrackerParams.OutputType.creation));
        assertTrue(java.util.Arrays.asList(types).contains(TrackerParams.OutputType.none));
    }

    @Test
    void testAllOperationTypes() {
        TrackerParams.OperationType[] types = TrackerParams.OperationType.values();
        assertEquals(2, types.length);
        assertTrue(java.util.Arrays.asList(types).contains(TrackerParams.OperationType.Replace));
        assertTrue(java.util.Arrays.asList(types).contains(TrackerParams.OperationType.Append));
    }
}
