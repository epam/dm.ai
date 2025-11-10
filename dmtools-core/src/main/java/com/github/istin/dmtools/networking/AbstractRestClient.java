package com.github.istin.dmtools.networking;

import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import okhttp3.*;
import org.apache.commons.codec.digest.DigestUtils;
import java.util.Arrays;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class AbstractRestClient implements RestClient {
    private static final Logger logger = LogManager.getLogger(AbstractRestClient.class);
    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");
    protected final OkHttpClient client;
    protected String basePath;
    protected String authorization;
    protected boolean isClearCache = false;
    private boolean isCacheGetRequestsEnabled = true;

    public boolean isCachePostRequestsEnabled() {
        return isCachePostRequestsEnabled;
    }

    public void setCachePostRequestsEnabled(boolean cachePostRequestsEnabled) {
        isCachePostRequestsEnabled = cachePostRequestsEnabled;
    }

    @Override
    public OkHttpClient getClient() {
        return client;
    }

    private boolean isCachePostRequestsEnabled = false;
    private boolean isWaitBeforePerform = false;
    private HashMap<String, Long> timeMeasurement = new HashMap<>();

    public AbstractRestClient(String basePath, String authorization) throws IOException {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(getTimeout(), TimeUnit.SECONDS)
                .writeTimeout(getTimeout(), TimeUnit.SECONDS)
                .readTimeout(getTimeout(), TimeUnit.SECONDS)
                .protocols(Arrays.asList(Protocol.HTTP_1_1)) // Force HTTP/1.1 to avoid HTTP/2 protocol issues
                // Enhanced connection pool for cloud environments
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                // Add connection retry on failure
                .retryOnConnectionFailure(true)
                .build();
        this.basePath = basePath;
        this.authorization = authorization;

        reinitCache();
    }

    public int getTimeout() {
        // Check if running in cloud environment and adjust timeout accordingly
        String cloudEnv = System.getenv("GOOGLE_CLOUD_PROJECT");
        boolean isCloudEnvironment = cloudEnv != null && !cloudEnv.isEmpty();
        
        if (isCloudEnvironment) {
            // Cloud environments may need longer timeouts due to network latency
            int cloudTimeout = 120; // 2 minutes for cloud
            logger.debug("Detected cloud environment ({}), using extended timeout: {}s", cloudEnv, cloudTimeout);
            return cloudTimeout;
        } else {
            // Local development timeout
            return 60;
        }
    }

    /**
     * Determines if a connection error is recoverable and should be retried.
     * This helps distinguish between temporary network issues and permanent configuration problems.
     * 
     * @param exception The connection exception that occurred
     * @return true if the error is likely recoverable with retry, false otherwise
     */
    protected boolean isRecoverableConnectionError(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        
        String lowerMessage = message.toLowerCase();
        
        // Recoverable connection errors - typically temporary network issues
        return lowerMessage.contains("broken pipe") ||
               lowerMessage.contains("connection reset") ||
               lowerMessage.contains("connection refused") ||
               lowerMessage.contains("timeout") ||
               lowerMessage.contains("network is unreachable") ||
               lowerMessage.contains("host is unreachable") ||
               lowerMessage.contains("connection timed out") ||
               lowerMessage.contains("connection lost") ||
               lowerMessage.contains("socket closed") ||
               lowerMessage.contains("premature eof") ||
               lowerMessage.contains("unexpected end of stream") ||
               lowerMessage.contains("end of stream") ||
               lowerMessage.contains("remote host terminated the handshake") ||
               lowerMessage.contains("handshake") ||
               lowerMessage.contains("ssl") ||
               exception instanceof java.net.SocketTimeoutException ||
               exception instanceof java.net.ConnectException;
    }

    private void reinitCache() throws IOException {
        File cache = new File(getCacheFolderName());
        logger.info("cache folder: {}", cache.getAbsolutePath());
        if (isClearCache) {
            cache.mkdirs();
            FileUtils.deleteDirectory(cache);
        }
    }

    /**
     * Sanitizes a URL by redacting sensitive query parameters to prevent credential exposure in logs.
     * 
     * @param url The URL to sanitize
     * @return The sanitized URL with sensitive parameters redacted
     */
    public static String sanitizeUrl(String url) {
        if (url == null) {
            return null;
        }
        
        try {
            // Define sensitive parameter names that should be redacted
            String[] sensitiveParams = {"key", "token", "password", "secret", "apikey", "api_key", "access_token", "auth", "authorization"};
            
            String result = url;
            
            // Check if URL contains query parameters
            if (result.contains("?")) {
                String[] urlParts = result.split("\\?", 2);
                String baseUrl = urlParts[0];
                String queryString = urlParts[1];
                
                // Process each query parameter
                StringBuilder sanitizedQuery = new StringBuilder();
                String[] params = queryString.split("&");
                
                for (int i = 0; i < params.length; i++) {
                    String param = params[i];
                    String[] keyValue = param.split("=", 2);
                    
                    if (keyValue.length == 2) {
                        String paramName = keyValue[0].toLowerCase();
                        String paramValue = keyValue[1];
                        
                        // Check if this is a sensitive parameter
                        boolean isSensitive = false;
                        for (String sensitiveParam : sensitiveParams) {
                            if (paramName.equals(sensitiveParam) || paramName.contains(sensitiveParam)) {
                                isSensitive = true;
                                break;
                            }
                        }
                        
                        if (isSensitive) {
                            sanitizedQuery.append(keyValue[0]).append("=***REDACTED***");
                        } else {
                            sanitizedQuery.append(param);
                        }
                    } else {
                        // Parameter without value
                        sanitizedQuery.append(param);
                    }
                    
                    if (i < params.length - 1) {
                        sanitizedQuery.append("&");
                    }
                }
                
                result = baseUrl + "?" + sanitizedQuery.toString();
            }
            
            return result;
        } catch (Exception e) {
            // If sanitization fails, return a safe fallback
            logger.warn("Failed to sanitize URL, using safe fallback: {}", e.getMessage());
            return url.contains("?") ? url.split("\\?")[0] + "?[QUERY_PARAMS_REDACTED]" : url;
        }
    }

    public static IOException printAndCreateException(Request request, Response response) throws IOException {
        int code = response.code();
        String body = response.body() != null ? response.body().string() : "";
        if (code == 503) {
            return new IOException(RestClientException.BACKUP_503);
        } else if (code == 400) {
            if (body.contains("No issues have a parent epic with key or name")) {
                return new IOException(RestClientException.NO_SUCH_PARENT_EPICS);
            }
        } else if (body.contains("rate")) {
            logger.error(body);
            return new RateLimitException("rate limit", body, response, code);
        }
        String sanitizedUrl = sanitizeUrl(request.url().toString());
        String responseError = "printAndCreateException error: " + sanitizedUrl + "\n" + body + "\n" + response.message() + "\n" + code;
        logger.error(responseError);
        return new RestClient.RestClientException(responseError, body, code);
    }

    public void setClearCache(boolean clearCache) throws IOException {
        if (clearCache != isClearCache) {
            isClearCache = clearCache;
            reinitCache();
        }
    }

    public void setCacheGetRequestsEnabled(boolean cacheGetRequestsEnabled) {
        isCacheGetRequestsEnabled = cacheGetRequestsEnabled;
    }

    public void clearCache(GenericRequest jiraRequest) {
        if (isCacheGetRequestsEnabled) {
            File cachedFile = getCachedFile(jiraRequest);
            if (cachedFile.exists()) {
                cachedFile.delete();
            }
        }
    }

    protected void clearRequestIfExpired(GenericRequest genericRequest, Long updated) throws IOException {
        File cachedFile = getCachedFile(genericRequest);
        clearRequestIfExpired(genericRequest, updated, cachedFile);
    }

    protected void clearRequestIfExpired(GenericRequest genericRequest, Long updated, File cachedFile) throws IOException {
        if (cachedFile.exists() && updated != null) {
            BasicFileAttributes attr = Files.readAttributes(cachedFile.toPath(), BasicFileAttributes.class);
            FileTime fileTime = attr.lastModifiedTime();
            if (fileTime.toMillis() < updated) {
                clearCache(genericRequest);
            }
        }
    }

    public static String resolveRedirect(RestClient restClient, String urlString) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .followRedirects(false)  // Don't follow redirects automatically
                .build();

        String currentUrl = urlString;
        int maxRedirects = 10; // Prevent infinite redirect loops
        int redirectCount = 0;

        while (redirectCount < maxRedirects) {
            Request.Builder builder = new Request.Builder();
            restClient.sign(builder);
            Request request = builder
                    .url(currentUrl)
                    .method("HEAD", null) // Use HEAD instead of GET to only fetch headers
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isRedirect()) {
                    return currentUrl;
                }

                String nextUrl = response.header("Location");
                if (nextUrl == null) {
                    throw new IOException("Redirect location is missing");
                }

                // If the Location header contains a relative URL, resolve it against the current URL
                if (!nextUrl.startsWith("http")) {
                    URL base = new URL(currentUrl);
                    nextUrl = new URL(base, nextUrl).toString();
                }

                currentUrl = nextUrl;
                redirectCount++;
            }
        }

        throw new IOException("Too many redirects (max: " + maxRedirects + ") for URL: " + urlString);
    }

    public File getCachedFile(GenericRequest genericRequest) {
        String value = getCacheFileName(genericRequest);
        return new File(getCacheFolderName() + "/" + value);
    }

    @Override
    public String getBasePath() {
        return basePath;
    }

    @Override
    public String execute(GenericRequest genericRequest) throws IOException {
        if (genericRequest == null) {
            return "";
        }
        String url = genericRequest.url();
        return execute(url, true, genericRequest.isIgnoreCache(), genericRequest);
    }

    @Override
    public String execute(String url) throws IOException {
        return execute(url, true, false, new GenericRequest(this, url));
    }

    private Request.Builder applyHeaders(Request.Builder builder, GenericRequest genericRequest) {
        for (String key : genericRequest.getHeaders().keySet()) {
            builder.header(key, genericRequest.getHeaders().get(key));
        }
        return builder;
    }

    private String execute(String url, boolean isRepeatIfFails, boolean isIgnoreCache, GenericRequest genericRequest) throws IOException {
        return execute(url, isRepeatIfFails, isIgnoreCache, genericRequest, 0);
    }
    
    private String execute(String url, boolean isRepeatIfFails, boolean isIgnoreCache, GenericRequest genericRequest, int retryCount) throws IOException {
        try {
            timeMeasurement.put(url, System.currentTimeMillis());
            if (isCacheGetRequestsEnabled && !isIgnoreCache) {
                String value = getCacheFileName(genericRequest);
                File cache = new File(getCacheFolderName());
                cache.mkdirs();
                File cachedFile = new File(getCacheFolderName() + "/" + value);
                if (cachedFile.exists()) {
                    return FileUtils.readFileToString(cachedFile);
                }
            }
            if (isWaitBeforePerform) {
                try {
                    Thread.currentThread().sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            try {
                Request request = applyHeaders(sign(new Request.Builder())
                        .header("User-Agent", "DMTools"), genericRequest)
                        .url(url)
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String result = response.body() != null ? response.body().string() : null;
                        if (isCacheGetRequestsEnabled) {
                            String value = getCacheFileName(genericRequest);
                            File cache = new File(getCacheFolderName());
                            cache.mkdirs();
                            File cachedFile = new File(getCacheFolderName() + "/" + value);
                            FileUtils.writeStringToFile(cachedFile, result);
                        }
                        return result;
                    } else {
                        throw AbstractRestClient.printAndCreateException(request, response);
                    }
                }
            } catch (IOException e) {
                logger.warn("Connection error for URL: {} - Error: {} (Attempt: {}/3)", url, e.getMessage(), retryCount + 1);
                
                // Check if it's a recoverable connection error
                boolean isRecoverableError = isRecoverableConnectionError(e);
                
                // Maximum of 3 attempts (2 retries)
                final int MAX_RETRIES = 2;
                
                if (isRepeatIfFails && isRecoverableError && retryCount < MAX_RETRIES) {
                    logger.info("Retrying request after connection error: {} (Retry {}/{})", e.getClass().getSimpleName(), retryCount + 1, MAX_RETRIES);
                    if (isWaitBeforePerform) {
                        try {
                            // Exponential backoff: 200ms, 400ms, 800ms
                            long waitTime = 200L * (long) Math.pow(2, retryCount);
                            Thread.sleep(waitTime);
                        } catch (InterruptedException interruptedException) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Request interrupted during retry", interruptedException);
                        }
                    }
                    return execute(url, false, isIgnoreCache, genericRequest, retryCount + 1);
                } else {
                    if (!isRecoverableError) {
                        logger.error("Non-recoverable connection error for URL: {}", url, e);
                    } else if (retryCount >= MAX_RETRIES) {
                        logger.error("Max retries ({}) exceeded for URL: {}. Final error: {}", MAX_RETRIES, url, e.getMessage());
                    }
                    throw e;
                }
            }
        } finally {
            Long prevTime = timeMeasurement.get(url);
            long time = System.currentTimeMillis() - 200 - prevTime;
            logger.info("{} {}", time, url);
            // Let OkHttp manage connection lifecycle automatically
        }
    }

    @NotNull
    protected String getCacheFileName(GenericRequest genericRequest) {
        StringBuilder hashValue = new StringBuilder(genericRequest.url());
        if (genericRequest.getBody() != null) {
            hashValue.append(genericRequest.getBody());
        }
        Map<String, String> headers = genericRequest.getHeaders();
        if (!headers.isEmpty()) {
            for (String key : headers.keySet()) {
                hashValue.append(key).append(":").append(headers.get(key));
            }
        }
        return DigestUtils.md5Hex(hashValue.toString());
    }

    protected String getCacheFolderName() {
        return "cache" + getClass().getSimpleName();
    }

    @Override
    public String post(GenericRequest genericRequest) throws IOException {
        return post(genericRequest, 0);
    }
    
    private String post(GenericRequest genericRequest, int retryCount) throws IOException {
        if (genericRequest == null) {
            return "";
        }
        String url = genericRequest.url();

        if (isWaitBeforePerform) {
            try {
                Thread.currentThread().sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (isCachePostRequestsEnabled && !genericRequest.isIgnoreCache()) {
            String value = getCacheFileName(genericRequest);
            File cache = new File(getCacheFolderName());
            cache.mkdirs();
            File cachedFile = new File(getCacheFolderName() + "/" + value);
            if (cachedFile.exists()) {
                logger.info("Read From Cache: ");
                return FileUtils.readFileToString(cachedFile);
            } else {
                logger.info("Network Request: ");
            }
        } else {
            logger.info("Network Request: ");
        }

        RequestBody body = RequestBody.create(JSON, genericRequest.getBody());
        Request request = applyHeaders(sign(
                new Request.Builder())
                .url(url)
                .header("User-Agent", "DMTools")
                , genericRequest)
                .post(body)
                .build();
        
        long startTime = System.currentTimeMillis();
        logger.debug("POST request starting for URL: {} (attempt: {})", url, retryCount + 1);
        
        try (Response response = client.newCall(request).execute()) {
            long responseTime = System.currentTimeMillis() - startTime;
            logger.debug("POST response received for URL: {} in {}ms, status: {}", url, responseTime, response.code());
            
            if (response.isSuccessful()) {
                String responseAsString = response.body() != null ? response.body().string() : "";
                logger.debug("POST success for URL: {} ({}ms, {} chars response)", url, responseTime, responseAsString.length());
                
                if (isCachePostRequestsEnabled) {
                    String value = getCacheFileName(genericRequest);
                    File cache = new File(getCacheFolderName());
                    cache.mkdirs();
                    File cachedFile = new File(getCacheFolderName() + "/" + value);
                    FileUtils.writeStringToFile(cachedFile, responseAsString);
                }
                return responseAsString;
            } else {
                logger.warn("POST failed for URL: {} ({}ms, status: {})", url, responseTime, response.code());
                throw AbstractRestClient.printAndCreateException(request, response);
            }
        } catch (IOException e) {
            logger.warn("POST connection error for URL: {} - Error: {} (Attempt: {}/3)", url, e.getMessage(), retryCount + 1);
            
            // Check if it's a recoverable connection error
            boolean isRecoverableError = isRecoverableConnectionError(e);
            
            // Maximum of 3 attempts (2 retries)
            final int MAX_RETRIES = 2;
            
            if (isRecoverableError && retryCount < MAX_RETRIES) {
                logger.info("Retrying POST request after connection error: {} (Retry {}/{})", e.getClass().getSimpleName(), retryCount + 1, MAX_RETRIES);
                if (isWaitBeforePerform) {
                    try {
                        // Exponential backoff: 200ms, 400ms, 800ms
                        long waitTime = 200L * (long) Math.pow(2, retryCount);
                        logger.debug("Waiting {}ms before retry", waitTime);
                        Thread.sleep(waitTime);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw new IOException("POST request interrupted during retry", interruptedException);
                    }
                }
                return post(genericRequest, retryCount + 1);
            } else {
                if (!isRecoverableError) {
                    logger.error("Non-recoverable POST connection error for URL: {}", url, e);
                } else if (retryCount >= MAX_RETRIES) {
                    logger.error("Max POST retries ({}) exceeded for URL: {}. Final error: {}", MAX_RETRIES, url, e.getMessage());
                }
                throw e;
            }
        } finally {
            // Removed aggressive connection pool eviction - let OkHttp manage connection lifecycle
        }
    }

    protected @NotNull String buildHashForPostRequest(GenericRequest genericRequest, String url) {
        return url + genericRequest.getBody();
    }

    @Override
    public String put(GenericRequest genericRequest) throws IOException {
        return put(genericRequest, 0);
    }
    
    private String put(GenericRequest genericRequest, int retryCount) throws IOException {
        String url = genericRequest.url();
        if (isWaitBeforePerform) {
            try {
                Thread.currentThread().sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        RequestBody body = RequestBody.create(JSON, genericRequest.getBody());
        Request request = applyHeaders(sign(
                new Request.Builder())
                .url(url)
                .header("User-Agent", "DMTools"), genericRequest)
                .put(body)
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return response.body() != null ? response.body().string() : "";
            } else {
                throw AbstractRestClient.printAndCreateException(request, response);
            }
        } catch (IOException e) {
            logger.warn("PUT connection error for URL: {} - Error: {} (Attempt: {}/3)", url, e.getMessage(), retryCount + 1);
            
            // Check if it's a recoverable connection error
            boolean isRecoverableError = isRecoverableConnectionError(e);
            
            // Maximum of 3 attempts (2 retries)
            final int MAX_RETRIES = 2;
            
            if (isRecoverableError && retryCount < MAX_RETRIES) {
                logger.info("Retrying PUT request after connection error: {} (Retry {}/{})", e.getClass().getSimpleName(), retryCount + 1, MAX_RETRIES);
                try {
                    // Exponential backoff: 200ms, 400ms, 800ms
                    long waitTime = 200L * (long) Math.pow(2, retryCount);
                    logger.debug("Waiting {}ms before PUT retry", waitTime);
                    Thread.sleep(waitTime);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IOException("PUT request interrupted during retry", interruptedException);
                }
                return put(genericRequest, retryCount + 1);
            } else {
                if (!isRecoverableError) {
                    logger.error("Non-recoverable PUT connection error for URL: {}", url, e);
                } else if (retryCount >= MAX_RETRIES) {
                    logger.error("Max PUT retries ({}) exceeded for URL: {}. Final error: {}", MAX_RETRIES, url, e.getMessage());
                }
                throw e;
            }
        } finally {
            // Let OkHttp manage connection lifecycle automatically
        }
    }

    @Override
    public String patch(GenericRequest genericRequest) throws IOException {
        return patch(genericRequest, 0);
    }
    
    private String patch(GenericRequest genericRequest, int retryCount) throws IOException {
        String url = genericRequest.url();
        if (isWaitBeforePerform) {
            try {
                Thread.currentThread().sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        RequestBody body = RequestBody.create(JSON, genericRequest.getBody());
        Request request = applyHeaders(sign(
                new Request.Builder())
                .url(url)
                .header("User-Agent", "DMTools"), genericRequest)
                .patch(body)
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return response.body() != null ? response.body().string() : "";
            } else {
                throw AbstractRestClient.printAndCreateException(request, response);
            }
        } catch (IOException e) {
            logger.warn("PATCH connection error for URL: {} - Error: {} (Attempt: {}/3)", url, e.getMessage(), retryCount + 1);
            
            // Check if it's a recoverable connection error
            boolean isRecoverableError = isRecoverableConnectionError(e);
            
            // Maximum of 3 attempts (2 retries)
            final int MAX_RETRIES = 2;
            
            if (isRecoverableError && retryCount < MAX_RETRIES) {
                logger.info("Retrying PATCH request after connection error: {} (Retry {}/{})", e.getClass().getSimpleName(), retryCount + 1, MAX_RETRIES);
                try {
                    // Exponential backoff: 200ms, 400ms, 800ms
                    long waitTime = 200L * (long) Math.pow(2, retryCount);
                    logger.debug("Waiting {}ms before PATCH retry", waitTime);
                    Thread.sleep(waitTime);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IOException("PATCH request interrupted during retry", interruptedException);
                }
                return patch(genericRequest, retryCount + 1);
            } else {
                if (!isRecoverableError) {
                    logger.error("Non-recoverable PATCH connection error for URL: {}", url, e);
                } else if (retryCount >= MAX_RETRIES) {
                    logger.error("Max PATCH retries ({}) exceeded for URL: {}. Final error: {}", MAX_RETRIES, url, e.getMessage());
                }
                throw e;
            }
        } finally {
            // Let OkHttp manage connection lifecycle automatically
        }
    }

    @Override
    public String delete(GenericRequest genericRequest) throws IOException {
        String url = genericRequest.url();
        if (isWaitBeforePerform) {
            try {
                Thread.currentThread().sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        String jiraRequestBody = genericRequest.getBody();
        RequestBody body = null;
        if (jiraRequestBody != null) {
            body = RequestBody.create(JSON, jiraRequestBody);
        }
        Request request = applyHeaders(sign(
                new Request.Builder())
                .url(url)
                .header("User-Agent", "DMTools"), genericRequest)
                .delete(body)
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return response.body() != null ? response.body().string() : "";
            } else {
                throw AbstractRestClient.printAndCreateException(request, response);
            }
        } finally {
            // Removed aggressive connection pool eviction - let OkHttp manage connection lifecycle
        }
    }

    public void setWaitBeforePerform(boolean waitBeforePerform) {
        isWaitBeforePerform = waitBeforePerform;
    }

    /**
     * Manually cleanup connection pool when absolutely necessary.
     * Should only be called when shutting down the client or in exceptional circumstances.
     * Normal operation should rely on OkHttp's built-in connection management.
     */
    public void cleanupConnectionPool() {
        client.connectionPool().evictAll();
        logger.debug("Connection pool manually cleaned up");
    }

    public interface Performer<T> {

        boolean perform(T model) throws Exception;

    }
}
