package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.model.*;
import com.github.istin.dmtools.common.networking.GenericRequest;
import okhttp3.Request;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class GitHubIntegrationTest {

    private GitHub gitHub;
    private static final String BASE_PATH = "https://api.github.com";
    private static final String AUTHORIZATION = "token";

    @Before
    public void setUp() throws IOException {
        gitHub = Mockito.mock(GitHub.class, withSettings().useConstructor(BASE_PATH, AUTHORIZATION).defaultAnswer(CALLS_REAL_METHODS));
    }

    @Test
    public void testPath() {
        String path = "repos/test/repo";
        String expected = BASE_PATH + "/" + path;
        assertEquals(expected, gitHub.path(path));
    }

    @Test
    public void testSign() {
        Request.Builder builder = new Request.Builder();
        Request.Builder signedBuilder = gitHub.sign(builder);
        assertNotNull(signedBuilder);
    }


    @Test
    public void testTriggerAction() throws IOException {
        String workspace = "testWorkspace";
        String repository = "testRepo";
        JSONObject params = new JSONObject();

        String response = gitHub.triggerAction(workspace, repository, params);
        when(gitHub.post(any())).thenReturn("");
        assertNotNull(response);
    }


    @Test
    public void testAddPullRequestComment() throws IOException {
        String workspace = "testWorkspace";
        String repository = "testRepo";
        String pullRequestId = "1";
        String text = "Test comment";

        String response = gitHub.addPullRequestComment(workspace, repository, pullRequestId, text);
        when(gitHub.post(any())).thenReturn("");
        assertNotNull(response);
    }


    @Test
    public void testAddPullRequestLabel() throws IOException {
        String workspace = "testWorkspace";
        String repository = "testRepo";
        String pullRequestId = "1";
        String label = "bug";
        when(gitHub.post(any())).thenReturn("");
        gitHub.addPullRequestLabel(workspace, repository, pullRequestId, label);
    }



    @Test
    public void testGetPullRequestDiff() throws IOException {
        String workspace = "testWorkspace";
        String repository = "testRepo";
        String pullRequestID = "1";
        when(gitHub.execute((GenericRequest) any())).thenReturn("");
        IDiffStats diffStats = gitHub.getPullRequestDiff(workspace, repository, pullRequestID);
        assertNotNull(diffStats);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetDefaultRepository() {
        gitHub.getDefaultRepository();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetDefaultBranch() {
        gitHub.getDefaultBranch();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetDefaultWorkspace() {
        gitHub.getDefaultWorkspace();
    }

    @Test
    public void testGetPullRequestUrl() {
        String workspace = "testWorkspace";
        String repository = "testRepo";
        String id = "1";

        String url = gitHub.getPullRequestUrl(workspace, repository, id);
        assertNotNull(url);
    }

    @Test
    public void testSearchFiles() throws IOException, InterruptedException {
        String workspace = "testWorkspace";
        String repository = "testRepo";
        String query = "README";

        // Mock the response from the execute method
        String mockResponse = "{ \"items\": [ { \"name\": \"README.md\", \"path\": \"README.md\", \"url\": \"https://api.github.com/repos/testWorkspace/testRepo/contents/README.md\" } ], \"total_count\": 1 }";

        // Mock the execute method to return the mock response
        doAnswer(invocation -> {
            GenericRequest request = invocation.getArgument(0);
            // Assuming request validation, you might log/print this to debug issues
            System.out.println("Executing request: " + request.url());
            return mockResponse;
        }).when(gitHub).execute(any(GenericRequest.class));

        List<IFile> files = gitHub.searchFiles(workspace, repository, query, -1);

        // Verifying the result
        assertNotNull(files);
        assertEquals(1, files.size());
        assertEquals("README.md", files.get(0).getPath());
    }

}