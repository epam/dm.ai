package com.github.istin.dmtools.ai.bedrock.auth;

import okhttp3.Request;

import java.util.Map;

/**
 * Authentication strategy using Bearer Token (Bedrock API Keys).
 */
public class BearerTokenAuthenticationStrategy implements BedrockAuthenticationStrategy {
    
    private final String bearerToken;
    
    public BearerTokenAuthenticationStrategy(String bearerToken) {
        this.bearerToken = bearerToken;
    }
    
    @Override
    public Request.Builder sign(Request.Builder builder, Map<String, String> customHeaders) {
        // Add Bearer token authentication
        if (bearerToken != null && !bearerToken.trim().isEmpty()) {
            builder = builder.header("Authorization", "Bearer " + bearerToken);
        }
        
        // Add custom headers if provided
        if (customHeaders != null && !customHeaders.isEmpty()) {
            for (Map.Entry<String, String> header : customHeaders.entrySet()) {
                builder = builder.header(header.getKey(), header.getValue());
            }
        }
        
        return builder;
    }
    
    @Override
    public Request signRequest(Request.Builder requestBuilder, String url, String body, String region, Map<String, String> customHeaders) {
        // For Bearer Token, signing is done in sign() method, just build the request
        return sign(requestBuilder, customHeaders).build();
    }
    
    @Override
    public String getAuthenticationType() {
        return "BEARER_TOKEN";
    }
}

