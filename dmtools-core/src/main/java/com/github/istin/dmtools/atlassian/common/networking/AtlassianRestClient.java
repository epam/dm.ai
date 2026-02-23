package com.github.istin.dmtools.atlassian.common.networking;

import com.github.istin.dmtools.networking.AbstractRestClient;
import lombok.Getter;
import lombok.Setter;
import okhttp3.Request;

import java.io.IOException;

public abstract class AtlassianRestClient extends AbstractRestClient {

    @Setter
    @Getter
    private String authType = "Basic";

    public AtlassianRestClient(String basePath, String authorization) throws IOException {
        super(basePath, authorization);
    }

    @Override
    public Request.Builder sign(Request.Builder builder) {
        return builder
                .header("Authorization", authType + " " + authorization)
                .header("X-Atlassian-Token", "nocheck")
                .header("Content-Type", "application/json")
                ;
    }
}