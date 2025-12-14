package com.github.istin.dmtools.ai.bedrock.auth;

import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Authentication strategy using AWS DefaultCredentialsProvider (reads from ~/.aws/credentials).
 */
public class DefaultCredentialsAuthenticationStrategy implements BedrockAuthenticationStrategy {
    
    private static final Logger logger = LogManager.getLogger(DefaultCredentialsAuthenticationStrategy.class);
    
    @Override
    public Request.Builder sign(Request.Builder builder, Map<String, String> customHeaders) {
        // For Default Credentials, signing happens in signRequest() method using AWS Signature V4
        // Here we only add custom headers
        if (customHeaders != null && !customHeaders.isEmpty()) {
            for (Map.Entry<String, String> header : customHeaders.entrySet()) {
                builder = builder.header(header.getKey(), header.getValue());
            }
        }
        return builder;
    }
    
    @Override
    public Request signRequest(Request.Builder requestBuilder, String url, String body, String region, Map<String, String> customHeaders) {
        try {
            // Get credentials from DefaultCredentialsProvider
            AwsCredentials credentials = 
                DefaultCredentialsProvider.create()
                    .resolveCredentials();
            
            // Create AWS SDK HttpFullRequest for signing
            SdkHttpFullRequest.Builder sdkRequestBuilder = 
                SdkHttpFullRequest.builder()
                    .uri(URI.create(url))
                    .method(SdkHttpMethod.POST)
                    .putHeader("Content-Type", "application/json");
            
            // Add body
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            sdkRequestBuilder.contentStreamProvider(() -> new ByteArrayInputStream(bodyBytes));
            
            SdkHttpFullRequest sdkRequest = sdkRequestBuilder.build();
            
            // Create signer
            Aws4Signer signer = Aws4Signer.create();
            
            // Parameters for signing
            Aws4SignerParams signerParams = 
                Aws4SignerParams.builder()
                    .awsCredentials(credentials)
                    .signingName("bedrock")  // Service name for Bedrock
                    .signingRegion(Region.of(region))
                    .build();
            
            // Sign the request
            SdkHttpFullRequest signedRequest = signer.sign(sdkRequest, signerParams);
            
            // Transfer signed headers to OkHttp Request.Builder
            signedRequest.headers().forEach((name, values) -> {
                values.forEach(value -> requestBuilder.addHeader(name, value));
            });
            
            // Add custom headers
            sign(requestBuilder, customHeaders);
            
            return requestBuilder.build();
            
        } catch (Exception e) {
            logger.error("Failed to sign request with AWS Signature V4 using DefaultCredentialsProvider: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to sign request: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getAuthenticationType() {
        return "DEFAULT_CREDENTIALS";
    }
}

