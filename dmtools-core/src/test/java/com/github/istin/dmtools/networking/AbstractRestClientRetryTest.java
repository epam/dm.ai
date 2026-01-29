package com.github.istin.dmtools.networking;

import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.common.networking.RestClient;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AbstractRestClientRetryTest {

    private static final Logger logger = LogManager.getLogger(AbstractRestClientRetryTest.class);
    private TestRestClient restClient;
    private OkHttpClient mockClient;
    private Call mockCall;

    // Test implementation of AbstractRestClient
    private static class TestRestClient extends AbstractRestClient {
        public TestRestClient(String basePath, String authorization) throws IOException {
            super(basePath, authorization);
        }

        @Override
        public String path(String path) {
            return getBasePath() + path;
        }

        @Override
        public Request.Builder sign(Request.Builder builder) {
            return builder.header("Authorization", "Bearer test-token");
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        restClient = new TestRestClient("https://api.example.com", "test-auth");
        mockClient = mock(OkHttpClient.class);
        mockCall = mock(Call.class);

        // Configure custom retry policy with shorter delays for testing
        RetryPolicy testRetryPolicy = new RetryPolicy(
            3,      // maxRetries
            100,    // baseDelayMs (short for testing)
            1000,   // maxDelayMs
            2.0,    // backoffMultiplier
            0.1,    // jitterFactor (low for predictable testing)
            logger
        );
        restClient.setRetryPolicy(testRetryPolicy);
    }

    @Test
    @DisplayName("Should retry on rate limit and eventually succeed")
    void testRetryOnRateLimitSuccess() throws IOException {
        // Create a mock response sequence: rate limit, rate limit, success
        AtomicInteger callCount = new AtomicInteger(0);

        // Use reflection to replace the client for testing
        try {
            java.lang.reflect.Field clientField = AbstractRestClient.class.getDeclaredField("client");
            clientField.setAccessible(true);

            when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
            when(mockCall.execute()).thenAnswer(invocation -> {
                int count = callCount.incrementAndGet();
                if (count <= 2) {
                    // First two calls return rate limit
                    Response response = new Response.Builder()
                        .request(new Request.Builder().url("https://api.example.com/test").build())
                        .protocol(Protocol.HTTP_1_1)
                        .code(429)
                        .message("Too Many Requests")
                        .header("Retry-After", "1")
                        .body(ResponseBody.create(MediaType.parse("text/plain"), "Rate limit exceeded"))
                        .build();
                    return response;
                } else {
                    // Third call succeeds
                    Response response = new Response.Builder()
                        .request(new Request.Builder().url("https://api.example.com/test").build())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(MediaType.parse("application/json"), "{\"result\":\"success\"}"))
                        .build();
                    return response;
                }
            });

            clientField.set(restClient, mockClient);

            // Execute request - should retry and eventually succeed
            GenericRequest request = new GenericRequest(restClient, "https://api.example.com/test");
            String result = restClient.execute(request);

            assertEquals("{\"result\":\"success\"}", result);
            assertEquals(3, callCount.get(), "Should have made 3 attempts");
        } catch (Exception e) {
            fail("Test setup failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Should throw exception after max retries exceeded")
    void testMaxRetriesExceeded() throws IOException {
        // Create a mock that always returns rate limit
        try {
            java.lang.reflect.Field clientField = AbstractRestClient.class.getDeclaredField("client");
            clientField.setAccessible(true);

            when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
            when(mockCall.execute()).thenAnswer(invocation -> {
                Response response = new Response.Builder()
                    .request(new Request.Builder().url("https://api.example.com/test").build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(429)
                    .message("Too Many Requests")
                    .body(ResponseBody.create(MediaType.parse("text/plain"), "Rate limit exceeded"))
                    .build();
                return response;
            });

            clientField.set(restClient, mockClient);

            // Execute request - should fail after max retries
            GenericRequest request = new GenericRequest(restClient, "https://api.example.com/test");

            assertThrows(RestClient.RateLimitException.class, () -> {
                restClient.execute(request);
            });

            // Verify that we made the expected number of attempts
            verify(mockCall, times(3)).execute(); // 3 attempts with retry policy maxRetries=3
        } catch (Exception e) {
            if (e instanceof RestClient.RateLimitException) {
                throw (RestClient.RateLimitException) e;
            }
            fail("Test setup failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Should respect Retry-After header in response")
    void testRetryAfterHeader() throws IOException {
        AtomicInteger callCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        try {
            java.lang.reflect.Field clientField = AbstractRestClient.class.getDeclaredField("client");
            clientField.setAccessible(true);

            when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
            when(mockCall.execute()).thenAnswer(invocation -> {
                int count = callCount.incrementAndGet();
                if (count == 1) {
                    // First call returns rate limit with Retry-After header
                    Response response = new Response.Builder()
                        .request(new Request.Builder().url("https://api.example.com/test").build())
                        .protocol(Protocol.HTTP_1_1)
                        .code(429)
                        .message("Too Many Requests")
                        .header("Retry-After", "2") // 2 seconds
                        .body(ResponseBody.create(MediaType.parse("text/plain"), "Rate limit exceeded"))
                        .build();
                    return response;
                } else {
                    // Second call succeeds
                    Response response = new Response.Builder()
                        .request(new Request.Builder().url("https://api.example.com/test").build())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(MediaType.parse("application/json"), "{\"result\":\"success\"}"))
                        .build();
                    return response;
                }
            });

            clientField.set(restClient, mockClient);

            GenericRequest request = new GenericRequest(restClient, "https://api.example.com/test");
            String result = restClient.execute(request);

            long elapsedTime = System.currentTimeMillis() - startTime;

            assertEquals("{\"result\":\"success\"}", result);
            assertEquals(2, callCount.get());
            // Should have waited approximately 2 seconds (with some jitter)
            assertTrue(elapsedTime >= 1500, "Should have waited at least 1.5 seconds");
        } catch (Exception e) {
            fail("Test setup failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Should not retry on non-retryable errors")
    void testNoRetryOnNonRetryableError() throws IOException {
        AtomicInteger callCount = new AtomicInteger(0);

        try {
            java.lang.reflect.Field clientField = AbstractRestClient.class.getDeclaredField("client");
            clientField.setAccessible(true);

            when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
            when(mockCall.execute()).thenAnswer(invocation -> {
                callCount.incrementAndGet();
                // Return a non-retryable error (401 Unauthorized)
                Response response = new Response.Builder()
                    .request(new Request.Builder().url("https://api.example.com/test").build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body(ResponseBody.create(MediaType.parse("text/plain"), "Authentication required"))
                    .build();
                return response;
            });

            clientField.set(restClient, mockClient);

            GenericRequest request = new GenericRequest(restClient, "https://api.example.com/test");

            assertThrows(RestClient.RestClientException.class, () -> {
                restClient.execute(request);
            });

            // Should only make one attempt (no retries)
            assertEquals(1, callCount.get());
        } catch (Exception e) {
            if (e instanceof RestClient.RestClientException) {
                throw (RestClient.RestClientException) e;
            }
            fail("Test setup failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("POST requests should also retry on rate limits")
    void testPostRetryOnRateLimit() throws IOException {
        AtomicInteger callCount = new AtomicInteger(0);

        try {
            java.lang.reflect.Field clientField = AbstractRestClient.class.getDeclaredField("client");
            clientField.setAccessible(true);

            when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
            when(mockCall.execute()).thenAnswer(invocation -> {
                int count = callCount.incrementAndGet();
                if (count == 1) {
                    // First call returns rate limit
                    Response response = new Response.Builder()
                        .request(new Request.Builder().url("https://api.example.com/test").build())
                        .protocol(Protocol.HTTP_1_1)
                        .code(429)
                        .message("Too Many Requests")
                        .body(ResponseBody.create(MediaType.parse("text/plain"), "Rate limit exceeded"))
                        .build();
                    return response;
                } else {
                    // Second call succeeds
                    Response response = new Response.Builder()
                        .request(new Request.Builder().url("https://api.example.com/test").build())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(MediaType.parse("application/json"), "{\"id\":\"123\"}"))
                        .build();
                    return response;
                }
            });

            clientField.set(restClient, mockClient);

            GenericRequest request = new GenericRequest(restClient, "https://api.example.com/test");
            request.setBody("{\"name\":\"test\"}");
            String result = restClient.post(request);

            assertEquals("{\"id\":\"123\"}", result);
            assertEquals(2, callCount.get(), "Should have made 2 attempts");
        } catch (Exception e) {
            fail("Test setup failed: " + e.getMessage());
        }
    }
}