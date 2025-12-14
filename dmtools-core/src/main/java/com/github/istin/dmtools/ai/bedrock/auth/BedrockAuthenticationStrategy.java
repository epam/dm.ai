package com.github.istin.dmtools.ai.bedrock.auth;

import okhttp3.Request;

import java.util.Map;

/**
 * Interface for AWS Bedrock authentication strategies.
 * Different implementations handle Bearer Token, IAM Keys, or Default Credentials.
 */
public interface BedrockAuthenticationStrategy {
    
    /**
     * Signs a request builder with authentication headers.
     * For Bearer Token, this adds the Authorization header.
     * For IAM-based auth, this may be a no-op as signing happens in post() method.
     * 
     * @param builder The request builder to sign
     * @param customHeaders Custom headers to add
     * @return The signed request builder
     */
    Request.Builder sign(Request.Builder builder, Map<String, String> customHeaders);
    
    /**
     * Signs a complete request using AWS Signature V4 if needed.
     * This is called from the post() method for IAM-based authentication.
     * For Bearer Token, this should return the request as-is.
     * 
     * @param requestBuilder The request builder
     * @param url The request URL
     * @param body The request body
     * @param region The AWS region
     * @param customHeaders Custom headers to add
     * @return The signed request
     */
    Request signRequest(Request.Builder requestBuilder, String url, String body, String region, Map<String, String> customHeaders);
    
    /**
     * Returns the authentication type name for logging/debugging.
     * 
     * @return The authentication type name
     */
    String getAuthenticationType();
}

