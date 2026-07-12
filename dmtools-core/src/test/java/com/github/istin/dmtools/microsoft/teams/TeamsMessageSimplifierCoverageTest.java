// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.microsoft.teams;

import com.github.istin.dmtools.microsoft.teams.model.ChatMessage;
import io.github.furstenheim.CopyDown;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Additional unit tests for TeamsMessageSimplifier covering branches not
 * exercised by {@link TeamsMessageSimplifierTest}: transcript event details
 * (callId, organizer), unknown authors, adaptive card content extraction,
 * inline image extraction, reply previews with authors, and the cleanHtml
 * fallback path.
 */
public class TeamsMessageSimplifierCoverageTest {

    @Test
    public void testClassInstantiation() {
        // Covers the implicit default constructor
        assertNotNull(new TeamsMessageSimplifier());
    }

    @Test
    public void testTranscriptEvent_WithCallIdAndOrganizer() {
        JSONObject transcriptJson = new JSONObject();
        transcriptJson.put("id", "msg-42");
        transcriptJson.put("messageType", "systemEventMessage");
        transcriptJson.put("createdDateTime", "2025-10-08T14:00:00Z");
        transcriptJson.put("eventDetail", new JSONObject()
            .put("@odata.type", "#microsoft.graph.callTranscriptEventMessageDetail")
            .put("callTranscriptICalUid", "ical-uid-1")
            .put("callId", "call-id-99")
            .put("meetingOrganizer", new JSONObject()
                .put("user", new JSONObject()
                    .put("id", "organizer-id-7")
                    .put("displayName", "Organizer Person"))));
        ChatMessage message = new ChatMessage(transcriptJson.toString());

        String chatId = "19:meeting_abc@thread.v2";
        JSONObject result = TeamsMessageSimplifier.simplifyMessage(message, chatId);

        assertNotNull(result);
        assertEquals("transcript", result.getString("type"));
        assertEquals("call-id-99", result.getString("callId"));
        assertEquals("organizer-id-7", result.getString("organizerId"));
        assertEquals("msg-42", result.getString("messageId"));
        assertEquals(chatId, result.getString("chatId"));
        assertEquals(
            "https://graph.microsoft.com/v1.0/chats/" + chatId + "/messages/msg-42/hostedContents",
            result.getString("hostedContentsUrl"));
    }

    @Test
    public void testTranscriptEvent_MinimalDetail() {
        // No iCalUid, no callId, no organizer, no id - optional fields must be skipped
        JSONObject transcriptJson = new JSONObject();
        transcriptJson.put("messageType", "systemEventMessage");
        transcriptJson.put("createdDateTime", "2025-10-08T14:00:00Z");
        transcriptJson.put("eventDetail", new JSONObject()
            .put("@odata.type", "#microsoft.graph.callTranscriptEventMessageDetail"));
        ChatMessage message = new ChatMessage(transcriptJson.toString());

        JSONObject result = TeamsMessageSimplifier.simplifyMessage(message);

        assertNotNull(result);
        assertEquals("transcript", result.getString("type"));
        assertFalse(result.has("callId"));
        assertFalse(result.has("organizerId"));
        assertFalse(result.has("transcriptICalUid"));
        assertFalse(result.has("chatId"));
    }

    @Test
    public void testRecordingEvent_EmptyUrl_FallsThroughToSystem() {
        // Recording event with an empty URL should not produce a recording message
        JSONObject json = new JSONObject();
        json.put("id", "10");
        json.put("messageType", "systemEventMessage");
        json.put("createdDateTime", "2025-10-08T14:00:00Z");
        json.put("eventDetail", new JSONObject()
            .put("@odata.type", "#microsoft.graph.callRecordingEventMessageDetail")
            .put("callRecordingUrl", ""));
        ChatMessage message = new ChatMessage(json.toString());

        JSONObject result = TeamsMessageSimplifier.simplifyMessage(message);

        assertNotNull(result);
        assertEquals("system", result.getString("type"));
        assertEquals("callRecordingEventMessageDetail", result.getString("eventType"));
    }

    @Test
    public void testSimplifyMessage_NullFrom_UnknownAuthor() {
        JSONObject json = new JSONObject();
        json.put("id", "11");
        json.put("messageType", "message");
        json.put("body", new JSONObject().put("content", "Anonymous note"));
        ChatMessage message = new ChatMessage(json.toString());

        JSONObject result = TeamsMessageSimplifier.simplifyMessage(message);

        assertNotNull(result);
        assertEquals("Unknown", result.getString("author"));
        // No createdDateTime -> date falls back to empty string
        assertEquals("", result.getString("date"));
        assertEquals("Anonymous note", result.getString("body"));
    }

    @Test
    public void testSimplifyMessage_EmptyBody_AdaptiveCardContentExtracted() {
        JSONArray cardBody = new JSONArray()
            .put(new JSONObject()
                .put("type", "TextBlock")
                .put("text", "Poll Question?")
                .put("weight", "bolder"))
            .put(new JSONObject()
                .put("type", "TextBlock")
                .put("text", "Pick an option"))
            .put(new JSONObject()
                .put("type", "Input.ChoiceSet")
                .put("choices", new JSONArray()
                    .put(new JSONObject().put("title", "Option A"))
                    .put(new JSONObject().put("title", "Option B"))
                    .put(new JSONObject().put("title", ""))))
            .put(new JSONObject()
                .put("type", "Container")
                .put("items", new JSONArray()
                    .put(new JSONObject()
                        .put("type", "TextBlock")
                        .put("text", "Container text"))))
            .put(new JSONObject()
                .put("type", "ColumnSet")
                .put("columns", new JSONArray()
                    .put(new JSONObject()
                        .put("type", "Column")
                        .put("items", new JSONArray()
                            .put(new JSONObject()
                                .put("type", "TextBlock")
                                .put("text", "Column text"))))))
            // A non-object entry must be skipped
            .put("just-a-string");
        JSONObject cardJson = new JSONObject()
            .put("type", "AdaptiveCard")
            .put("body", cardBody);

        JSONObject json = new JSONObject();
        json.put("id", "12");
        json.put("messageType", "message");
        json.put("createdDateTime", "2025-10-09T10:00:00Z");
        json.put("from", new JSONObject()
            .put("user", new JSONObject().put("displayName", "Polly")));
        json.put("body", new JSONObject().put("content", ""));
        json.put("attachments", new JSONArray()
            .put(new JSONObject()
                .put("contentType", "application/vnd.microsoft.card.adaptive")
                .put("content", cardJson.toString())));
        ChatMessage message = new ChatMessage(json.toString());

        JSONObject result = TeamsMessageSimplifier.simplifyMessage(message);

        assertNotNull(result);
        String body = result.getString("body");
        assertTrue(body.startsWith("[Poll/Card] "));
        assertTrue(body.contains("**Poll Question?**"));
        assertTrue(body.contains("Pick an option"));
        assertTrue(body.contains("  - Option A"));
        assertTrue(body.contains("  - Option B"));
        assertTrue(body.contains("Container text"));
        assertTrue(body.contains("Column text"));
        // Adaptive card attachments are not listed as attachments
        assertFalse(result.has("attachments"));
    }

    @Test
    public void testSimplifyMessage_EmptyBody_AdaptiveCardWithNoText() {
        // Card whose body yields no text -> body falls back to empty string
        JSONObject cardJson = new JSONObject()
            .put("type", "AdaptiveCard")
            .put("body", new JSONArray()
                .put(new JSONObject().put("type", "TextBlock").put("text", "")));

        JSONObject json = new JSONObject();
        json.put("id", "13");
        json.put("messageType", "message");
        json.put("attachments", new JSONArray()
            .put(new JSONObject()
                .put("contentType", "application/vnd.microsoft.card.adaptive")
                .put("content", cardJson.toString())));
        ChatMessage message = new ChatMessage(json.toString());

        JSONObject result = TeamsMessageSimplifier.simplifyMessage(message);

        assertNotNull(result);
        assertEquals("", result.getString("body"));
    }

    @Test
    public void testSimplifyMessage_EmptyBody_InvalidAdaptiveCardJson() {
        // Unparseable card content is skipped; non-card attachment gives nothing -> empty body
        JSONObject json = new JSONObject();
        json.put("id", "14");
        json.put("messageType", "message");
        json.put("attachments", new JSONArray()
            .put(new JSONObject()
                .put("contentType", "application/vnd.microsoft.card.adaptive")
                .put("content", "not-a-json"))
            .put(new JSONObject()
                .put("contentType", "text/plain")
                .put("content", "irrelevant")));
        ChatMessage message = new ChatMessage(json.toString());

        JSONObject result = TeamsMessageSimplifier.simplifyMessage(message);

        assertNotNull(result);
        assertEquals("", result.getString("body"));
    }

    @Test
    public void testSimplifyMessage_BodyWithInlineImages() {
        JSONObject json = new JSONObject();
        json.put("id", "15");
        json.put("messageType", "message");
        json.put("createdDateTime", "2025-10-09T10:00:00Z");
        json.put("body", new JSONObject()
            .put("content", "<p>Look:</p>"
                + "<img src=\"https://example.com/pic1.png\" alt=\"a\">"
                + "<IMG SRC=\"https://example.com/pic2.jpg\">"
                + "<img src=\"https://example.com/hostedContents/secret\">"
                + "<img src=\"\">"));
        ChatMessage message = new ChatMessage(json.toString());

        JSONObject result = TeamsMessageSimplifier.simplifyMessage(message);

        assertNotNull(result);
        assertTrue(result.has("attachments"));
        JSONArray attachments = result.getJSONArray("attachments");
        assertEquals(2, attachments.length());
        JSONObject first = attachments.getJSONObject(0);
        assertEquals("image", first.getString("type"));
        assertEquals("https://example.com/pic1.png", first.getString("url"));
        JSONObject second = attachments.getJSONObject(1);
        assertEquals("image", second.getString("type"));
        assertEquals("https://example.com/pic2.jpg", second.getString("url"));
    }

    @Test
    public void testSimplifyMessage_ImagesPlusFileAttachments() {
        JSONObject json = new JSONObject();
        json.put("id", "16");
        json.put("messageType", "message");
        json.put("body", new JSONObject()
            .put("content", "<p>Doc</p><img src=\"https://example.com/inline.png\">"));
        json.put("attachments", new JSONArray()
            .put(new JSONObject()
                .put("contentType", "application/pdf")
                .put("name", "report.pdf")
                .put("contentUrl", "https://example.com/report.pdf")));
        ChatMessage message = new ChatMessage(json.toString());

        JSONObject result = TeamsMessageSimplifier.simplifyMessage(message);

        assertNotNull(result);
        JSONArray attachments = result.getJSONArray("attachments");
        assertEquals(2, attachments.length());
        // Extracted image comes first
        assertEquals("image", attachments.getJSONObject(0).getString("type"));
        assertEquals("report.pdf", attachments.getJSONObject(1).getString("name"));
    }

    @Test
    public void testSimplifyMessage_EmptyBody_CardWithoutBodyAndNoAttachments() {
        // Card JSON without a "body" array -> extractCardBodyContent(null, ...) guard
        JSONObject json = new JSONObject();
        json.put("id", "17");
        json.put("messageType", "message");
        json.put("attachments", new JSONArray()
            .put(new JSONObject()
                .put("contentType", "application/vnd.microsoft.card.adaptive")
                .put("content", new JSONObject().put("type", "AdaptiveCard").toString())));
        ChatMessage message = new ChatMessage(json.toString());

        JSONObject result = TeamsMessageSimplifier.simplifyMessage(message);
        assertNotNull(result);
        assertEquals("", result.getString("body"));

        // No attachments at all -> extractAdaptiveCardContent(null) guard
        JSONObject noAttJson = new JSONObject();
        noAttJson.put("id", "18");
        noAttJson.put("messageType", "message");
        ChatMessage noAttachments = new ChatMessage(noAttJson.toString());

        JSONObject result2 = TeamsMessageSimplifier.simplifyMessage(noAttachments);
        assertNotNull(result2);
        assertEquals("", result2.getString("body"));
    }

    @Test
    public void testExtractAttachments_MessageReference_WithAuthor() {
        List<ChatMessage.Attachment> attachments = new ArrayList<>();
        JSONObject replyContent = new JSONObject()
            .put("messagePreview", "Original text")
            .put("messageSender", new JSONObject()
                .put("user", new JSONObject().put("displayName", "Alice")));
        attachments.add(new ChatMessage.Attachment(new JSONObject()
            .put("contentType", "messageReference")
            .put("content", replyContent.toString())));

        JSONArray result = TeamsMessageSimplifier.extractAttachments(attachments);

        assertEquals(1, result.length());
        assertEquals("Reply to Alice: Original text", result.getString(0));
    }

    @Test
    public void testExtractAttachments_MessageReference_EmptyPreviewWithAuthor() {
        List<ChatMessage.Attachment> attachments = new ArrayList<>();
        JSONObject replyContent = new JSONObject()
            .put("messagePreview", "")
            .put("messageSender", new JSONObject()
                .put("user", new JSONObject().put("displayName", "Bob")));
        attachments.add(new ChatMessage.Attachment(new JSONObject()
            .put("contentType", "messageReference")
            .put("content", replyContent.toString())));

        JSONArray result = TeamsMessageSimplifier.extractAttachments(attachments);

        assertEquals(1, result.length());
        assertEquals("Reply to Bob", result.getString(0));
    }

    @Test
    public void testExtractAttachments_MessageReference_EmptyPreviewNoAuthor() {
        List<ChatMessage.Attachment> attachments = new ArrayList<>();
        JSONObject replyContent = new JSONObject().put("messagePreview", "");
        attachments.add(new ChatMessage.Attachment(new JSONObject()
            .put("contentType", "messageReference")
            .put("content", replyContent.toString())));

        JSONArray result = TeamsMessageSimplifier.extractAttachments(attachments);

        assertEquals(1, result.length());
        assertEquals("Reply to message", result.getString(0));
    }

    @Test
    public void testExtractAttachments_MessageReference_InvalidContent() {
        List<ChatMessage.Attachment> attachments = new ArrayList<>();
        attachments.add(new ChatMessage.Attachment(new JSONObject()
            .put("contentType", "messageReference")
            .put("content", "{not valid json")));

        JSONArray result = TeamsMessageSimplifier.extractAttachments(attachments);

        assertEquals(1, result.length());
        // Extraction failed, so it falls back to the structured object with the type
        JSONObject fallback = result.getJSONObject(0);
        assertEquals("messageReference", fallback.getString("type"));
    }

    @Test
    public void testExtractAttachments_MessageReference_NullContent() {
        List<ChatMessage.Attachment> attachments = new ArrayList<>();
        attachments.add(new ChatMessage.Attachment(new JSONObject()
            .put("contentType", "messageReference")));

        JSONArray result = TeamsMessageSimplifier.extractAttachments(attachments);

        assertEquals(1, result.length());
        JSONObject fallback = result.getJSONObject(0);
        assertEquals("messageReference", fallback.getString("type"));
    }

    @Test
    public void testExtractAttachments_EmptyAttachment_FallbackString() {
        List<ChatMessage.Attachment> attachments = new ArrayList<>();
        attachments.add(new ChatMessage.Attachment(new JSONObject()));

        JSONArray result = TeamsMessageSimplifier.extractAttachments(attachments);

        assertEquals(1, result.length());
        assertEquals("Unknown attachment", result.getString(0));
    }

    @Test
    public void testCleanHtml_FallbackWhenConverterFails() {
        try (MockedConstruction<CopyDown> mocked = Mockito.mockConstruction(
                CopyDown.class,
                (mock, context) -> Mockito.when(mock.convert(Mockito.anyString()))
                    .thenThrow(new RuntimeException("boom")))) {
            String cleaned = TeamsMessageSimplifier.cleanHtml(
                "<p>Hello&nbsp;<b>world</b>&lt;&gt;&amp;&quot;q&#39;</p>");
            assertEquals("Hello world<>&\"q'", cleaned);
        }
    }

    @Test
    public void testExtractImageUrls_NullAndEmpty_ViaReflection() throws Exception {
        Method method = TeamsMessageSimplifier.class
            .getDeclaredMethod("extractImageUrls", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> nullResult = (List<String>) method.invoke(null, (String) null);
        assertTrue(nullResult.isEmpty());

        @SuppressWarnings("unchecked")
        List<String> emptyResult = (List<String>) method.invoke(null, "");
        assertTrue(emptyResult.isEmpty());
    }

    @Test
    public void testExtractCardBodyContent_NullElements_ViaReflection() throws Exception {
        // The null-elements guard is unreachable through the public API because
        // callers check for null before invoking; cover it directly.
        Method method = TeamsMessageSimplifier.class
            .getDeclaredMethod("extractCardBodyContent", JSONArray.class, StringBuilder.class);
        method.setAccessible(true);

        StringBuilder content = new StringBuilder();
        method.invoke(null, null, content);
        assertEquals(0, content.length());
    }
}
