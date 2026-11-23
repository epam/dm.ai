// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai.openai;

import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.networking.GenericRequest;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class OpenAIClientExtraBodyTest {

    private static final String BASE_PATH = "http://llama.local/v1/chat/completions";
    private static final String RESPONSE = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}";

    private OpenAIClient newClient(double temperature) throws IOException {
        return new OpenAIClient(BASE_PATH, "not-used", "qwen", 1024, temperature, "max_tokens", null, null);
    }

    private JSONObject sentBody(OpenAIClient client) throws Exception {
        OpenAIClient spy = Mockito.spy(client);
        doReturn(RESPONSE).when(spy).post(any(GenericRequest.class));
        assertEquals("ok", spy.chat("Hi"));
        ArgumentCaptor<GenericRequest> captor = ArgumentCaptor.forClass(GenericRequest.class);
        verify(spy).post(captor.capture());
        return new JSONObject(captor.getValue().getBody());
    }

    @Test
    public void noExtraBodyKeepsDefaultRequest() throws Exception {
        JSONObject body = sentBody(newClient(0.5));

        assertEquals(0.5, body.getDouble("temperature"), 0.0001);
        assertEquals(1024, body.getInt("max_tokens"));
        assertFalse(body.has("top_k"));
    }

    @Test
    public void extraBodyFieldsAreMergedIncludingNestedObjects() throws Exception {
        OpenAIClient client = newClient(1.0);
        client.setExtraBody("{\"top_k\":20,\"min_p\":0.0,\"chat_template_kwargs\":{\"preserve_thinking\":true}}");

        JSONObject body = sentBody(client);

        assertEquals(20, body.getInt("top_k"));
        assertEquals(0.0, body.getDouble("min_p"), 0.0001);
        assertTrue(body.getJSONObject("chat_template_kwargs").getBoolean("preserve_thinking"));
        assertEquals(1.0, body.getDouble("temperature"), 0.0001);
    }

    @Test
    public void extraBodyOverridesTemperatureAndMaxTokens() throws Exception {
        OpenAIClient client = newClient(0.1);
        client.setExtraBody("{\"temperature\":0.9,\"max_tokens\":8}");

        JSONObject body = sentBody(client);

        assertEquals(0.9, body.getDouble("temperature"), 0.0001);
        assertEquals(8, body.getInt("max_tokens"));
    }

    @Test
    public void extraBodyCannotOverrideModelOrMessages() throws Exception {
        OpenAIClient client = newClient(-1);
        client.setExtraBody("{\"model\":\"other\",\"messages\":[],\"top_k\":5}");

        JSONObject body = sentBody(client);

        assertEquals("qwen", body.getString("model"));
        assertEquals(1, body.getJSONArray("messages").length());
        assertEquals(5, body.getInt("top_k"));
    }

    @Test
    public void blankNullOrUnresolvedValueClearsExtraBody() throws Exception {
        OpenAIClient client = newClient(-1);
        client.setExtraBody("{\"top_k\":5}");
        client.setExtraBody("   ");
        assertNull(client.getExtraBody());

        client.setExtraBody("{\"top_k\":5}");
        client.setExtraBody(null);
        assertNull(client.getExtraBody());

        client.setExtraBody("${OPENAI_EXTRA_BODY}");
        assertNull(client.getExtraBody());
        assertFalse(sentBody(client).has("top_k"));
    }

    @Test
    public void invalidJsonIsRejected() throws Exception {
        OpenAIClient client = newClient(-1);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> client.setExtraBody("top_k=20"));
        assertTrue(e.getMessage().contains("OPENAI_EXTRA_BODY"));
        assertThrows(IllegalArgumentException.class, () -> client.setExtraBody("[1,2]"));
    }

    @Test
    public void getExtraBodyReturnsDefensiveCopy() throws Exception {
        OpenAIClient client = newClient(-1);
        client.setExtraBody("{\"top_k\":5}");

        client.getExtraBody().put("top_k", 99);

        assertEquals(5, client.getExtraBody().getInt("top_k"));
    }

    @Test
    public void basicOpenAIReadsExtraBodyFromConfiguration() throws Exception {
        ApplicationConfiguration configuration = mock(ApplicationConfiguration.class);
        when(configuration.getOpenAIBasePath()).thenReturn(BASE_PATH);
        when(configuration.getOpenAIApiKey()).thenReturn("not-used");
        when(configuration.getOpenAIModel()).thenReturn("qwen");
        when(configuration.getOpenAIMaxTokens()).thenReturn(1024);
        when(configuration.getOpenAITemperature()).thenReturn(1.0);
        when(configuration.getOpenAIMaxTokensParamName()).thenReturn("max_tokens");
        when(configuration.getOpenAIExtraBody()).thenReturn("{\"top_k\":20}");

        BasicOpenAI client = new BasicOpenAI(null, configuration);

        assertEquals(20, client.getExtraBody().getInt("top_k"));
    }
}
