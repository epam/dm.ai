// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.microsoft.ado;

import com.github.istin.dmtools.common.networking.GenericRequest;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tracker-agnostic operations on Azure DevOps (epam/dm.ai#661): update_field, set_priority,
 * get_field_code, attach_file and create-with-parent. All HTTP is mocked at the OkHttp layer.
 */
class AzureDevOpsTrackerOpsTest {

    private AzureDevOpsClient client;
    private OkHttpClient okHttp;
    private final List<Request> sent = new ArrayList<>();

    private static Response ok(Request request, String body) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(body, MediaType.parse("application/json")))
                .build();
    }

    /** Responds with the next canned body for each outgoing call and records the requests. */
    private void respondWith(String... bodies) throws IOException {
        okHttp = mock(OkHttpClient.class);
        when(okHttp.connectionPool()).thenReturn(new okhttp3.ConnectionPool());
        int[] idx = {0};
        when(okHttp.newCall(any())).thenAnswer(inv -> {
            Request request = inv.getArgument(0);
            sent.add(request);
            String body = bodies[Math.min(idx[0]++, bodies.length - 1)];
            Call call = mock(Call.class);
            when(call.execute()).thenReturn(ok(request, body));
            return call;
        });
    }

    private static String bodyOf(Request request) throws IOException {
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return buffer.readUtf8();
    }

    @BeforeEach
    void setUp() throws IOException {
        client = new AzureDevOpsClient("TestOrg", "TestProject", "fake-pat") {
            @Override
            public OkHttpClient getClient() {
                return okHttp;
            }

            @Override
            public String execute(GenericRequest request) throws IOException {
                // read path used by performTicket / fields lookup: route through the mocked OkHttp
                Request req = new Request.Builder().url(request.url()).build();
                try (Response r = okHttp.newCall(req).execute()) {
                    return r.body().string();
                }
            }
        };
    }

    // ---- ado_update_field ----

    @Test
    void updateField_customReferenceName_patchesThatField() throws IOException {
        respondWith("{\"id\":7}");

        client.updateField("7", "Custom.SolutionDesign", "the design");

        assertEquals("PATCH", sent.get(0).method());
        JSONObject op = new JSONArray(bodyOf(sent.get(0))).getJSONObject(0);
        assertEquals("/fields/Custom.SolutionDesign", op.getString("path"));
        assertEquals("the design", op.getString("value"));
    }

    @Test
    void updateField_humanNameIsMappedToReferenceName() throws IOException {
        respondWith("{}", "{}", "{}");

        client.updateField("7", "summary", "New title");
        client.updateField("7", "priority", "2");
        client.updateField("7", "tags", "a; b");

        assertEquals("/fields/System.Title", new JSONArray(bodyOf(sent.get(0))).getJSONObject(0).getString("path"));
        assertEquals("/fields/Microsoft.VSTS.Common.Priority", new JSONArray(bodyOf(sent.get(1))).getJSONObject(0).getString("path"));
        assertEquals("/fields/System.Tags", new JSONArray(bodyOf(sent.get(2))).getJSONObject(0).getString("path"));
    }

    // ---- ado_set_priority ----

    @Test
    void setPriority_mapsJiraNamesAndNumbers() throws IOException {
        respondWith("{}");
        client.setPriority("7", "High");
        client.setPriority("7", "Lowest");
        client.setPriority("7", "3");
        client.setPriority("7", "Blocker");

        int[] expected = {2, 4, 3, 1};
        for (int i = 0; i < expected.length; i++) {
            JSONObject op = new JSONArray(bodyOf(sent.get(i))).getJSONObject(0);
            assertEquals("/fields/Microsoft.VSTS.Common.Priority", op.getString("path"));
            assertEquals(expected[i], op.getInt("value"), "call " + i);
        }
    }

    @Test
    void setPriority_unknownName_failsWithTheAllowedList() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> AzureDevOpsClient.mapPriority("Whatever"));
        assertTrue(e.getMessage().contains("Unknown priority 'Whatever'"));
        assertTrue(e.getMessage().contains("Blocker"));
        assertThrows(IllegalArgumentException.class, () -> AzureDevOpsClient.mapPriority(" "));
        assertThrows(IllegalArgumentException.class, () -> AzureDevOpsClient.mapPriority(null));
        assertEquals(1, AzureDevOpsClient.mapPriority("1"));
        assertEquals(2, AzureDevOpsClient.mapPriority("Major"));
    }

    // ---- ado_get_field_code ----

    @Test
    void getFieldCode_resolvesHumanNameCaseInsensitively_andReturnsNullWhenUnknown() throws IOException {
        String fields = new JSONObject().put("value", new JSONArray()
                .put(new JSONObject().put("name", "Solution Design").put("referenceName", "Custom.SolutionDesign"))
                .put(new JSONObject().put("name", "Title").put("referenceName", "System.Title"))).toString();
        respondWith(fields, fields, fields);

        assertEquals("Custom.SolutionDesign", client.getFieldCode("TestProject", "solution design"));
        assertEquals("System.Title", client.getFieldCode(null, "System.Title"));
        assertNull(client.getFieldCode("TestProject", "No Such Field"));
        assertNull(client.getFieldCode("TestProject", " "));
        assertTrue(sent.get(0).url().toString().contains("/TestProject/_apis/wit/fields"), sent.get(0).url().toString());
    }

    // ---- ado_attach_file ----

    @Test
    void attachFile_uploadsBytesThenLinksAnAttachedFileRelation(@TempDir Path dir) throws IOException {
        File file = dir.resolve("report.png").toFile();
        Files.write(file.toPath(), new byte[]{1, 2, 3});
        // 1) performTicket (no attachments), 2) upload -> {url}, 3) PATCH relation
        respondWith("{\"id\":7,\"fields\":{\"System.Title\":\"t\"}}",
                "{\"id\":\"abc\",\"url\":\"https://dev.azure.com/TestOrg/_apis/wit/attachments/abc\"}",
                "{\"id\":7}");

        client.attachFileToTicket("7", "report.png", "image/png", file);

        Request upload = sent.stream().filter(r -> r.url().encodedPath().endsWith("/_apis/wit/attachments")).findFirst().orElseThrow();
        assertEquals("POST", upload.method());
        assertEquals("report.png", upload.url().queryParameter("fileName"));
        assertEquals(3, upload.body().contentLength());

        Request link = sent.get(sent.size() - 1);
        assertEquals("PATCH", link.method());
        JSONObject op = new JSONArray(bodyOf(link)).getJSONObject(0);
        assertEquals("/relations/-", op.getString("path"));
        assertEquals("AttachedFile", op.getJSONObject("value").getString("rel"));
        assertEquals("https://dev.azure.com/TestOrg/_apis/wit/attachments/abc", op.getJSONObject("value").getString("url"));
        assertEquals("report.png", op.getJSONObject("value").getJSONObject("attributes").getString("name"));
    }

    @Test
    void attachFile_skipsWhenAnAttachmentWithTheSameNameExists(@TempDir Path dir) throws IOException {
        File file = dir.resolve("report.png").toFile();
        Files.write(file.toPath(), new byte[]{1});
        String existing = new JSONObject().put("id", 7)
                .put("fields", new JSONObject().put("System.Title", "t")
                        .put("System.Description", "<img src=\"https://dev.azure.com/TestOrg/_apis/wit/attachments/x?fileName=report.png\">"))
                .put("relations", new JSONArray().put(new JSONObject().put("rel", "AttachedFile")
                        .put("url", "https://dev.azure.com/TestOrg/_apis/wit/attachments/x")
                        .put("attributes", new JSONObject().put("name", "report.png")))).toString();
        respondWith(existing);

        client.attachFileToTicket("7", "report.png", "image/png", file);

        boolean uploaded = sent.stream().anyMatch(r -> r.method().equals("POST"));
        assertFalse(uploaded, "no second upload when the name is already attached");
    }

    @Test
    void attachFile_missingFile_failsBeforeAnyRequest() {
        assertThrows(IOException.class, () -> client.attachFile("7", "x.png", "image/png", "/nope/x.png"));
        assertTrue(sent.isEmpty());
    }

    @Test
    void attachFile_toolReturnsSuccessJson(@TempDir Path dir) throws IOException {
        File file = dir.resolve("a.txt").toFile();
        Files.write(file.toPath(), new byte[]{9});
        respondWith("{\"id\":7,\"fields\":{\"System.Title\":\"t\"}}",
                "{\"url\":\"https://dev.azure.com/TestOrg/_apis/wit/attachments/q\"}", "{}");

        JSONObject result = client.attachFile("7", "a.txt", null, file.getAbsolutePath());

        assertEquals("success", result.getString("status"));
        assertEquals("a.txt", result.getString("name"));
    }

    // ---- create with parent ----

    @Test
    void createWorkItem_withParentId_linksTheNewItemAsChild() throws IOException {
        respondWith("{\"id\":88}", "{\"id\":70}");

        String response = client.createWorkItemWithFieldsJson("TestProject", "Task", "child", "d", null, "70");

        assertEquals("{\"id\":88}", response);
        assertEquals(2, sent.size());
        assertTrue(sent.get(0).url().encodedPath().endsWith("/workitems/$Task"), sent.get(0).url().encodedPath());
        Request link = sent.get(1);
        assertTrue(link.url().encodedPath().endsWith("/workitems/70"), "the PARENT is the patched source: " + link.url());
        JSONObject op = new JSONArray(bodyOf(link)).getJSONObject(0);
        assertEquals("System.LinkTypes.Hierarchy-Forward", op.getJSONObject("value").getString("rel"));
        assertTrue(op.getJSONObject("value").getString("url").endsWith("/workItems/88"));
    }

    @Test
    void createWorkItem_withoutParent_doesNotLink() throws IOException {
        respondWith("{\"id\":88}");

        client.createWorkItemWithFieldsJson("TestProject", "Task", "t", "d", null, null);
        client.createWorkItemWithFieldsJson("TestProject", "Task", "t", "d", null, "  ");

        assertEquals(2, sent.size(), "two creates, no link calls");
    }

    @Test
    void createWorkItem_parentButNoIdInResponse_failsLoudly() throws IOException {
        respondWith("{\"nope\":1}");
        IOException e = assertThrows(IOException.class,
                () -> client.createWorkItemWithFieldsJson("TestProject", "Task", "t", "d", null, "70"));
        assertTrue(e.getMessage().contains("70"));
    }
}
