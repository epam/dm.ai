package com.github.istin.dmtools.ai.bedrock.auth;

import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
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
 * Authentication strategy using AWS IAM Access Keys with Signature V4.
 */
public class IAMKeysAuthenticationStrategy implements BedrockAuthenticationStrategy {
    
    private static final Logger logger = LogManager.getLogger(IAMKeysAuthenticationStrategy.class);
    
    private final String accessKeyId;
    private final String secretAccessKey;
    private final String sessionToken;
    
    public IAMKeysAuthenticationStrategy(String accessKeyId, String secretAccessKey, String sessionToken) {
        if (accessKeyId == null || accessKeyId.trim().isEmpty()) {
            throw new IllegalArgumentException("Access Key ID is required for IAM_KEYS authentication");
        }
        if (secretAccessKey == null || secretAccessKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Secret Access Key is required for IAM_KEYS authentication");
        }
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.sessionToken = sessionToken;
    }
    
    @Override
    public Request.Builder sign(Request.Builder builder, Map<String, String> customHeaders) {
        // For IAM Keys, signing happens in signRequest() method using AWS Signature V4
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
            // Get AWS credentials
            AwsCredentials credentials;
            
            if (sessionToken != null && !sessionToken.trim().isEmpty()) {
                credentials = AwsSessionCredentials.create(
                    accessKeyId, 
                    secretAccessKey, 
                    sessionToken
                );
            } else {
                credentials = AwsBasicCredentials.create(
                    accessKeyId, 
                    secretAccessKey
                );
            }
            
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
            logger.error("Failed to sign request with AWS Signature V4: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to sign request: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getAuthenticationType() {
        return "IAM_KEYS";
    }
}

