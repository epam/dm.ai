// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.microsoft.teams;

import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.microsoft.teams.model.Channel;
import com.github.istin.dmtools.microsoft.teams.model.Chat;
import com.github.istin.dmtools.microsoft.teams.model.ChatMessage;
import com.github.istin.dmtools.microsoft.teams.model.Team;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Unit tests for {@link TeamsClient} covering chats, messages, teams, channels,
 * file/transcript helpers, and error handling.
 *
 * <p>Uses a Mockito spy on a stub subclass so HTTP calls ({@code execute}/{@code post})
 * are intercepted and answered with canned responses. A pre-seeded OAuth token cache
 * file in a temp directory keeps client construction offline.
 */
class TeamsClientUnitTest {

    /** Minimal concrete subclass that lets us construct and spy on the client. */
    private static class StubTeamsClient extends TeamsClient {
        StubTeamsClient(String tokenCachePath) throws IOException {
            super("test-client-id", "test-tenant", "User.Read", "refresh_token", 0, tokenCachePath, null);
        }
    }

    @TempDir
    Path tempDir;

    private TeamsClient client;
    /** url substring -> queued canned responses (String) or failures (IOException). */
    private final Map<String, Deque<Object>> responses = new LinkedHashMap<>();
    private final Deque<Object> postResponses = new ArrayDeque<>();
    private final List<String> executedUrls = new ArrayList<>();
    private final List<GenericRequest> postedRequests = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        Path cacheFile = tempDir.resolve("teams-test.token");
        Files.writeString(cacheFile, "{\"access_token\":\"test-access-token\",\"expires_at\":9999999999999}");
        client = Mockito.spy(new StubTeamsClient(cacheFile.toString()));
        responses.clear();
        postResponses.clear();
        executedUrls.clear();
        postedRequests.clear();
        stubNetworking();
    }

    private void stubNetworking() throws IOException {
        doAnswer(invocation -> {
            GenericRequest request = invocation.getArgument(0);
            String url = request.url();
            executedUrls.add(url);
            String bestKey = null;
            for (String key : responses.keySet()) {
                if (url.contains(key) && (bestKey == null || key.length() > bestKey.length())) {
                    bestKey = key;
                }
            }
            if (bestKey == null) {
                throw new IOException("Unexpected URL in test: " + url);
            }
            Object canned = responses.get(bestKey).poll();
            if (canned instanceof IOException) {
                throw (IOException) canned;
            }
            if (canned == null) {
                throw new IOException("No canned response left for: " + url);
            }
            return (String) canned;
        }).when(client).execute(any(GenericRequest.class));

        doAnswer(invocation -> {
            GenericRequest request = invocation.getArgument(0);
            postedRequests.add(request);
            Object canned = postResponses.poll();
            if (canned instanceof IOException) {
                throw (IOException) canned;
            }
            if (canned == null) {
                throw new IOException("No canned POST response in test");
            }
            return (String) canned;
        }).when(client).post(any(GenericRequest.class));
    }

    /** Registers a canned response (String) or failure (IOException) for any URL containing the key. */
    private void on(String urlSubstring, Object canned) {
        responses.computeIfAbsent(urlSubstring, k -> new ArrayDeque<>()).add(canned);
    }

    private void onPost(Object canned) {
        postResponses.add(canned);
    }

    // ==================== JSON fixtures ====================

    private static String nowIso() {
        return Instant.now().toString();
    }

    private static String chatJson(String id, String topic, String chatType) {
        return new JSONObject()
                .put("id", id)
                .put("chatType", chatType)
                .put("topic", topic)
                .put("lastUpdatedDateTime", nowIso())
                .put("members", new JSONArray()
                        .put(new JSONObject().put("displayName", "Alice Smith"))
                        .put(new JSONObject().put("displayName", "Bob Jones")))
                .put("lastMessagePreview", new JSONObject()
                        .put("createdDateTime", nowIso())
                        .put("from", new JSONObject().put("user", new JSONObject().put("displayName", "Alice Smith")))
                        .put("body", new JSONObject().put("content", "hello there")))
                .toString();
    }

    private static String messageJson(String id, String createdDateTime, String content) {
        return new JSONObject()
                .put("id", id)
                .put("messageType", "message")
                .put("createdDateTime", createdDateTime)
                .put("from", new JSONObject().put("user", new JSONObject().put("displayName", "Alice Smith")))
                .put("body", new JSONObject().put("contentType", "text").put("content", content))
                .toString();
    }

    private static String systemEventMessageJson(String id, String createdDateTime, String eventType) {
        JSONObject message = new JSONObject()
                .put("id", id)
                .put("messageType", "systemEventMessage")
                .put("createdDateTime", createdDateTime)
                .put("body", new JSONObject().put("content", ""));
        if (eventType != null) {
            message.put("eventDetail", new JSONObject().put("@odata.type", eventType));
        }
        return message.toString();
    }

    private static String page(String... items) {
        JSONObject json = new JSONObject();
        JSONArray value = new JSONArray();
        for (String item : items) {
            value.put(new JSONObject(item));
        }
        json.put("value", value);
        return json.toString();
    }

    private static String pageWithNext(String nextLink, String... items) {
        JSONObject json = new JSONObject(page(items));
        json.put("@odata.nextLink", nextLink);
        return json.toString();
    }

    private static String teamJson(String id, String displayName) {
        return new JSONObject().put("id", id).put("displayName", displayName).toString();
    }

    private static String channelJson(String id, String displayName) {
        return new JSONObject().put("id", id).put("displayName", displayName).toString();
    }

    private static String userJson() {
        return new JSONObject()
                .put("id", "user-1")
                .put("displayName", "Alice Smith")
                .put("mail", "alice@example.com")
                .toString();
    }

    // ==================== testConnection ====================

    @Test
    void testConnection_success() {
        on("/me", userJson());

        Map<String, Object> result = client.testConnection();

        assertEquals(true, result.get("success"));
        assertEquals("Alice Smith", result.get("user"));
        assertEquals("alice@example.com", result.get("email"));
    }

    @Test
    void testConnection_emailFallsBackToUserPrincipalName() {
        on("/me", new JSONObject()
                .put("id", "user-1")
                .put("displayName", "Alice Smith")
                .put("userPrincipalName", "alice@tenant.onmicrosoft.com")
                .toString());

        Map<String, Object> result = client.testConnection();

        assertEquals(true, result.get("success"));
        assertEquals("alice@tenant.onmicrosoft.com", result.get("email"));
    }

    @Test
    void testConnection_unexpectedResponseFormat() {
        on("/me", "{}");

        Map<String, Object> result = client.testConnection();

        assertEquals(false, result.get("success"));
        assertEquals("Unexpected response format from Microsoft Teams API", result.get("message"));
    }

    @Test
    void testConnection_emptyResponse() {
        on("/me", "");

        Map<String, Object> result = client.testConnection();

        assertEquals(false, result.get("success"));
        assertEquals("Empty response from Microsoft Teams API", result.get("message"));
    }

    @Test
    void testConnection_exception() {
        on("/me", new IOException("boom"));

        Map<String, Object> result = client.testConnection();

        assertEquals(false, result.get("success"));
        assertTrue(String.valueOf(result.get("message")).contains("boom"));
        assertEquals("IOException", result.get("error"));
    }

    // ==================== Chat listing / pagination ====================

    @Test
    void getChatsRaw_singlePage() throws IOException {
        on("/me/chats", page(chatJson("chat-1", "Project", "group"), chatJson("chat-2", "Random", "group")));

        List<Chat> chats = client.getChatsRaw(50);

        assertEquals(2, chats.size());
        assertEquals("chat-1", chats.get(0).getId());
        assertTrue(executedUrls.get(0).contains("$top=50"));
        assertTrue(executedUrls.get(0).contains("$orderby="));
        assertTrue(executedUrls.get(0).contains("$expand="));
    }

    @Test
    void getChatsRaw_multiplePages_followsNextLink() throws IOException {
        on("/me/chats", pageWithNext(
                "https://graph.microsoft.com/v1.0/me/chats?$skiptoken=page2",
                chatJson("chat-1", "One", "group"), chatJson("chat-2", "Two", "group")));
        on("/me/chats", page(chatJson("chat-3", "Three", "group"), chatJson("chat-4", "Four", "group")));

        List<Chat> chats = client.getChatsRaw(0); // 0 = get all

        assertEquals(4, chats.size());
        assertEquals(2, executedUrls.size());
        // query parameters must only be added to the first request
        assertTrue(executedUrls.get(0).contains("$top=50"));
        assertFalse(executedUrls.get(1).contains("$orderby"));
    }

    @Test
    void getChatsRaw_skipsDuplicateChatIds() throws IOException {
        on("/me/chats", page(
                chatJson("chat-1", "One", "group"),
                chatJson("chat-1", "One duplicate", "group"),
                chatJson("chat-2", "Two", "group")));

        List<Chat> chats = client.getChatsRaw(0);

        assertEquals(2, chats.size());
    }

    @Test
    void getChatsRaw_respectsLimitWithinPage() throws IOException {
        on("/me/chats", page(
                chatJson("chat-1", "One", "group"),
                chatJson("chat-2", "Two", "group"),
                chatJson("chat-3", "Three", "group")));

        List<Chat> chats = client.getChatsRaw(1);

        assertEquals(1, chats.size());
        assertTrue(executedUrls.get(0).contains("$top=1"));
    }

    @Test
    void getChatsAndPerform_earlyExitStopsPagination() throws IOException {
        on("/me/chats", pageWithNext(
                "https://graph.microsoft.com/v1.0/me/chats?$skiptoken=page2",
                chatJson("chat-1", "One", "group"), chatJson("chat-2", "Two", "group")));

        AtomicInteger processed = new AtomicInteger();
        client.getChatsAndPerform(0, chat -> {
            processed.incrementAndGet();
            return true; // stop after first chat
        });

        assertEquals(1, processed.get());
        assertEquals(1, executedUrls.size());
    }

    @Test
    void getChats_simplifiedOutput() throws IOException {
        on("/me/chats", page(chatJson("chat-1", "Project", "group")));
        on("/me", userJson());

        String result = client.getChats(10);

        JSONArray simplified = new JSONArray(result);
        assertEquals(1, simplified.length());
        assertEquals("Project", simplified.getJSONObject(0).getString("chatName"));
    }

    // ==================== Recent chats ====================

    @Test
    void getRecentChats_withChatTypeFilter() throws IOException {
        on("/me/chats", page(
                chatJson("chat-1", null, "oneOnOne"),
                chatJson("chat-2", "Group chat", "group")));
        on("/me", userJson());

        String result = client.getRecentChats(10, "oneOnOne");

        JSONArray simplified = new JSONArray(result);
        assertTrue(simplified.length() >= 1);
        // filtered fetches much more than the target limit
        assertTrue(executedUrls.get(0).contains("$top=50"));
    }

    @Test
    void getRecentChats_defaultLimitAndAllTypes() throws IOException {
        on("/me/chats", page(chatJson("chat-1", "Project", "group")));
        on("/me", userJson());

        String result = client.getRecentChats(null, null);

        assertNotNull(result);
        assertTrue(new JSONArray(result).length() >= 1);
    }

    @Test
    void getRecentChats_zeroLimitMeansAll() throws IOException {
        on("/me/chats", page(chatJson("chat-1", "Project", "group")));
        on("/me", userJson());

        String result = client.getRecentChats(0, "all");

        assertNotNull(result);
        assertTrue(new JSONArray(result).length() >= 1);
    }

    // ==================== Message iteration ====================

    @Test
    void getChatMessagesRaw_descPagination() throws IOException {
        on("/me/chats/chat-1/messages", pageWithNext(
                "https://graph.microsoft.com/v1.0/me/chats/chat-1/messages?$skiptoken=page2",
                messageJson("msg-1", "2026-07-10T10:00:00Z", "first"),
                messageJson("msg-2", "2026-07-10T09:00:00Z", "second")));
        on("/me/chats/chat-1/messages", page(messageJson("msg-3", "2026-07-10T08:00:00Z", "third")));

        List<ChatMessage> messages = client.getChatMessagesRaw("chat-1", 0, null);

        assertEquals(3, messages.size());
        assertEquals(2, executedUrls.size());
    }

    @Test
    void getChatMessagesRaw_appliesServerSideFilter() throws IOException {
        on("/me/chats/chat-1/messages", page(messageJson("msg-1", "2026-07-10T10:00:00Z", "hi")));

        client.getChatMessagesRaw("chat-1", 10, " lastModifiedDateTime gt 2025-01-01T00:00:00Z ");

        assertTrue(executedUrls.get(0).contains("$filter=lastModifiedDateTime"));
        assertTrue(executedUrls.get(0).contains("$top=10"));
    }

    @Test
    void getMessagesAndPerform_ascOrderReversesMessages() throws IOException {
        // API returns newest first; ASC must reverse to oldest first
        on("/me/chats/chat-1/messages", page(
                messageJson("msg-new", "2026-07-10T10:00:00Z", "newest"),
                messageJson("msg-old", "2026-07-10T08:00:00Z", "oldest")));

        List<ChatMessage> collected = new ArrayList<>();
        client.getMessagesAndPerform("chat-1", 0, null, "asc", message -> {
            collected.add(message);
            return false;
        });

        assertEquals(2, collected.size());
        assertEquals("msg-old", collected.get(0).getId());
        assertEquals("msg-new", collected.get(1).getId());
    }

    @Test
    void getMessagesAndPerform_ascOrderRespectsLimit() throws IOException {
        on("/me/chats/chat-1/messages", page(
                messageJson("msg-new", "2026-07-10T10:00:00Z", "newest"),
                messageJson("msg-old", "2026-07-10T08:00:00Z", "oldest")));

        List<ChatMessage> collected = new ArrayList<>();
        client.getMessagesAndPerform("chat-1", 1, null, "ASC", message -> {
            collected.add(message);
            return false;
        });

        assertEquals(1, collected.size());
        assertEquals("msg-new", collected.get(0).getId());
    }

    @Test
    void getMessagesAndPerform_descEarlyExit() throws IOException {
        on("/me/chats/chat-1/messages", pageWithNext(
                "https://graph.microsoft.com/v1.0/me/chats/chat-1/messages?$skiptoken=page2",
                messageJson("msg-1", "2026-07-10T10:00:00Z", "first"),
                messageJson("msg-2", "2026-07-10T09:00:00Z", "second")));

        List<ChatMessage> collected = new ArrayList<>();
        client.getMessagesAndPerform("chat-1", 0, null, "desc", message -> {
            collected.add(message);
            return true; // stop immediately
        });

        assertEquals(1, collected.size());
        assertEquals(1, executedUrls.size());
    }

    @Test
    void getMessagesAndPerform_descStopsAtLimit() throws IOException {
        on("/me/chats/chat-1/messages", pageWithNext(
                "https://graph.microsoft.com/v1.0/me/chats/chat-1/messages?$skiptoken=page2",
                messageJson("msg-1", "2026-07-10T10:00:00Z", "first"),
                messageJson("msg-2", "2026-07-10T09:00:00Z", "second"),
                messageJson("msg-3", "2026-07-10T08:00:00Z", "third")));

        List<ChatMessage> collected = new ArrayList<>();
        client.getMessagesAndPerform("chat-1", 2, null, "desc", message -> {
            collected.add(message);
            return false;
        });

        assertEquals(2, collected.size());
        assertEquals(1, executedUrls.size());
    }

    // ==================== findChatByName ====================

    @Test
    void findChatByName_matchesTopic() throws IOException {
        on("/me/chats", page(chatJson("chat-1", "Project Alpha", "group")));

        Chat chat = client.findChatByName("alpha");

        assertNotNull(chat);
        assertEquals("chat-1", chat.getId());
    }

    @Test
    void findChatByName_matchesMemberName() throws IOException {
        on("/me/chats", page(chatJson("chat-1", null, "oneOnOne")));

        Chat chat = client.findChatByName("bob jones");

        assertNotNull(chat);
        assertEquals("chat-1", chat.getId());
    }

    @Test
    void findChatByName_notFoundReturnsNull() throws IOException {
        on("/me/chats", page(chatJson("chat-1", "Project", "group")));

        assertNull(client.findChatByName("nonexistent"));
    }

    @Test
    void getChatMessagesByNameRaw_chatNotFoundReturnsEmpty() throws IOException {
        on("/me/chats", page());

        List<ChatMessage> messages = client.getChatMessagesByNameRaw("nonexistent", 10);

        assertTrue(messages.isEmpty());
    }

    @Test
    void getChatMessagesByNameRaw_found() throws IOException {
        on("/me/chats", page(chatJson("chat-1", "Project", "group")));
        on("/me/chats/chat-1/messages", page(messageJson("msg-1", "2026-07-10T10:00:00Z", "hi")));

        List<ChatMessage> messages = client.getChatMessagesByNameRaw("project", 10);

        assertEquals(1, messages.size());
    }

    // ==================== getChatMessages (simplified) ====================

    @Test
    void getChatMessages_chatNotFoundReturnsErrorJson() throws IOException {
        on("/me/chats", page());

        String result = client.getChatMessages("nonexistent", 10, null);

        JSONObject error = new JSONObject(result);
        assertTrue(error.getString("error").contains("Chat not found"));
    }

    @Test
    void getChatMessages_filtersNoisySystemMessages() throws IOException {
        on("/me/chats", page(chatJson("chat-1", "Project", "group")));
        on("/me/chats/chat-1/messages", page(
                messageJson("msg-1", "2026-07-10T10:00:00Z", "real message"),
                systemEventMessageJson("sys-1", "2026-07-10T09:00:00Z",
                        "#microsoft.graph.membersAddedEventMessageDetail"),
                systemEventMessageJson("sys-2", "2026-07-10T08:30:00Z", null)));

        String result = client.getChatMessages("project", null, null);

        JSONArray simplified = new JSONArray(result);
        assertEquals(1, simplified.length());
        assertEquals("real message", simplified.getJSONObject(0).getString("body"));
    }

    @Test
    void getChatMessages_keepsImportantSystemEvents() throws IOException {
        on("/me/chats", page(chatJson("chat-1", "Project", "group")));
        on("/me/chats/chat-1/messages", page(
                systemEventMessageJson("sys-1", "2026-07-10T10:00:00Z",
                        "#microsoft.graph.callRecordingEventMessageDetail")));

        String result = client.getChatMessages("project", 10, "asc");

        JSONArray simplified = new JSONArray(result);
        assertEquals(1, simplified.length());
    }

    @Test
    void getChatMessages_zeroLimitCollectsAll() throws IOException {
        on("/me/chats", page(chatJson("chat-1", "Project", "group")));
        on("/me/chats/chat-1/messages", page(
                messageJson("msg-1", "2026-07-10T10:00:00Z", "one"),
                messageJson("msg-2", "2026-07-10T09:00:00Z", "two")));

        String result = client.getChatMessages("project", 0, "desc");

        assertEquals(2, new JSONArray(result).length());
    }

    // ==================== getChatMessagesSince ====================

    @Test
    void getChatMessagesSince_invalidDateThrows() {
        IOException exception = assertThrows(IOException.class,
                () -> client.getChatMessagesSince("chat-1", "not-a-date", "desc"));

        assertTrue(exception.getMessage().contains("Invalid date format"));
    }

    @Test
    void getChatMessagesSince_descStopsAtDateBoundary() throws IOException {
        on("/me/chats/chat-1/messages", pageWithNext(
                "https://graph.microsoft.com/v1.0/me/chats/chat-1/messages?$skiptoken=page2",
                messageJson("msg-new", "2026-01-16T10:00:00Z", "after cutoff"),
                messageJson("msg-old", "2026-01-14T10:00:00Z", "before cutoff")));

        String result = client.getChatMessagesSince("chat-1", "2026-01-15T00:00:00Z", "desc");

        JSONArray simplified = new JSONArray(result);
        assertEquals(1, simplified.length());
        assertEquals("after cutoff", simplified.getJSONObject(0).getString("body"));
        // iteration must stop early without fetching the second page
        assertEquals(1, executedUrls.size());
    }

    @Test
    void getChatMessagesSince_ascSkipsMessagesBeforeCutoff() throws IOException {
        on("/me/chats/chat-1/messages", page(
                messageJson("msg-new", "2026-01-16T10:00:00Z", "after cutoff"),
                messageJson("msg-old", "2026-01-14T10:00:00Z", "before cutoff")));

        String result = client.getChatMessagesSince("chat-1", "2026-01-15T00:00:00Z", "asc");

        JSONArray simplified = new JSONArray(result);
        assertEquals(1, simplified.length());
        assertEquals("after cutoff", simplified.getJSONObject(0).getString("body"));
    }

    @Test
    void getChatMessagesSince_unparseableMessageDateIsIncluded() throws IOException {
        on("/me/chats/chat-1/messages", page(
                messageJson("msg-bad-date", "not-a-timestamp", "unparseable date")));

        String result = client.getChatMessagesSince("chat-1", "2026-01-15T00:00:00Z", "desc");

        JSONArray simplified = new JSONArray(result);
        assertEquals(1, simplified.length());
        assertEquals("unparseable date", simplified.getJSONObject(0).getString("body"));
    }

    @Test
    void getChatMessagesSince_skipsNoisyMessages() throws IOException {
        on("/me/chats/chat-1/messages", page(
                systemEventMessageJson("sys-1", "2026-01-16T10:00:00Z",
                        "#microsoft.graph.memberLeftEventMessageDetail")));

        String result = client.getChatMessagesSince("chat-1", "2026-01-15T00:00:00Z", "desc");

        assertEquals(0, new JSONArray(result).length());
    }

    @Test
    void getChatMessagesByNameSince_chatNotFound() throws IOException {
        on("/me/chats", page());

        String result = client.getChatMessagesByNameSince("nope", "2026-01-15T00:00:00Z", null);

        assertTrue(new JSONObject(result).getString("error").contains("Chat not found"));
    }

    @Test
    void getChatMessagesByNameSince_found() throws IOException {
        on("/me/chats", page(chatJson("chat-1", "Project", "group")));
        on("/me/chats/chat-1/messages", page(messageJson("msg-1", "2026-01-16T10:00:00Z", "hi")));

        String result = client.getChatMessagesByNameSince("project", "2026-01-15T00:00:00Z", "desc");

        assertEquals(1, new JSONArray(result).length());
    }

    // ==================== Send operations ====================

    @Test
    void sendChatMessage_postsBodyAndParsesResponse() throws IOException {
        onPost(messageJson("msg-sent", "2026-07-10T10:00:00Z", "hello"));

        ChatMessage message = client.sendChatMessage("chat-1", "hello");

        assertEquals("msg-sent", message.getId());
        assertEquals(1, postedRequests.size());
        String body = postedRequests.get(0).getBody();
        assertTrue(body.contains("\"contentType\":\"text\""));
        assertTrue(body.contains("\"content\":\"hello\""));
        assertTrue(postedRequests.get(0).url().contains("/me/chats/chat-1/messages"));
    }

    @Test
    void sendChatMessage_withHtmlContentType_postsHtmlBody() throws IOException {
        onPost(messageJson("msg-sent", "2026-07-10T10:00:00Z", "<b>hello</b>"));

        ChatMessage message = client.sendChatMessage("chat-1", "<b>hello</b>", "html");

        assertEquals("msg-sent", message.getId());
        assertEquals(1, postedRequests.size());
        JSONObject body = new JSONObject(postedRequests.get(0).getBody());
        assertEquals("html", body.getJSONObject("body").getString("contentType"));
        assertEquals("<b>hello</b>", body.getJSONObject("body").getString("content"));
    }

    @Test
    void sendChatMessage_withNullContentType_defaultsToText() throws IOException {
        onPost(messageJson("msg-sent", "2026-07-10T10:00:00Z", "hello"));

        ChatMessage message = client.sendChatMessage("chat-1", "hello", null);

        assertEquals("msg-sent", message.getId());
        assertEquals(1, postedRequests.size());
        JSONObject body = new JSONObject(postedRequests.get(0).getBody());
        assertEquals("text", body.getJSONObject("body").getString("contentType"));
    }

    @Test
    void sendChatMessage_withEmptyContentType_defaultsToText() throws IOException {
        onPost(messageJson("msg-sent", "2026-07-10T10:00:00Z", "hello"));

        ChatMessage message = client.sendChatMessage("chat-1", "hello", "   ");

        assertEquals("msg-sent", message.getId());
        assertEquals(1, postedRequests.size());
        JSONObject body = new JSONObject(postedRequests.get(0).getBody());
        assertEquals("text", body.getJSONObject("body").getString("contentType"));
    }

    @Test
    void sendChatMessageByName_chatNotFound() throws IOException {
        on("/me/chats", page());

        String result = client.sendChatMessageByName("nope", "hello");

        JSONObject json = new JSONObject(result);
        assertFalse(json.getBoolean("success"));
        assertTrue(json.getString("error").contains("Chat not found"));
    }

    @Test
    void sendChatMessageByName_successWithTopic() throws IOException {
        on("/me/chats", page(chatJson("chat-1", "Project", "group")));
        onPost(messageJson("msg-sent", "2026-07-10T10:00:00Z", "hello"));

        String result = client.sendChatMessageByName("project", "hello");

        JSONObject json = new JSONObject(result);
        assertTrue(json.getBoolean("success"));
        assertEquals("Project", json.getString("chatName"));
        assertEquals("chat-1", json.getString("chatId"));
        assertEquals("msg-sent", json.getString("messageId"));
        assertEquals("2026-07-10T10:00:00Z", json.getString("createdDateTime"));
    }

    @Test
    void sendChatMessageByName_oneOnOneChatWithoutTopic() throws IOException {
        on("/me/chats", page(chatJson("chat-1", null, "oneOnOne")));
        onPost(messageJson("msg-sent", "2026-07-10T10:00:00Z", "hello"));

        String result = client.sendChatMessageByName("alice", "hello");

        JSONObject json = new JSONObject(result);
        assertTrue(json.getBoolean("success"));
        assertEquals("1-on-1 chat", json.getString("chatName"));
    }

    @Test
    void sendChatMessageByName_withHtmlContentType_postsHtmlBody() throws IOException {
        on("/me/chats", page(chatJson("chat-1", "Project", "group")));
        onPost(messageJson("msg-sent", "2026-07-10T10:00:00Z", "<b>hello</b>"));

        String result = client.sendChatMessageByName("project", "<b>hello</b>", "html");

        JSONObject json = new JSONObject(result);
        assertTrue(json.getBoolean("success"));
        JSONObject body = new JSONObject(postedRequests.get(0).getBody());
        assertEquals("html", body.getJSONObject("body").getString("contentType"));
        assertEquals("<b>hello</b>", body.getJSONObject("body").getString("content"));
    }

    @Test
    void getSelfChatMessages_usesSelfChatId() throws IOException {
        on("/me/chats/48:notes/messages", page(messageJson("note-1", "2026-07-10T10:00:00Z", "note")));

        List<ChatMessage> messages = client.getSelfChatMessages(10);

        assertEquals(1, messages.size());
        assertTrue(executedUrls.get(0).contains("/me/chats/48:notes/messages"));
    }

    @Test
    void getSelfChatMessagesSimple_returnsSimplifiedJson() throws IOException {
        on("/me/chats/48:notes/messages", page(messageJson("note-1", "2026-07-10T10:00:00Z", "note")));

        String result = client.getSelfChatMessagesSimple(10);

        JSONArray simplified = new JSONArray(result);
        assertEquals(1, simplified.length());
        assertEquals("note", simplified.getJSONObject(0).getString("body"));
    }

    @Test
    void sendSelfChatMessage_success() throws IOException {
        onPost(messageJson("note-sent", "2026-07-10T10:00:00Z", "remember this"));

        String result = client.sendSelfChatMessage("remember this");

        JSONObject json = new JSONObject(result);
        assertTrue(json.getBoolean("success"));
        assertEquals("Self Chat (Personal Notes)", json.getString("chatName"));
        assertEquals("48:notes", json.getString("chatId"));
        assertEquals("note-sent", json.getString("messageId"));
    }

    @Test
    void sendSelfChatMessage_withHtmlContentType_postsHtmlBody() throws IOException {
        onPost(messageJson("note-sent", "2026-07-10T10:00:00Z", "<b>remember this</b>"));

        String result = client.sendSelfChatMessage("<b>remember this</b>", "html");

        JSONObject json = new JSONObject(result);
        assertTrue(json.getBoolean("success"));
        JSONObject body = new JSONObject(postedRequests.get(0).getBody());
        assertEquals("html", body.getJSONObject("body").getString("contentType"));
        assertEquals("<b>remember this</b>", body.getJSONObject("body").getString("content"));
        assertTrue(postedRequests.get(0).url().contains("/me/chats/48:notes/messages"));
    }

    // ==================== File download ====================

    @Test
    void downloadFile_invalidUrlReturnsErrorJson() throws IOException {
        Path output = tempDir.resolve("nested/dir/file.txt");

        String result = client.downloadFile("not a valid url", output.toString());

        JSONObject json = new JSONObject(result);
        assertFalse(json.getBoolean("success"));
        assertNotNull(json.getString("error"));
        // parent directories are created before the download attempt
        assertTrue(Files.isDirectory(tempDir.resolve("nested/dir")));
    }

    // ==================== Hosted contents / transcripts ====================

    @Test
    void getMessageHostedContents_addsDownloadUrls() throws IOException {
        on("/hostedContents", page(
                new JSONObject().put("id", "content-1").put("contentType", "text/vtt").toString(),
                new JSONObject().put("contentType", "image/png").toString()));

        String result = client.getMessageHostedContents("chat-1", "msg-1");

        JSONArray contents = new JSONArray(result);
        assertEquals(2, contents.length());
        String downloadUrl = contents.getJSONObject(0).getString("downloadUrl");
        assertTrue(downloadUrl.contains("/chats/chat-1/messages/msg-1/hostedContents/content-1/$value"));
        assertFalse(contents.getJSONObject(1).has("downloadUrl"));
    }

    @Test
    void getMessageHostedContents_emptyValue() throws IOException {
        on("/hostedContents", "{}");

        assertEquals(0, new JSONArray(client.getMessageHostedContents("chat-1", "msg-1")).length());
    }

    @Test
    void getCallTranscripts_addsDownloadUrls() throws IOException {
        on("/communications/callRecords", page(new JSONObject().put("id", "transcript-1").toString()));

        String result = client.getCallTranscripts("call-1");

        JSONArray transcripts = new JSONArray(result);
        assertEquals(1, transcripts.length());
        assertTrue(transcripts.getJSONObject(0).getString("downloadUrl")
                .contains("/communications/callRecords/call-1/transcripts/transcript-1/content"));
    }

    @Test
    void getCallTranscripts_emptyValue() throws IOException {
        on("/communications/callRecords", new JSONObject().put("value", new JSONArray()).toString());

        assertEquals(0, new JSONArray(client.getCallTranscripts("call-1")).length());
    }

    @Test
    void searchUserDriveFiles_filtersFoldersAndAddsPath() throws IOException {
        on("/users/user-1/drive", page(
                new JSONObject()
                        .put("file", new JSONObject())
                        .put("name", "recording.mp4")
                        .put("size", 12345L)
                        .put("webUrl", "https://web")
                        .put("@microsoft.graph.downloadUrl", "https://download")
                        .put("id", "file-1")
                        .put("createdDateTime", "2026-07-01T00:00:00Z")
                        .put("lastModifiedDateTime", "2026-07-02T00:00:00Z")
                        .put("parentReference", new JSONObject().put("path", "/drive/root:/Recordings"))
                        .toString(),
                new JSONObject().put("folder", new JSONObject()).put("name", "Some folder").toString()));

        String result = client.searchUserDriveFiles("user-1", "transcript");

        JSONArray files = new JSONArray(result);
        assertEquals(1, files.length());
        JSONObject file = files.getJSONObject(0);
        assertEquals("recording.mp4", file.getString("name"));
        assertEquals(12345L, file.getLong("size"));
        assertEquals("/drive/root:/Recordings", file.getString("path"));
        assertTrue(executedUrls.get(0).contains("search(q="));
    }

    @Test
    void getRecordingTranscripts_noMediaReturnsEmptyArray() throws IOException {
        on("/drives/drive-1/items/item-1", new JSONObject().put("id", "item-1").toString());

        assertEquals(0, new JSONArray(client.getRecordingTranscripts("drive-1", "item-1")).length());
    }

    @Test
    void getRecordingTranscripts_withParentReference() throws IOException {
        on("/drives/drive-1/items/item-1", new JSONObject()
                .put("id", "item-1")
                .put("media", new JSONObject())
                .put("parentReference", new JSONObject()
                        .put("siteId", "site-1")
                        .put("path", "/drive/root:"))
                .toString());

        JSONArray result = new JSONArray(client.getRecordingTranscripts("drive-1", "item-1"));

        assertEquals(1, result.length());
        JSONObject info = result.getJSONObject(0);
        assertEquals("site-1", info.getString("siteId"));
        assertEquals("drive-1", info.getString("driveId"));
        assertEquals("item-1", info.getString("itemId"));
    }

    @Test
    void getRecordingTranscripts_exceptionReturnsErrorArray() throws IOException {
        on("/drives/drive-1/items/item-1", new IOException("access denied"));

        JSONArray result = new JSONArray(client.getRecordingTranscripts("drive-1", "item-1"));

        assertEquals(1, result.length());
        JSONObject error = result.getJSONObject(0);
        assertFalse(error.getBoolean("success"));
        assertTrue(error.getString("error").contains("access denied"));
    }

    @Test
    void listRecordingTranscripts_findsMediaOnFirstEndpoint() throws IOException {
        on("/drives/drive-1/items/item-1", new JSONObject()
                .put("id", "item-1")
                .put("media", new JSONObject().put("transcripts", new JSONArray()))
                .toString());

        String result = client.listRecordingTranscripts("drive-1", "item-1");

        JSONObject json = new JSONObject(result);
        assertTrue(json.getBoolean("success"));
        assertTrue(json.has("media"));
        assertEquals(1, executedUrls.size());
        assertTrue(executedUrls.get(0).contains("$expand=media"));
    }

    @Test
    void listRecordingTranscripts_allEndpointsFail() throws IOException {
        on("/drives/drive-1/items/item-1", new IOException("fail 1"));
        on("/drives/drive-1/items/item-1", new IOException("fail 2"));
        on("/drives/drive-1/items/item-1", new IOException("fail 3"));

        String result = client.listRecordingTranscripts("drive-1", "item-1");

        JSONObject json = new JSONObject(result);
        assertFalse(json.getBoolean("success"));
        assertEquals(3, json.getJSONArray("attempts").length());
        assertEquals(3, executedUrls.size());
    }

    @Test
    void listRecordingTranscripts_responseWithoutMediaContinues() throws IOException {
        on("/drives/drive-1/items/item-1", new JSONObject().put("id", "item-1").toString());
        on("/drives/drive-1/items/item-1", new JSONObject().put("id", "item-1").toString());
        on("/drives/drive-1/items/item-1", new JSONObject().put("id", "item-1").toString());

        String result = client.listRecordingTranscripts("drive-1", "item-1");

        JSONObject json = new JSONObject(result);
        assertFalse(json.getBoolean("success"));
        JSONArray attempts = json.getJSONArray("attempts");
        assertEquals(3, attempts.length());
        assertTrue(attempts.getJSONObject(0).getBoolean("success"));
    }

    @Test
    void extractTranscriptFromSharePoint_parsesTranscriptIds() throws IOException {
        String uuid1 = "11111111-2222-3333-4444-555555555555";
        String uuid2 = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        String html = "<html><body>"
                + "/media/transcripts/" + uuid1 + "/streamContent"
                + "/media/transcripts/" + uuid1 + "/streamContent" // duplicate
                + "/media/transcripts/" + uuid2 + "/streamContent"
                + "drives/drive-abc/items/item-xyz/streamContent"
                + "</body></html>";
        on("sharepoint.com", html);

        String result = client.extractTranscriptFromSharePoint("https://contoso.sharepoint.com/recording");

        JSONObject json = new JSONObject(result);
        assertTrue(json.getBoolean("success"));
        assertEquals(2, json.getInt("transcriptsFound"));
        assertEquals("drive-abc", json.getString("driveId"));
        assertEquals("item-xyz", json.getString("itemId"));
        assertEquals(html.length(), json.getInt("htmlLength"));
    }

    @Test
    void extractTranscriptFromSharePoint_noMatches() throws IOException {
        on("sharepoint.com", "<html>nothing here</html>");

        String result = client.extractTranscriptFromSharePoint("https://contoso.sharepoint.com/recording");

        JSONObject json = new JSONObject(result);
        assertTrue(json.getBoolean("success"));
        assertEquals(0, json.getInt("transcriptsFound"));
        assertFalse(json.has("driveId"));
        assertFalse(json.has("itemId"));
    }

    @Test
    void extractTranscriptFromSharePoint_exception() throws IOException {
        on("sharepoint.com", new IOException("timeout"));

        String result = client.extractTranscriptFromSharePoint("https://contoso.sharepoint.com/recording");

        JSONObject json = new JSONObject(result);
        assertFalse(json.getBoolean("success"));
        assertTrue(json.getString("error").contains("timeout"));
    }

    @Test
    void downloadRecordingTranscript_missingWebUrl() throws IOException {
        on("/drives/drive-1/items/item-1", new JSONObject().put("id", "item-1").toString());

        String result = client.downloadRecordingTranscript("drive-1", "item-1", "transcript-1",
                tempDir.resolve("out.vtt").toString());

        JSONObject json = new JSONObject(result);
        assertFalse(json.getBoolean("success"));
        assertTrue(json.getString("error").contains("Could not get webUrl"));
    }

    @Test
    void downloadRecordingTranscript_unparseableWebUrl() throws IOException {
        on("/drives/drive-1/items/item-1", new JSONObject()
                .put("id", "item-1")
                .put("webUrl", "https://contoso.sharepoint.com/sites/team/recording.mp4")
                .toString());

        String result = client.downloadRecordingTranscript("drive-1", "item-1", "transcript-1",
                tempDir.resolve("out.vtt").toString());

        JSONObject json = new JSONObject(result);
        assertFalse(json.getBoolean("success"));
        assertTrue(json.getString("error").contains("Could not extract SharePoint site URL"));
    }

    // ==================== Teams and channels ====================

    @Test
    void getJoinedTeams_pagination() throws IOException {
        on("/me/joinedTeams", pageWithNext(
                "https://graph.microsoft.com/v1.0/me/joinedTeams?$skiptoken=page2",
                teamJson("team-1", "Engineering")));
        on("/me/joinedTeams", page(teamJson("team-2", "Design")));

        List<Team> teams = client.getJoinedTeams();

        assertEquals(2, teams.size());
        assertEquals("team-2", teams.get(1).getId());
        assertEquals(2, executedUrls.size());
    }

    @Test
    void getTeamChannels_singlePage() throws IOException {
        on("/teams/team-1/channels", page(
                channelJson("chan-1", "General"), channelJson("chan-2", "Random")));

        List<Channel> channels = client.getTeamChannels("team-1");

        assertEquals(2, channels.size());
        assertEquals("General", channels.get(0).getDisplayName());
    }

    @Test
    void findTeamByName_serverSideFilterHit() throws IOException {
        on("/me/joinedTeams", page(teamJson("team-1", "Engineering"), teamJson("team-2", "Engineering Ops")));

        Team team = client.findTeamByName("Engineering");

        assertNotNull(team);
        assertEquals("team-1", team.getId());
        assertTrue(executedUrls.get(0).contains("$filter=startswith"));
        assertEquals(1, executedUrls.size());
    }

    @Test
    void findTeamByName_escapesQuotesInFilter() throws IOException {
        on("/me/joinedTeams", page(teamJson("team-1", "O'Brien's team")));

        Team team = client.findTeamByName("O'Brien");

        assertNotNull(team);
        assertTrue(executedUrls.get(0).contains("O%27%27Brien"));
    }

    @Test
    void findTeamByName_fallsBackToClientSideFiltering() throws IOException {
        // server-side filter returns nothing, then fallback fetches all teams
        on("/me/joinedTeams", page());
        on("/me/joinedTeams", page(
                teamJson("team-1", "Engineering Team"), teamJson("team-2", "Design")));

        Team team = client.findTeamByName("engineer");

        assertNotNull(team);
        assertEquals("team-1", team.getId());
        assertEquals(2, executedUrls.size());
    }

    @Test
    void findTeamByName_notFound() throws IOException {
        on("/me/joinedTeams", page());
        on("/me/joinedTeams", page(teamJson("team-2", "Design")));

        assertNull(client.findTeamByName("nonexistent"));
    }

    @Test
    void findChannelByName_match() throws IOException {
        on("/teams/team-1/channels", page(
                channelJson("chan-1", "General"), channelJson("chan-2", "Release Notes")));

        Channel channel = client.findChannelByName("team-1", "release");

        assertNotNull(channel);
        assertEquals("chan-2", channel.getId());
    }

    @Test
    void findChannelByName_multipleMatchesReturnsFirst() throws IOException {
        on("/teams/team-1/channels", page(
                channelJson("chan-1", "Dev General"), channelJson("chan-2", "Dev Random")));

        Channel channel = client.findChannelByName("team-1", "dev");

        assertNotNull(channel);
        assertEquals("chan-1", channel.getId());
    }

    @Test
    void findChannelByName_notFound() throws IOException {
        on("/teams/team-1/channels", page(channelJson("chan-1", "General")));

        assertNull(client.findChannelByName("team-1", "nonexistent"));
    }

    @Test
    void getChannelMessagesByName_teamNotFound() throws IOException {
        on("/me/joinedTeams", page());
        on("/me/joinedTeams", page());

        assertTrue(client.getChannelMessagesByName("nope", "general", 10).isEmpty());
    }

    @Test
    void getChannelMessagesByName_channelNotFound() throws IOException {
        on("/me/joinedTeams", page(teamJson("team-1", "Engineering")));
        on("/teams/team-1/channels", page());

        assertTrue(client.getChannelMessagesByName("engineering", "nope", 10).isEmpty());
    }

    @Test
    void getChannelMessagesByName_successWithPagination() throws IOException {
        on("/me/joinedTeams", page(teamJson("team-1", "Engineering")));
        on("/teams/team-1/channels", page(channelJson("chan-1", "General")));
        on("/teams/team-1/channels/chan-1/messages", pageWithNext(
                "https://graph.microsoft.com/v1.0/teams/team-1/channels/chan-1/messages?$skiptoken=page2",
                messageJson("msg-1", "2026-07-10T10:00:00Z", "one"),
                messageJson("msg-2", "2026-07-10T09:00:00Z", "two")));
        on("/teams/team-1/channels/chan-1/messages", page(
                messageJson("msg-3", "2026-07-10T08:00:00Z", "three")));

        List<ChatMessage> messages = client.getChannelMessagesByName("engineering", "general", 0);

        assertEquals(3, messages.size());
    }

    @Test
    void getChannelMessagesByName_respectsLimit() throws IOException {
        on("/me/joinedTeams", page(teamJson("team-1", "Engineering")));
        on("/teams/team-1/channels", page(channelJson("chan-1", "General")));
        on("/teams/team-1/channels/chan-1/messages", page(
                messageJson("msg-1", "2026-07-10T10:00:00Z", "one"),
                messageJson("msg-2", "2026-07-10T09:00:00Z", "two")));

        List<ChatMessage> messages = client.getChannelMessagesByName("engineering", "general", 1);

        assertEquals(1, messages.size());
        String messagesUrl = executedUrls.stream()
                .filter(url -> url.contains("/messages"))
                .findFirst()
                .orElse("");
        assertTrue(messagesUrl.contains("$top=1"));
    }
}
