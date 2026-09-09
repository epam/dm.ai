// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.github;

import com.github.istin.dmtools.common.networking.GenericRequest;
import com.github.istin.dmtools.mcp.MCPParam;
import com.github.istin.dmtools.mcp.MCPTool;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Repository content MCP tools for GitHub: repository metadata, branches,
 * files, commits, trees, CODEOWNERS, and collaborators.
 *
 * <p>Tool names and parameter names mirror the dmtools-dart catalog 1:1 so the
 * same agent code runs on both runtimes. Split out of {@link GitHub} to keep
 * that file under the file-size quality gate.</p>
 */
public abstract class GitHubRepository extends GitHubPullRequests {

    public GitHubRepository(String basePath, String authorization) throws IOException {
        super(basePath, authorization);
    }

    @MCPTool(
            name = "github_get_repo",
            description = "Get a GitHub repository by owner and name",
            integration = "github",
            category = "repositories"
    )
    public String getRepo(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo) throws IOException {
        String path = path(String.format("repos/%s/%s", owner, repo));
        GenericRequest getRequest = new GenericRequest(this, path);
        return execute(getRequest);
    }

    @MCPTool(
            name = "github_get_tree",
            description = "Get a GitHub git tree recursively by ref",
            integration = "github",
            category = "files"
    )
    public String getTree(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "ref", description = "Branch, tag, or commit SHA to read the tree from", required = true, example = "main")
            String ref) throws IOException {
        String path = path(String.format("repos/%s/%s/git/trees/%s?recursive=1", owner, repo, ref));
        GenericRequest getRequest = new GenericRequest(this, path);
        return execute(getRequest);
    }

    @MCPTool(
            name = "github_get_codeowners",
            description = "Get the CODEOWNERS file from a GitHub repository",
            integration = "github",
            category = "files"
    )
    public String getCodeowners(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo) throws IOException {
        String path = path(String.format("repos/%s/%s/contents/.github/CODEOWNERS", owner, repo));
        GenericRequest getRequest = new GenericRequest(this, path);
        return execute(getRequest);
    }

    @MCPTool(
            name = "github_list_branches",
            description = "List branches in a GitHub repository",
            integration = "github",
            category = "branches"
    )
    public String listBranches(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo) throws IOException {
        String path = path(String.format("repos/%s/%s/branches", owner, repo));
        GenericRequest getRequest = new GenericRequest(this, path);
        return execute(getRequest);
    }

    @MCPTool(
            name = "github_create_branch",
            description = "Create a new branch from an existing commit SHA",
            integration = "github",
            category = "branches"
    )
    public String createBranch(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "branch", description = "The name of the new branch", required = true, example = "feature/x")
            String branch,
            @MCPParam(name = "from_sha", description = "The commit SHA to branch from", required = true, example = "abc123")
            String fromSha) throws IOException {
        String path = path(String.format("repos/%s/%s/git/refs", owner, repo));
        GenericRequest postRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("ref", "refs/heads/" + branch);
        jsonObject.put("sha", fromSha);
        postRequest.setBody(jsonObject.toString());
        return post(postRequest);
    }

    @MCPTool(
            name = "github_delete_branch",
            description = "Delete a branch in a GitHub repository",
            integration = "github",
            category = "branches"
    )
    public String deleteBranch(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "branch", description = "The name of the branch to delete", required = true, example = "feature/x")
            String branch) throws IOException {
        String path = path(String.format("repos/%s/%s/git/refs/heads/%s", owner, repo, branch));
        GenericRequest deleteRequest = new GenericRequest(this, path);
        return delete(deleteRequest);
    }

    @MCPTool(
            name = "github_get_file_content",
            description = "Get the contents of a file in a GitHub repository",
            integration = "github",
            category = "files"
    )
    public String getFileContent(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "path", description = "The file path within the repository", required = true, example = "README.md")
            String filePath,
            @MCPParam(name = "ref", description = "Branch, tag, or commit SHA (defaults to default branch)", required = false, example = "main")
            String ref) throws IOException {
        StringBuilder pathBuilder = new StringBuilder(String.format("repos/%s/%s/contents/%s", owner, repo, filePath));
        if (ref != null && !ref.trim().isEmpty()) {
            pathBuilder.append("?ref=").append(ref);
        }
        GenericRequest getRequest = new GenericRequest(this, path(pathBuilder.toString()));
        return execute(getRequest);
    }

    @MCPTool(
            name = "github_update_file",
            description = "Create or update a file in a GitHub repository",
            integration = "github",
            category = "files"
    )
    public String updateFile(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "path", description = "The file path within the repository", required = true, example = "README.md")
            String filePath,
            @MCPParam(name = "content", description = "The new file content (plain text)", required = true, example = "Hello world")
            String content,
            @MCPParam(name = "message", description = "The commit message", required = true, example = "Update README")
            String message,
            @MCPParam(name = "sha", description = "The blob SHA of the existing file (required to update)", required = true, example = "abc123")
            String sha) throws IOException {
        String path = path(String.format("repos/%s/%s/contents/%s", owner, repo, filePath));
        GenericRequest putRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("message", message);
        jsonObject.put("content", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
        jsonObject.put("sha", sha);
        putRequest.setBody(jsonObject.toString());
        return put(putRequest);
    }

    @MCPTool(
            name = "github_get_commit",
            description = "Get a GitHub commit by SHA",
            integration = "github",
            category = "commits"
    )
    public String getCommit(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "sha", description = "The commit SHA (or ref) to fetch", required = true, example = "abc123")
            String sha) throws IOException {
        String path = path(String.format("repos/%s/%s/commits/%s", owner, repo, sha));
        GenericRequest getRequest = new GenericRequest(this, path);
        return execute(getRequest);
    }

    @MCPTool(
            name = "github_list_commits",
            description = "List commits in a GitHub repository",
            integration = "github",
            category = "commits"
    )
    public String listCommits(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "sha", description = "Branch or commit SHA to list from (defaults to default branch)", required = false, example = "main")
            String sha) throws IOException {
        StringBuilder pathBuilder = new StringBuilder(String.format("repos/%s/%s/commits", owner, repo));
        if (sha != null && !sha.trim().isEmpty()) {
            pathBuilder.append("?sha=").append(sha);
        }
        GenericRequest getRequest = new GenericRequest(this, path(pathBuilder.toString()));
        return execute(getRequest);
    }

    @MCPTool(
            name = "github_list_releases",
            description = "List GitHub releases for a repository",
            integration = "github",
            category = "releases"
    )
    public String listReleases(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo) throws IOException {
        String path = path(String.format("repos/%s/%s/releases", owner, repo));
        GenericRequest getRequest = new GenericRequest(this, path);
        return execute(getRequest);
    }

    @MCPTool(
            name = "github_get_release",
            description = "Get a GitHub release by tag name",
            integration = "github",
            category = "releases"
    )
    public String getRelease(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "tag", description = "The tag name of the release", required = true, example = "v1.0.0")
            String tag) throws IOException {
        String path = path(String.format("repos/%s/%s/releases/tags/%s", owner, repo, tag));
        GenericRequest getRequest = new GenericRequest(this, path);
        return execute(getRequest);
    }

    @MCPTool(
            name = "github_create_release",
            description = "Create a GitHub release for a tag",
            integration = "github",
            category = "releases"
    )
    public String createRelease(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "tag_name", description = "The name of the tag the release targets", required = true, example = "v1.0.0")
            String tagName,
            @MCPParam(name = "body", description = "The release description (markdown)", required = false, example = "Release notes")
            String body) throws IOException {
        String path = path(String.format("repos/%s/%s/releases", owner, repo));
        GenericRequest postRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("tag_name", tagName);
        if (body != null && !body.trim().isEmpty()) {
            jsonObject.put("body", body);
        }
        postRequest.setBody(jsonObject.toString());
        return post(postRequest);
    }

    @MCPTool(
            name = "github_add_collaborator",
            description = "Add a collaborator to a GitHub repository",
            integration = "github",
            category = "collaborators"
    )
    public String addCollaborator(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "username", description = "The collaborator login to add", required = true, example = "octocat")
            String username,
            @MCPParam(name = "permission", description = "The permission level: push, pull, admin, maintain, or triage", required = true, example = "push")
            String permission) throws IOException {
        String path = path(String.format("repos/%s/%s/collaborators/%s", owner, repo, username));
        GenericRequest putRequest = new GenericRequest(this, path);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("permission", permission);
        putRequest.setBody(jsonObject.toString());
        return put(putRequest);
    }

    @MCPTool(
            name = "github_remove_collaborator",
            description = "Remove a collaborator from a GitHub repository",
            integration = "github",
            category = "collaborators"
    )
    public String removeCollaborator(
            @MCPParam(name = "owner", description = "The repository owner (user or organization)", required = true, example = "IstiN")
            String owner,
            @MCPParam(name = "repo", description = "The repository name", required = true, example = "dmtools")
            String repo,
            @MCPParam(name = "username", description = "The collaborator login to remove", required = true, example = "octocat")
            String username) throws IOException {
        String path = path(String.format("repos/%s/%s/collaborators/%s", owner, repo, username));
        GenericRequest deleteRequest = new GenericRequest(this, path);
        return delete(deleteRequest);
    }
}
