// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.atlassian.rovo;

import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import com.github.istin.dmtools.networking.AbstractRestClient;
import lombok.Getter;
import lombok.Setter;
import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Atlassian Rovo remote MCP client ({@code https://mcp.atlassian.com/v2/mcp}).
 *
 * <p>Sends JSON-RPC 2.0 {@code tools/call} requests over HTTP POST and exposes
 * wrapper MCP tools for semantic search, Confluence AI summaries, Teamwork Graph
 * context, and Agentic Q&amp;A (with automatic fallback to standard search when
 * Agentic mode returns HTTP 403 or is disabled for the site/user).
 */
public class RovoMcpClient extends AbstractRestClient {

    public static final String DEFAULT_MCP_URL = "https://mcp.atlassian.com/v2/mcp";

    private static final Logger logger = LogManager.getLogger(RovoMcpClient.class);

    private final AtomicInteger requestIdCounter = new AtomicInteger(1);

    @Getter
    @Setter
    private String authType = "Basic";

    @Getter
    @Setter
    private String defaultCloudId;

    public RovoMcpClient(String basePath, String authorization) throws IOException {
        this(basePath, authorization, "Basic", null);
    }

    public RovoMcpClient(String basePath, String authorization, String authType, String defaultCloudId) throws IOException {
        super(normalizeBasePath(basePath), authorization);
        this.authType = normalizeAuthType(authType);
        this.defaultCloudId = trimToNull(defaultCloudId);
        setClearCache(false);
        setCacheGetRequestsEnabled(false);
        setCachePostRequestsEnabled(false);
    }

    private static String normalizeBasePath(String basePath) {
        if (basePath == null || basePath.trim().isEmpty()) {
            return DEFAULT_MCP_URL;
        }
        return basePath.trim();
    }

    private static String normalizeAuthType(String authType) {
        if (authType == null || authType.trim().isEmpty()) {
            return "Basic";
        }
        String trimmed = authType.trim();
        if ("Bearer".equalsIgnoreCase(trimmed)) {
            return "Bearer";
        }
        if ("Basic".equalsIgnoreCase(trimmed)) {
            return "Basic";
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public String path(String path) {
        if (path == null || path.isEmpty()) {
            return getBasePath();
        }
        String base = getBasePath();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (path.startsWith("/")) {
            return base + path;
        }
        return base + "/" + path;
    }

    @Override
    public synchronized Request.Builder sign(Request.Builder builder) {
        String authHeaderValue = buildAuthHeaderValue();
        if (authHeaderValue != null) {
            builder.header("Authorization", authHeaderValue);
        }
        return builder
                .header("Accept", "application/json, text/event-stream")
                .header("Content-Type", "application/json");
    }

    String buildAuthHeaderValue() {
        if (authorization == null || authorization.trim().isEmpty()) {
            return null;
        }
        String token = authorization.trim();
        if (token.regionMatches(true, 0, "Basic ", 0, 6)
                || token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return token;
        }
        return normalizeAuthType(authType) + " " + token;
    }

    /**
     * Resolves the effective Atlassian Cloud ID from an explicit parameter or
     * the configured {@code ROVO_CLOUD_ID} default.
     */
    public String resolveCloudId(String cloudId) {
        String explicit = trimToNull(cloudId);
        return explicit != null ? explicit : defaultCloudId;
    }

    /**
     * Builds a JSON-RPC 2.0 {@code tools/call} payload for the given remote MCP tool.
     */
    public JSONObject buildToolCallRequest(String remoteToolName, JSONObject arguments) {
        JSONObject params = new JSONObject();
        params.put("name", remoteToolName);
        params.put("arguments", arguments != null ? arguments : new JSONObject());

        JSONObject rpc = new JSONObject();
        rpc.put("jsonrpc", "2.0");
        rpc.put("id", requestIdCounter.getAndIncrement());
        rpc.put("method", "tools/call");
        rpc.put("params", params);
        return rpc;
    }

    /**
     * Invokes a remote tool on the Atlassian Rovo MCP server via JSON-RPC 2.0 {@code tools/call}.
     */
    public String callMcpTool(String remoteToolName, JSONObject arguments) throws IOException {
        JSONObject payload = buildToolCallRequest(remoteToolName, arguments);
        GenericRequest request = new GenericRequest(this, getBasePath());
        request.setBody(payload.toString());
        String rawResponse = post(request);
        return unwrapMcpResponse(rawResponse);
    }

    /**
     * Unwraps a JSON-RPC 2.0 / SSE response from the Atlassian MCP endpoint.
     * Throws {@link IOException} if the JSON-RPC response contains an {@code error} object
     * or if {@code result.isError} is {@code true}.
     */
    String unwrapMcpResponse(String rawResponse) throws IOException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            return "";
        }
        String jsonCandidate = extractJsonFromSseIfNeeded(rawResponse.trim());
        JSONObject root;
        try {
            root = new JSONObject(jsonCandidate);
        } catch (Exception e) {
            return rawResponse;
        }

        if (root.has("error") && !root.isNull("error")) {
            Object errObj = root.get("error");
            if (errObj instanceof JSONObject) {
                JSONObject errJson = (JSONObject) errObj;
                int code = errJson.optInt("code", -1);
                String message = errJson.optString("message", errJson.toString());
                throw new RestClient.RestClientException(
                        "Rovo MCP error (" + code + "): " + message,
                        errJson.toString(),
                        code
                );
            }
            throw new IOException("Rovo MCP error: " + errObj);
        }

        if (root.has("result") && !root.isNull("result")) {
            Object resultObj = root.get("result");
            if (resultObj instanceof JSONObject) {
                JSONObject resultJson = (JSONObject) resultObj;
                if (resultJson.optBoolean("isError", false)) {
                    String errText = extractContentText(resultJson);
                    int statusCode = (errText != null && isAgenticForbiddenText(errText)) ? 403 : -1;
                    throw new RestClient.RestClientException(
                            "Rovo MCP tool error: " + (errText != null ? errText : resultJson.toString()),
                            resultJson.toString(),
                            statusCode
                    );
                }
                String extracted = extractSingleTextIfApplicable(resultJson);
                if (extracted != null) {
                    return extracted;
                }
                return resultJson.toString();
            }
            return String.valueOf(resultObj);
        }

        return rawResponse;
    }

    private static String extractJsonFromSseIfNeeded(String body) {
        if (body.startsWith("{") || body.startsWith("[")) {
            return body;
        }
        String lastDataLine = null;
        for (String line : body.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("data:")) {
                String data = trimmed.substring("data:".length()).trim();
                if (data.startsWith("{")) {
                    lastDataLine = data;
                }
            }
        }
        return lastDataLine != null ? lastDataLine : body;
    }

    private static String extractContentText(JSONObject resultJson) {
        JSONArray content = resultJson.optJSONArray("content");
        if (content == null || content.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            JSONObject item = content.optJSONObject(i);
            if (item != null && item.has("text")) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(item.optString("text", ""));
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static String extractSingleTextIfApplicable(JSONObject resultJson) {
        JSONArray content = resultJson.optJSONArray("content");
        if (content != null && content.length() == 1 && resultJson.keySet().size() == 1) {
            JSONObject first = content.optJSONObject(0);
            if (first != null && "text".equals(first.optString("type")) && first.has("text")) {
                return first.optString("text");
            }
        }
        return null;
    }

    private static boolean isAgenticFallbackEligible(Exception e) {
        if (e instanceof RestClient.RestClientException) {
            int code = ((RestClient.RestClientException) e).getCode();
            if (code == 403) {
                return true;
            }
        }
        String msg = e.getMessage();
        return msg != null && isAgenticForbiddenText(msg);
    }

    private static boolean isAgenticForbiddenText(String text) {
        String lower = text.toLowerCase();
        return lower.contains("403")
                || lower.contains("agentic search is not enabled")
                || lower.contains("not enabled for this user or site")
                || lower.contains("forbidden");
    }

    @MCPTool(
            name = "rovo_search",
            description = "Semantic natural-language search across Jira, Confluence, and connected third-party sources via Atlassian Rovo MCP in STANDARD mode.",
            integration = "rovo",
            category = "search"
    )
    public String rovoSearch(
            @MCPParam(name = "query", description = "Natural-language search query", required = true, example = "deployment architecture for payment service")
            String query,
            @MCPParam(name = "cloudId", description = "Atlassian Cloud ID (optional, defaults to ROVO_CLOUD_ID)", required = false, example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
            String cloudId,
            @MCPParam(name = "targetApp", description = "Optional target application filter (e.g. confluence, jira)", required = false, example = "confluence")
            String targetApp,
            @MCPParam(name = "limit", description = "Maximum number of results to return", required = false, example = "10")
            Integer limit) throws IOException {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Parameter 'query' is required");
        }
        JSONObject args = new JSONObject();
        args.put("query", query.trim());
        args.put("mode", "STANDARD");
        String effectiveCloudId = resolveCloudId(cloudId);
        if (effectiveCloudId != null) {
            args.put("cloudId", effectiveCloudId);
        }
        String cleanTargetApp = trimToNull(targetApp);
        if (cleanTargetApp != null) {
            args.put("targetApp", cleanTargetApp);
        }
        if (limit != null && limit > 0) {
            args.put("limit", limit);
        }
        return callMcpTool("search", args);
    }

    @MCPTool(
            name = "rovo_summarize",
            description = "Fetch a pre-generated AI summary of a Confluence page via Atlassian Rovo MCP (getConfluenceContent with detail=ai_summary).",
            integration = "rovo",
            category = "confluence"
    )
    public String rovoSummarize(
            @MCPParam(name = "contentId", description = "Confluence page or content ID", required = true, example = "123456789")
            String contentId,
            @MCPParam(name = "cloudId", description = "Atlassian Cloud ID (optional, defaults to ROVO_CLOUD_ID)", required = false, example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
            String cloudId) throws IOException {
        if (contentId == null || contentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Parameter 'contentId' is required");
        }
        JSONObject args = new JSONObject();
        args.put("contentId", contentId.trim());
        args.put("detail", "ai_summary");
        String effectiveCloudId = resolveCloudId(cloudId);
        if (effectiveCloudId != null) {
            args.put("cloudId", effectiveCloudId);
        }
        return callMcpTool("getConfluenceContent", args);
    }

    @MCPTool(
            name = "rovo_context",
            description = "Retrieve Atlassian Teamwork Graph relationships (linked Jira issues, Confluence pages, pull requests, repositories, goals, and people) for an object via getGraphContext.",
            integration = "rovo",
            category = "graph"
    )
    public String rovoContext(
            @MCPParam(name = "objectType", description = "Type of the Atlassian object (e.g. jira_issue, confluence_page, pull_request, team, goal)", required = true, example = "jira_issue")
            String objectType,
            @MCPParam(name = "identifier", description = "Identifier or ARI/key of the object (e.g. PROJ-123 or page ID)", required = true, example = "PROJ-123")
            String identifier,
            @MCPParam(name = "cloudId", description = "Atlassian Cloud ID (optional, defaults to ROVO_CLOUD_ID)", required = false, example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
            String cloudId) throws IOException {
        if (objectType == null || objectType.trim().isEmpty()) {
            throw new IllegalArgumentException("Parameter 'objectType' is required");
        }
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Parameter 'identifier' is required");
        }
        JSONObject args = new JSONObject();
        args.put("objectType", objectType.trim());
        args.put("identifier", identifier.trim());
        String effectiveCloudId = resolveCloudId(cloudId);
        if (effectiveCloudId != null) {
            args.put("cloudId", effectiveCloudId);
        }
        return callMcpTool("getGraphContext", args);
    }

    @MCPTool(
            name = "rovo_ask",
            description = "Ask a natural-language question via Atlassian Rovo MCP using AGENTIC search mode; automatically falls back to STANDARD semantic search when Agentic search returns 403 or is not enabled.",
            integration = "rovo",
            category = "qa"
    )
    public String rovoAsk(
            @MCPParam(name = "question", description = "Natural-language question to ask Rovo", required = true, example = "How does the authentication token refresh flow work?")
            String question,
            @MCPParam(name = "cloudId", description = "Atlassian Cloud ID (optional, defaults to ROVO_CLOUD_ID)", required = false, example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
            String cloudId) throws IOException {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("Parameter 'question' is required");
        }
        JSONObject args = new JSONObject();
        args.put("query", question.trim());
        args.put("mode", "AGENTIC");
        String effectiveCloudId = resolveCloudId(cloudId);
        if (effectiveCloudId != null) {
            args.put("cloudId", effectiveCloudId);
        }
        try {
            return callMcpTool("search", args);
        } catch (IOException e) {
            if (isAgenticFallbackEligible(e)) {
                logger.info("Rovo AGENTIC search unavailable ({}); falling back to STANDARD search", e.getMessage());
                String fallbackResult = rovoSearch(question.trim(), cloudId, null, null);
                return wrapFallbackResult(question.trim(), fallbackResult);
            }
            throw e;
        }
    }

    private static String wrapFallbackResult(String question, String standardSearchResult) {
        if (standardSearchResult != null && standardSearchResult.trim().startsWith("{")) {
            try {
                JSONObject json = new JSONObject(standardSearchResult);
                if (!json.has("mode")) {
                    json.put("mode", "STANDARD_FALLBACK");
                }
                if (!json.has("question")) {
                    json.put("question", question);
                }
                return json.toString();
            } catch (Exception ignored) {
                // Return raw string if not valid JSON object
            }
        }
        return standardSearchResult;
    }
}
