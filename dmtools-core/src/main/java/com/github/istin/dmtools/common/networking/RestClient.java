package com.github.istin.dmtools.common.networking;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.Okio;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;

public interface RestClient {

    String execute(GenericRequest jiraRequest) throws IOException;

    String execute(String url) throws IOException;

    String post(GenericRequest jiraRequest) throws IOException;

    String put(GenericRequest jiraRequest) throws IOException;

    String patch(GenericRequest jiraRequest) throws IOException;

    String delete(GenericRequest jiraRequest) throws IOException;

    String getBasePath();

    String path(String path);

    Request.Builder sign(Request.Builder builder);

    OkHttpClient getClient();

    class Impl {

        private static final Logger logger = LogManager.getLogger(Impl.class);

        public static File downloadFile(RestClient restClient, GenericRequest genericRequest, File downloadedFile) throws IOException {
            String url = genericRequest.url();
            logger.info(url);
            if (downloadedFile.exists()) {
                return downloadedFile;
            }
            OkHttpClient client = restClient.getClient();
            try (Response response = client.newCall(restClient.sign(
                            new Request.Builder())
                    .url(url)
//                    .header("User-Agent", "DMTools")
                    .build()
            ).execute()) {
                // Check if the response is successful
                if (response.isSuccessful() && response.body() != null) {
                    // Get the response body
                    ResponseBody responseBody = response.body();
                    // Create a file to save the downloaded data
                    try (BufferedSink sink = Okio.buffer(Okio.sink(downloadedFile))) {
                        // Write the response body to the file
                        sink.writeAll(responseBody.source());
                    }

                    logger.info("Download successful: {}", downloadedFile.getAbsolutePath());
                    return downloadedFile;
                } else {
                    throw new IOException(response.code() + " " + response.body());
                }
            } finally {
                client.connectionPool().evictAll();
            }
        }

        public static String getFileImageExtension(String url) {
            String imageExtension = "";

            if (url.endsWith(".png")) {
                imageExtension = ".png";
            } else if (url.endsWith(".jpg") || url.endsWith(".jpeg")) {
                imageExtension = ".jpg";
            } else if (url.endsWith(".gif")) {
                imageExtension = ".gif";
            } else if (url.endsWith(".bmp")) {
                imageExtension = ".bmp";
            } else if (url.endsWith(".svg")) {
                imageExtension = ".svg";
            } else if (url.endsWith(".webp")) {
                imageExtension = ".webp";
            }
            return imageExtension;
        }
    }

    class RestClientException extends IOException {

        public static final String BACKUP_503 = "backup:503";
        public static final String NO_SUCH_PARENT_EPICS = "No issues have a parent epic with key or name:400";
        private final String body;

        public int getCode() {
            return code;
        }

        private final int code;

        public RestClientException(String message, String body, int code) {
            super(message);
            this.body = body;
            this.code = code;
        }

        public String getBody() {
            return body;
        }
    }

    class RateLimitException extends RestClientException{

        private final Response response;

        public RateLimitException(String message, String body, Response response, int code) {
            super(message, body, code);
            this.response = response;
        }

        public Response getResponse() {
            return response;
        }
    }
}
