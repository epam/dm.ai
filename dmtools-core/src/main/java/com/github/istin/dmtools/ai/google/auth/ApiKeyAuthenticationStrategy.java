package com.github.istin.dmtools.ai.google.auth;

import okhttp3.HttpUrl;
import okhttp3.Request;

import java.util.Map;

/**
 * API Key authentication strategy for Gemini public API.
 * Adds the API key as a query parameter: ?key={GEMINI_API_KEY}
 */
public class ApiKeyAuthenticationStrategy implements GeminiAuthenticationStrategy {

    private final String apiKey;

    public ApiKeyAuthenticationStrategy(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        this.apiKey = apiKey;
    }

    @Override
    public Request.Builder sign(Request.Builder builder, Map<String, String> customHeaders) {
        // For API key mode, the key is added as a query parameter in signRequest
        // Add custom headers if any
        if (customHeaders != null) {
            customHeaders.forEach(builder::addHeader);
        }
        return builder;
    }

    @Override
    public Request signRequest(Request.Builder requestBuilder, String url, String body, Map<String, String> customHeaders) {
        // Add API key as query parameter
        HttpUrl originalUrl = HttpUrl.parse(url);
        if (originalUrl == null) {
            throw new IllegalArgumentException("Invalid URL: " + url);
        }

        HttpUrl urlWithKey = originalUrl.newBuilder()
                .addQueryParameter("key", apiKey)
                .build();

        requestBuilder.url(urlWithKey);

        // Add custom headers
        if (customHeaders != null) {
            customHeaders.forEach(requestBuilder::addHeader);
        }

        return requestBuilder.build();
    }

    @Override
    public String getAuthenticationType() {
        return "API_KEY";
    }
}
