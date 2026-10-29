import { describe, it, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  defaults,
  validate,
  renderFiles,
  renderQualityWorkflow,
  prBody,
  createRepo,
  ensureLabels,
  ensureGitlabLabels,
  listLabels,
  listGitlabLabels,
  FACTORY_LABELS,
  QUALITY_PRESETS,
  TOOLCHAINS,
  SCM,
  listRepos,
  checkGitlabToken,
  normalizeGitlabBaseUrl,
  TOKEN_LINKS,
  renderGitlabFiles,
  ensureGitlabIssueEventWebhook,
  removeGitlabIssueEventWebhook,
  putGitlabVariables,
  createGitlabMergeRequest,
  requiredSecrets,
  isCiEnabled,
  isFaUniversalEnabled,
  isFaMemoryEnabled,
  renderFahConfig,
  type FactoryConfig,
} from '../src/lib/factory.ts';

const here = dirname(fileURLToPath(import.meta.url));

function validConfig(overrides: Partial<FactoryConfig> = {}): FactoryConfig {
  return {
    ...defaults,
    repo: 'acme/demo',
    ...overrides,
  };
}

describe('Source control options', () => {
  it('makes GitLab available in the factory selector', () => {
    assert.deepEqual(SCM.find((option) => option.id === 'gitlab'), {
      id: 'gitlab',
      label: 'GitLab',
      available: true,
    });
  });

  it('accepts nested GitLab namespaces in repository paths', () => {
    assert.equal(validate(validConfig({ repo: 'team/platform/service' }), 'gitlab').repo, undefined);
    assert.equal(validate(validConfig({ repo: 'team' }), 'gitlab').repo, 'Use group/project');
  });

  it('normalizes GitLab instance URLs, including self-managed path prefixes', () => {
    assert.equal(normalizeGitlabBaseUrl('https://gitlab.example.com/'), 'https://gitlab.example.com');
    assert.equal(normalizeGitlabBaseUrl('https://git.example.com/gitlab/api/v4/'), 'https://git.example.com/gitlab');
    assert.equal(normalizeGitlabBaseUrl('http://localhost:8080/gitlab'), 'http://localhost:8080/gitlab');
    assert.throws(() => normalizeGitlabBaseUrl('ftp://gitlab.example.com'), /must use HTTPS/);
    assert.throws(() => normalizeGitlabBaseUrl('http://gitlab.example.com'), /must use HTTPS/);
    assert.throws(() => normalizeGitlabBaseUrl('https://user:pass@gitlab.example.com'), /cannot include credentials/);
    assert.equal(
      TOKEN_LINKS.setupGitlab('https://gitlab.example.com/gitlab'),
      'https://gitlab.example.com/gitlab/-/user_settings/personal_access_tokens',
    );
  });

  it('lists writable GitLab projects using a personal access token', async () => {
    const origFetch = globalThis.fetch;
    const calls: { url: string; init?: RequestInit }[] = [];
    globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push({ url: String(input), init });
      return new Response(JSON.stringify([
        { path_with_namespace: 'team/platform/service', visibility: 'private' },
        { path_with_namespace: 'team/public-tool', visibility: 'public' },
      ]), { status: 200 });
    }) as typeof fetch;

    try {
      const repos = await listRepos('glpat_test-token-123456789', 'gitlab', 'https://gitlab.example.com/gitlab');
      assert.deepEqual(repos, [
        { full_name: 'team/platform/service', private: true },
        { full_name: 'team/public-tool', private: false },
      ]);
      assert.match(calls[0].url, /^https:\/\/gitlab\.example\.com\/gitlab\/api\/v4\/projects\?.*min_access_level=30/);
      assert.equal(new Headers(calls[0].init?.headers).get('PRIVATE-TOKEN'), 'glpat_test-token-123456789');
    } finally {
      globalThis.fetch = origFetch;
    }
  });

  it('checks a GitLab personal access token against the authenticated user endpoint', async () => {
    const origFetch = globalThis.fetch;
    let request: { url: string; init?: RequestInit } | undefined;
    globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
      request = { url: String(input), init };
      return new Response(JSON.stringify({ username: 'alice' }), { status: 200 });
    }) as typeof fetch;

    try {
      const result = await checkGitlabToken('glpat_test-token-123456789', 'https://gitlab.example.com/gitlab/');
      assert.equal(request?.url, 'https://gitlab.example.com/gitlab/api/v4/user');
      assert.equal(new Headers(request?.init?.headers).get('PRIVATE-TOKEN'), 'glpat_test-token-123456789');
      assert.equal(result.login, 'alice');
      assert.equal(result.kind, 'personal access');
    } finally {
      globalThis.fetch = origFetch;
    }
  });

  it('renders GitLab-native factory jobs and configures GitLab runner settings', () => {
    const cfg = validConfig();
    const files = renderGitlabFiles(cfg, 'https://gitlab.example.com/gitlab');
    assert.ok(files.some((file) => file.path === '.gitlab-ci.yml'));
    assert.ok(files.every((file) => !file.path.startsWith('.github/')));
    assert.ok(files.some((file) => file.path === '.dmtools/config.js'));
    const story = files.find((file) => file.path === '.dmtools/runners/fa-story-dev.json');
    assert.ok(story);
    assert.match(story.content, /"DEFAULT_TRACKER": "gitlab"/);
    assert.match(story.content, /"DEFAULT_SCM": "gitlab"/);
    assert.match(story.content, /"GITLAB_BASE_PATH": "https:\/\/gitlab\.example\.com\/gitlab"/);
    assert.match(story.content, /"GITLAB_WORKSPACE": "acme"/);
    assert.match(story.content, /"GITLAB_REPOSITORY": "demo"/);
    const review = files.find((file) => file.path === '.dmtools/runners/fa-review.json');
    assert.ok(review);
    assert.equal(JSON.parse(review.content).params.requireCliOutputFile, false);
    for (const path of [
      '.dmtools/runners/fa-bug-dev.json',
      '.dmtools/runners/fa-story-dev.json',
      '.dmtools/runners/fa-rework.json',
      '.dmtools/runners/fa-review.json',
    ]) {
      const runner = files.find((file) => file.path === path);
      assert.ok(runner, `GitLab runner should include ${path}`);
    }
    const pipeline = files.find((file) => file.path === '.gitlab-ci.yml')!.content;
    assert.match(pipeline, /apt-get install -y --no-install-recommends curl git jq unzip/);
    assert.match(pipeline, /setup\/_common\.sh/);
    assert.match(pipeline, /setup\/fa\.sh/);
    assert.match(pipeline, /FA_INSTALL_DIR="\$HOME\/\.local\/bin" bash/);
    assert.match(pipeline, /export PATH="\$HOME\/\.local\/bin:\$PATH"/);
    assert.match(pipeline, /fa --version/);
    assert.match(
      pipeline,
      /DMTOOLS_PACK_REGISTRY:\n    value: https:\/\/github\.com\/IstiN\/dmtools-agents\/releases\/latest\/download/,
    );
    for (const job of [
      'poll-assigned-issues:',
      'ai-teammate:',
      'review-merge-request:',
      'rework-merge-request:',
      'merge-approved-merge-request:',
    ]) {
      assert.ok(pipeline.includes(job), `GitLab pipeline should include ${job}`);
    }
    assert.match(
      pipeline,
      /review-merge-request:[\s\S]*?script:\n    - \|\n      dmtools run \.dmtools\/runners\/fa-review\.json/,
    );
    assert.match(
      pipeline,
      /rework-merge-request:[\s\S]*?script:\n    - \|\n      dmtools run \.dmtools\/runners\/fa-rework\.json/,
    );
    assert.match(pipeline, /CI_PIPELINE_SOURCE == "merge_request_event"/);
    assert.match(pipeline, /CI_PIPELINE_SOURCE == "trigger" && \$DMTOOLS_FACTORY_TRIGGER == "issue_webhook"/);
    assert.match(pipeline, /poll-assigned-issues:[\s\S]*?rules:\n    - if: '\$CI_PIPELINE_SOURCE == "trigger"/);
    assert.doesNotMatch(pipeline, /CI_PIPELINE_SOURCE == "schedule"/);
    const qualityFiles = renderGitlabFiles(validConfig({ qualityPreset: 'crap4js' }));
    const qualityPipeline = qualityFiles.find((file) => file.path === '.gitlab-ci.yml')!.content;
    assert.match(qualityPipeline, /  - quality/);
    assert.match(qualityPipeline, /image: node:22/);
    assert.match(qualityPipeline, /npx --yes github:IstiN\/crap4js/);
    const poller = files.find((file) => file.path === '.dmtools/scripts/gitlab-factory-poll-issues.sh')!.content;
    assert.match(poller, /assignees.*username == \$machine/);
    assert.match(poller, /agent:dev/);
    assert.match(poller, /agent:review/);
    assert.match(poller, /agent:rework/);
    assert.match(poller, /all\(\. != "agent:skip" and \. != "blocked"\)/);
    assert.match(poller, /"status:In Development", "status:In Review", "status:In Rework", "ai_developed", "ai_pr_reviewed"/);
    assert.match(poller, /in progress\|_wip\|rework-round-/);
    assert.match(poller, /status:In\\ Development/);
    assert.match(poller, /status:In\\ Review/);
    assert.match(poller, /status:In\\ Rework/);
    assert.match(poller, /in\\ progress/);
    assert.match(poller, /TRIGGER_PAYLOAD:\?GitLab issue webhook payload is required/);
    assert.match(poller, /changes\.labels\.current/);
    assert.match(poller, /changes\.labels\.previous/);
    assert.match(poller, /\$previous \| index\(\$label\)\) == null/);
    assert.match(poller, /no labels were added to issue/);
    assert.match(poller, /--argjson webhook_issue_iid/);
    assert.match(poller, /if \[\[ -n "\$webhook_issue_iid" \]\]; then\s+exit 0\s+fi/);
    assert.match(
      poller,
      /any\(\. == "agent:review"\)[\s\S]*?any\(\. == "agent:rework"\)[\s\S]*?all\(\. != "status:In Rework"\)/,
      'explicit review and rework labels should bypass development status filters',
    );
    assert.match(poller, /agent:review,\*\) config='.dmtools\/runners\/fa-review\.json'; trigger='agent:review'/);
    assert.match(poller, /author\.username == \$machine\s+and \(\(\.labels \/\/ \[\]\) \| any\(\. == "pr_approved"\)\)/);
    assert.match(poller, /\(\.labels \/\/ \[\]\) \| all\(\. != "blocked"\)/);
    assert.match(poller, /run_agent "\$config" --inputTicketId "\$iid"/);
    assert.match(poller, /dmtools run "\$@" 2>&1 \| tee "\$output_file"/);
    assert.match(poller, /PIPESTATUS\[@\]/);
    assert.match(poller, /Agent completed with exit code: \[1-9\]/);
    assert.match(
      poller,
      /if \[ "\$trigger" != 'agent:review' \]; then\s+base_sha=\$\(git rev-parse HEAD\)\s+fi\s+run_agent "\$config" --inputTicketId "\$iid"\s+if \[ "\$trigger" != 'agent:review' \]; then\s+publish_issue_changes "\$iid" "\$base_sha"\s+fi/,
      'issue review runs should not require code changes or create a branch/MR',
    );
    assert.match(poller, /if \[ "\$trigger" = 'agent:review' \]; then\s+result_label='ai_pr_reviewed'/);
    assert.match(poller, /leaving issue\/MR labels unchanged/);
    assert.match(poller, /git switch -c "\$branch"/);
    assert.match(poller, /git push/);
    assert.match(poller, /\[gitlab-api\] request failed method=\$method url=\$url curl_exit=\$curl_status http_status=\$http_status/);
    assert.match(poller, /\[gitlab-api\] request failed method=\$method url=\$url http_status=\$http_status/);
    assert.match(poller, /error response \(max 2000 chars\)/);
    assert.match(poller, /message\/\/"\$GITLAB_TOKEN"\/\[REDACTED\]/);
    assert.match(poller, /GET "\$CI_API_V4_URL\/user"/);
    assert.match(poller, /git_remote_url="https:\/\/\$git_username@\$\{remote_url#https:\/\/\}"/);
    assert.match(poller, /Git credential helper/);
    assert.match(poller, /printf 'username=%s\\\\npassword=%s\\\\n\\\\n'/);
    assert.match(poller, /push --porcelain/);
    assert.doesNotMatch(poller, /GIT_TRACE_CURL|GIT_TRACE=|log_git_trace/);
    assert.match(poller, /Pushed branch \$branch to GitLab/);
    assert.match(poller, /\[poller-error\] line=%s exit=%s issue=%s pipeline=%s/);
    assert.match(poller, /write_repository scope/);
    assert.match(poller, /Developer or higher access/);
    assert.match(poller, /branch protection/);
    assert.match(poller, /projects\/\$CI_PROJECT_ID\/merge_requests/);
    assert.match(poller, /Created GitLab merge request:/);
    assert.ok(
      poller.indexOf('publish_issue_changes "$iid" "$base_sha"') <
        poller.indexOf('add_labels=$result_label'),
      'GitLab issues must only be marked developed after their changes are pushed and an MR is created',
    );
    const gitlabToken = requiredSecrets(cfg, 'gitlab')[0];
    assert.equal(gitlabToken.name, 'GITLAB_TOKEN');
    assert.match(gitlabToken.hint, /api and write_repository scopes/);
    assert.match(gitlabToken.hint, /Developer or higher access/);
  });

  it('creates and updates a managed GitLab issue-event webhook idempotently', async () => {
    const origFetch = globalThis.fetch;
    const calls: { url: string; method: string; body?: Record<string, unknown> }[] = [];
    let triggerCreated = false;
    let hookCreated = false;
    globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const method = init?.method ?? 'GET';
      const body = init?.body
        ? init.body instanceof URLSearchParams
          ? Object.fromEntries(init.body)
          : JSON.parse(String(init.body))
        : undefined;
      calls.push({ url, method, body });
      if (url.includes('/triggers?')) {
        return new Response(
          JSON.stringify(
            triggerCreated
              ? [{ id: 21, token: 'trigger-secret', description: 'DMTools factory issue webhook' }]
              : [],
          ),
          { status: 200 },
        );
      }
      if (url.endsWith('/triggers') && method === 'POST') {
        triggerCreated = true;
        return new Response(
          JSON.stringify({ id: 21, token: 'trigger-secret', description: 'DMTools factory issue webhook' }),
          { status: 201 },
        );
      }
      if (url.includes('/hooks?')) {
        const hooks = hookCreated
          ? [{ id: 34, url: 'https://gitlab.example.com/api/v4/projects/1/trigger/pipeline?variables%5BDMTOOLS_FACTORY_TRIGGER%5D=issue_webhook' }]
          : [];
        return new Response(JSON.stringify(hooks), { status: 200 });
      }
      if (url.endsWith('/hooks') && method === 'POST') {
        hookCreated = true;
        return new Response(JSON.stringify({ id: 34 }), { status: 201 });
      }
      if (url.endsWith('/hooks/34') && method === 'PUT') {
        return new Response(JSON.stringify({ id: 34 }), { status: 200 });
      }
      return new Response('unexpected request', { status: 500 });
    }) as typeof fetch;

    try {
      const logs: string[] = [];
      await ensureGitlabIssueEventWebhook(
        'glpat_test-token-123456789',
        'team/app',
        'https://gitlab.example.com',
        'main',
        (message) => logs.push(message),
      );
      await ensureGitlabIssueEventWebhook(
        'glpat_test-token-123456789',
        'team/app',
        'https://gitlab.example.com',
        'main',
        (message) => logs.push(message),
      );
      const created = calls.find((call) => call.url.endsWith('/hooks') && call.method === 'POST');
      const updated = calls.find((call) => call.url.endsWith('/hooks/34') && call.method === 'PUT');
      assert.equal(calls.filter((call) => call.url.endsWith('/triggers') && call.method === 'POST').length, 1);
      assert.equal(
        calls.find((call) => call.url.endsWith('/triggers') && call.method === 'POST')?.body?.description,
        'DMTools factory issue webhook',
      );
      assert.equal(new URL(String(created?.body?.url)).searchParams.get('token'), 'trigger-secret');
      assert.equal(new URL(String(created?.body?.url)).searchParams.get('ref'), 'main');
      assert.equal(
        new URL(String(created?.body?.url)).searchParams.get('variables[DMTOOLS_FACTORY_TRIGGER]'),
        'issue_webhook',
      );
      assert.equal(created?.body?.issues_events, true);
      assert.equal(created?.body?.push_events, false);
      assert.equal(created?.body?.enable_ssl_verification, true);
      assert.deepEqual(updated?.body, created?.body);
      assert.doesNotMatch(logs.join('\n'), /trigger-secret/);
      assert.deepEqual(logs, [
        'GitLab Issue-event webhook created for main',
        'GitLab Issue-event webhook updated for main',
      ]);
    } finally {
      globalThis.fetch = origFetch;
    }
  });

  it('removes only factory-managed GitLab webhook resources', async () => {
    const origFetch = globalThis.fetch;
    const calls: { url: string; method: string }[] = [];
    globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const method = init?.method ?? 'GET';
      calls.push({ url, method });
      if (url.includes('/hooks?')) {
        return new Response(
          JSON.stringify([
            {
              id: 34,
              url: 'https://gitlab.example.com/api/v4/projects/1/trigger/pipeline?variables%5BDMTOOLS_FACTORY_TRIGGER%5D=issue_webhook',
            },
            { id: 35, url: 'https://example.com/other-hook' },
          ]),
          { status: 200 },
        );
      }
      if (url.includes('/triggers?')) {
        return new Response(
          JSON.stringify([
            { id: 21, token: 'trigger-secret', description: 'DMTools factory issue webhook' },
            { id: 22, token: 'other-secret', description: 'Other trigger' },
          ]),
          { status: 200 },
        );
      }
      if (method === 'DELETE') return new Response(null, { status: 204 });
      return new Response('unexpected request', { status: 500 });
    }) as typeof fetch;

    try {
      const logs: string[] = [];
      await removeGitlabIssueEventWebhook(
        'glpat_test-token-123456789',
        'team/app',
        'https://gitlab.example.com',
        (message) => logs.push(message),
      );
      assert.deepEqual(
        calls.filter((call) => call.method === 'DELETE').map((call) => call.url),
        [
          'https://gitlab.example.com/api/v4/projects/team%2Fapp/hooks/34',
          'https://gitlab.example.com/api/v4/projects/team%2Fapp/triggers/21',
        ],
      );
      assert.equal(logs.length, 1);
    } finally {
      globalThis.fetch = origFetch;
    }
  });

  it('recreates existing GitLab secrets as masked and hidden and creates new ones that way', async () => {
    const origFetch = globalThis.fetch;
    const calls: { url: string; init?: RequestInit }[] = [];
    globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      calls.push({ url, init });
      if (url.endsWith('/version')) return new Response(JSON.stringify({ version: '17.5.0' }), { status: 200 });
      if (url.includes('/variables?')) {
        return new Response(JSON.stringify([{ key: 'EXISTING', masked: true, hidden: false }]), { status: 200 });
      }
      if (init?.method === 'DELETE') return new Response(null, { status: 204 });
      return new Response(JSON.stringify({ key: 'NEW_SECRET' }), { status: init?.method === 'PUT' ? 200 : 201 });
    }) as typeof fetch;

    try {
      await putGitlabVariables(
        'glpat_test-token-123456789',
        'team/app',
        'https://gitlab.example.com/prefix',
        {
          EXISTING: 'oldSecretValue123',
          NEW_SECRET: 'newSecretValue456',
        },
      );
      assert.equal(calls[2].init?.method, 'DELETE');
      assert.equal(calls[3].init?.method, 'POST');
      assert.equal(JSON.parse(String(calls[3].init?.body)).masked, true);
      assert.equal(JSON.parse(String(calls[3].init?.body)).masked_and_hidden, true);
      assert.equal(calls[4].init?.method, 'POST');
      assert.equal(JSON.parse(String(calls[4].init?.body)).masked_and_hidden, true);
      assert.equal(calls[5].init?.method, 'POST');
      const basePathVariable = JSON.parse(String(calls[5].init?.body));
      assert.equal(basePathVariable.masked, false);
      assert.equal('masked_and_hidden' in basePathVariable, false);
      assert.match(calls[5].url, /^https:\/\/gitlab\.example\.com\/prefix\/api\/v4\/projects\/team%2Fapp\/variables$/);
    } finally {
      globalThis.fetch = origFetch;
    }
  });

  it('updates already hidden GitLab secrets without trying to change their hidden setting', async () => {
    const origFetch = globalThis.fetch;
    const calls: { url: string; init?: RequestInit }[] = [];
    globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      calls.push({ url, init });
      if (url.endsWith('/version')) return new Response(JSON.stringify({ version: '17.5.0' }), { status: 200 });
      if (url.includes('/variables?')) {
        return new Response(JSON.stringify([{ key: 'HIDDEN_SECRET', masked: true, hidden: true }]), { status: 200 });
      }
      return new Response(JSON.stringify({ key: 'HIDDEN_SECRET', masked: true, hidden: true }), { status: 200 });
    }) as typeof fetch;

    try {
      await putGitlabVariables(
        'glpat_test-token-123456789',
        'team/app',
        'https://gitlab.example.com',
        { HIDDEN_SECRET: 'updatedSecretValue123' },
      );
      assert.equal(calls[2].init?.method, 'PUT');
      const update = JSON.parse(String(calls[2].init?.body));
      assert.equal(update.masked, true);
      assert.equal('masked_and_hidden' in update, false);
    } finally {
      globalThis.fetch = origFetch;
    }
  });

  it('rejects GitLab secret values that cannot be masked and hidden', async () => {
    await assert.rejects(
      putGitlabVariables(
        'glpat_test-token-123456789',
        'team/app',
        'https://gitlab.example.com',
        { MULTILINE_SECRET: 'line one\nline two' },
      ),
      /must be a single line to be masked and hidden/,
    );
  });

  it('does not alter project variables when GitLab is older than 17.4', async () => {
    const origFetch = globalThis.fetch;
    const calls: string[] = [];
    globalThis.fetch = (async (input: RequestInfo | URL) => {
      const url = String(input);
      calls.push(url);
      return new Response(JSON.stringify({ version: '17.3.8' }), { status: 200 });
    }) as typeof fetch;

    try {
      await assert.rejects(
        putGitlabVariables(
          'glpat_test-token-123456789',
          'team/app',
          'https://gitlab.example.com',
          { GITLAB_TOKEN: 'glpat_secretvalue123' },
        ),
        /require GitLab 17\.4 or newer/,
      );
      assert.deepEqual(calls, ['https://gitlab.example.com/api/v4/version']);
    } finally {
      globalThis.fetch = origFetch;
    }
  });

  it('commits GitLab setup files and opens a merge request', async () => {
    const origFetch = globalThis.fetch;
    const calls: { url: string; init?: RequestInit }[] = [];
    globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      calls.push({ url, init });
      if (url.endsWith('/projects/team%2Fapp')) {
        return new Response(JSON.stringify({ default_branch: 'main' }), { status: 200 });
      }
      if (url.includes('/repository/tree?')) return new Response(JSON.stringify([]), { status: 200 });
      if (url.endsWith('/merge_requests')) {
        return new Response(JSON.stringify({ web_url: 'https://gitlab.example.com/team/app/-/merge_requests/1' }), { status: 201 });
      }
      return new Response(JSON.stringify({ id: 1 }), { status: 201 });
    }) as typeof fetch;

    try {
      const mrUrl = await createGitlabMergeRequest(
        'glpat_test-token-123456789',
        'team/app',
        'https://gitlab.example.com',
        validConfig({ repo: 'team/app', baseBranch: 'main' }),
      );
      assert.equal(mrUrl, 'https://gitlab.example.com/team/app/-/merge_requests/1');
      const commit = calls.find((call) => call.url.endsWith('/repository/commits'));
      const actions = JSON.parse(String(commit?.init?.body)).actions as { file_path: string }[];
      assert.ok(actions.some((file) => file.file_path === '.gitlab-ci.yml'));
      assert.ok(actions.every((file) => !file.file_path.startsWith('.github/')));
      assert.ok(calls.some((call) => call.url.endsWith('/merge_requests')));
    } finally {
      globalThis.fetch = origFetch;
    }
  });
});

describe('Issue #749 — agents repository link', () => {
  it('points /agents/ index page at AGENTS_REPO (IstiN/dmtools-agents) and not broken epam/dm.ai/tree/main/agents', () => {
    const contentTs = readFileSync(resolve(here, '../src/data/content.ts'), 'utf8');
    assert.match(contentTs, /export const AGENTS_REPO\s*=\s*'https:\/\/github\.com\/IstiN\/dmtools-agents'/);
    const indexAstro = readFileSync(resolve(here, '../src/pages/agents/index.astro'), 'utf8');
    assert.ok(indexAstro.includes('href={AGENTS_REPO}'), 'agents/index.astro should link to AGENTS_REPO');
    assert.ok(!indexAstro.includes('https://github.com/epam/dm.ai/tree/main/agents'), 'must not contain broken submodule tree URL');
  });
});

describe('Issue #750 — http and https base URLs in provider config', () => {
  it('accepts http:// base URLs for custom / local OpenAI-compatible providers', () => {
    const cfg = validConfig({
      providers: {
        mode: 'default',
        entries: [{ preset: 'custom', model: 'qwen-coder', baseUrl: 'http://localhost:11434/v1', keyEnv: 'CUSTOM_API_KEY' }],
        reviewSeparate: false,
        reviewEntries: [],
      },
    });
    const errors = validate(cfg);
    assert.equal(errors.providers, undefined);
  });

  it('accepts https:// base URLs', () => {
    const cfg = validConfig({
      providers: {
        mode: 'default',
        entries: [{ preset: 'custom', model: 'gpt-4o', baseUrl: 'https://my-proxy.internal/v1', keyEnv: 'CUSTOM_API_KEY' }],
        reviewSeparate: false,
        reviewEntries: [],
      },
    });
    const errors = validate(cfg);
    assert.equal(errors.providers, undefined);
  });

  it('rejects base URLs without http:// or https://', () => {
    const cfg = validConfig({
      providers: {
        mode: 'default',
        entries: [{ preset: 'custom', model: 'gpt-4o', baseUrl: 'ftp://my-proxy.internal/v1', keyEnv: 'CUSTOM_API_KEY' }],
        reviewSeparate: false,
        reviewEntries: [],
      },
    });
    const errors = validate(cfg);
    assert.equal(errors.providers, 'Base URL must start with http:// or https://');
  });
});

describe('Issue #752 — custom self-hosted runner label across all workflows', () => {
  it('propagates custom runner label to sm-kicker.yml, ai-teammate.yml (including dedupe), machine-sm.yml, machine-merge.yml, and merge-trigger.yml', () => {
    const customRunner = 'my-corp-self-hosted';
    const cfg = validConfig({ runner: customRunner });
    const files = renderFiles(cfg);

    const byPath = Object.fromEntries(files.map((f) => [f.path, f.content]));

    // sm-kicker.yml passes runner: my-corp-self-hosted
    assert.match(byPath['.github/workflows/sm-kicker.yml'], /runner:\s*my-corp-self-hosted/);
    assert.doesNotMatch(byPath['.github/workflows/sm-kicker.yml'], /ubuntu-slim/);

    // ai-teammate.yml uses my-corp-self-hosted on both dedupe and teammate jobs
    assert.match(byPath['.github/workflows/ai-teammate.yml'], /runs-on:\s*my-corp-self-hosted/);
    assert.match(byPath['.github/workflows/ai-teammate.yml'], /runner:\s*my-corp-self-hosted/);
    assert.doesNotMatch(byPath['.github/workflows/ai-teammate.yml'], /runs-on:\s*ubuntu-slim/);

    // machine-sm.yml, machine-merge.yml, merge-trigger.yml
    assert.match(byPath['.github/workflows/machine-sm.yml'], /runner:\s*my-corp-self-hosted/);
    assert.match(byPath['.github/workflows/machine-merge.yml'], /runner:\s*my-corp-self-hosted/);
    assert.match(byPath['.github/workflows/merge-trigger.yml'], /runner:\s*my-corp-self-hosted/);
  });
});

describe('Issue #747 (a) — Factory without default quality check workflow', () => {
  it('validates cleanly when ciEnabled is false even if ciFile, ciName, and checks are empty', () => {
    const cfg = validConfig({
      ciEnabled: false,
      ciFile: '',
      ciName: '',
      checks: [],
    });
    assert.equal(isCiEnabled(cfg), false);
    const errors = validate(cfg);
    assert.deepEqual(errors, {});
  });

  it('omits workflow_run triggers and sets empty validation-checks when ciEnabled is false', () => {
    const cfg = validConfig({
      ciEnabled: false,
      ciFile: '',
      ciName: '',
      checks: [],
    });
    const files = renderFiles(cfg);
    const byPath = Object.fromEntries(files.map((f) => [f.path, f.content]));

    assert.doesNotMatch(byPath['.github/workflows/machine-merge.yml'], /workflow_run:/);
    assert.doesNotMatch(byPath['.github/workflows/merge-trigger.yml'], /workflow_run:/);
    assert.doesNotMatch(byPath['.github/workflows/sm-kicker.yml'], /workflow_run:/);

    assert.match(byPath['.github/workflows/machine-sm.yml'], /validation-checks:\s*'\[\]'/);
    assert.match(byPath['.github/workflows/machine-sm.yml'], /ci-workflow:\s*''/);
    assert.match(byPath['.github/workflows/machine-merge.yml'], /ci_workflow:\s*''/);
    assert.match(byPath['.github/workflows/sm-kicker.yml'], /ci_workflow:\s*''/);
    assert.match(byPath['.github/workflows/sm-kicker.yml'], /required_contexts:\s*''/);

    const body = prBody(cfg);
    assert.match(body, /No CI workflow configured/);
  });
});

describe('Issue #747 (b) — Create new repository for Greenfield', () => {
  const origFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = origFetch;
  });

  it('creates a user repository via POST /user/repos when owner matches authenticated login', async () => {
    const calls: { url: string; init?: RequestInit }[] = [];
    globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      calls.push({ url, init });
      return new Response(JSON.stringify({ full_name: 'alice/greenfield-app', default_branch: 'main' }), { status: 201 });
    }) as typeof fetch;

    const logs: string[] = [];
    const res = await createRepo('ghp_testtoken123456789012345', 'alice/greenfield-app', true, 'Alice', (m) => logs.push(m));
    assert.equal(res.full_name, 'alice/greenfield-app');
    assert.equal(res.default_branch, 'main');
    assert.equal(calls.length, 1);
    assert.equal(calls[0].url, 'https://api.github.com/user/repos');
    assert.deepEqual(JSON.parse(String(calls[0].init?.body)), {
      name: 'greenfield-app',
      private: true,
      auto_init: true,
    });
    assert.ok(logs.some((l) => l.includes('created alice/greenfield-app')));
  });

  it('creates an organization repository via POST /orgs/{org}/repos when owner differs from user login', async () => {
    const calls: { url: string; init?: RequestInit }[] = [];
    globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      calls.push({ url, init });
      return new Response(JSON.stringify({ full_name: 'my-org/new-service', default_branch: 'main' }), { status: 201 });
    }) as typeof fetch;

    const res = await createRepo('ghp_testtoken123456789012345', 'my-org/new-service', false, 'alice');
    assert.equal(res.full_name, 'my-org/new-service');
    assert.equal(calls.length, 1);
    assert.equal(calls[0].url, 'https://api.github.com/orgs/my-org/repos');
    assert.deepEqual(JSON.parse(String(calls[0].init?.body)), {
      name: 'new-service',
      private: false,
      auto_init: true,
    });
  });
});

describe('Issue #747 (c) — Default crap4* quality workflows for specific tech stacks', () => {
  it('exposes presets for JS/TS, Dart/Flutter, Java, Python, and Go with expected repositories', () => {
    const ids = QUALITY_PRESETS.map((p) => p.id);
    assert.deepEqual(ids, ['crap4js', 'crap4dart', 'crap4java', 'crap4py', 'crap4go']);
    const urls = Object.fromEntries(QUALITY_PRESETS.map((p) => [p.id, p.repoUrl]));
    assert.equal(urls.crap4js, 'https://github.com/IstiN/crap4js');
    assert.equal(urls.crap4dart, 'https://github.com/IstiN/crap4dart');
    assert.equal(urls.crap4java, 'https://github.com/IstiN/crap4java');
    assert.equal(urls.crap4py, 'https://github.com/vabhzw17eg2qu4m9-bit/crap4py');
    assert.equal(urls.crap4go, 'https://github.com/vabhzw17eg2qu4m9-bit/crap4go');
    assert.ok(TOOLCHAINS.some((t) => t.id === 'go'));
  });

  for (const preset of QUALITY_PRESETS) {
    it(`renders .github/workflows/quality.yml for ${preset.id} and wires runner & workflow_dispatch`, () => {
      const cfg = validConfig({
        ciEnabled: true,
        qualityPreset: preset.id,
        ciFile: 'quality.yml',
        ciName: 'Quality',
        checks: ['quality'],
        runner: 'custom-runner-x',
      });
      const wf = renderQualityWorkflow(cfg);
      assert.ok(wf, `expected quality workflow for ${preset.id}`);
      assert.match(wf!, /name:\s*Quality/);
      assert.match(wf!, /workflow_dispatch:/);
      assert.match(wf!, /runs-on:\s*custom-runner-x/);
      assert.ok(wf!.includes(preset.id), `workflow should invoke ${preset.id}`);

      const files = renderFiles(cfg);
      const qualityFile = files.find((f) => f.path === '.github/workflows/quality.yml');
      assert.ok(qualityFile, 'renderFiles should include .github/workflows/quality.yml');

      const smFile = files.find((f) => f.path === '.github/workflows/machine-sm.yml')!;
      assert.match(smFile.content, /ci-workflow:\s*'quality\.yml'/);
      assert.match(smFile.content, /validation-checks:\s*'\["quality"\]'/);

      const body = prBody(cfg);
      assert.match(body, new RegExp(`Generated \`quality\\.yml\` \\(${preset.id}\\)`));
    });
  }
});

describe('Issue #757 — Create required labels automatically / on demand', () => {
  const origFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = origFetch;
  });

  it('FACTORY_LABELS contains all required factory labels', () => {
    const names = FACTORY_LABELS.map((l) => l.name);
    assert.deepEqual(names, [
      'agent:dev',
      'agent:review',
      'agent:rework',
      'agent:skip',
      'ai_developed',
      'ai_validating',
      'ai_validated',
      'ai_pr_reviewed',
      'pr_approved',
      'validation_failed',
      'blocked',
      'needs-human',
    ]);
    for (const l of FACTORY_LABELS) {
      assert.match(l.color, /^[0-9a-f]{6}$/i);
      assert.ok(l.description.length > 0);
    }
  });

  it('ensureLabels fetches existing labels and only POSTs missing labels (case-insensitive)', async () => {
    const calls: { url: string; method: string; body?: unknown }[] = [];
    globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const method = init?.method ?? 'GET';
      const body = init?.body ? JSON.parse(String(init.body)) : undefined;
      calls.push({ url, method, body });
      if (method === 'GET' && url === 'https://api.github.com/repos/acme/demo/labels?per_page=100') {
        return new Response(
          JSON.stringify([
            { name: 'AGENT:DEV' },
            { name: 'blocked' },
            { name: 'bug' },
          ]),
          { status: 200 },
        );
      }
      if (method === 'POST' && url === 'https://api.github.com/repos/acme/demo/labels') {
        return new Response(JSON.stringify(body), { status: 201 });
      }
      return new Response('not found', { status: 404 });
    }) as typeof fetch;

    const logs: string[] = [];
    const res = await ensureLabels('ghp_testtoken123456789012345', 'acme/demo', (m) => logs.push(m));

    assert.deepEqual(res.existing, ['agent:dev', 'blocked']);
    assert.equal(res.created.length, FACTORY_LABELS.length - 2);
    assert.ok(!res.created.includes('agent:dev'));
    assert.ok(!res.created.includes('blocked'));
    assert.ok(res.created.includes('agent:review'));
    assert.ok(res.created.includes('needs-human'));

    const posts = calls.filter((c) => c.method === 'POST');
    assert.equal(posts.length, FACTORY_LABELS.length - 2);
    assert.ok(logs.some((l) => l.startsWith('labels created: ') && l.includes('agent:review')));
  });

  it('ensureLabels makes 0 POST requests when all required labels already exist', async () => {
    const calls: { url: string; method: string }[] = [];
    globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const method = init?.method ?? 'GET';
      calls.push({ url, method });
      return new Response(
        JSON.stringify(FACTORY_LABELS.map((l) => ({ name: l.name }))),
        { status: 200 },
      );
    }) as typeof fetch;

    const logs: string[] = [];
    const existingList = await listLabels('ghp_testtoken123456789012345', 'acme/demo');
    assert.equal(existingList.length, FACTORY_LABELS.length);

    calls.length = 0;
    const res = await ensureLabels('ghp_testtoken123456789012345', 'acme/demo', (m) => logs.push(m));
    assert.deepEqual(res.created, []);
    assert.equal(res.existing.length, FACTORY_LABELS.length);
    assert.equal(calls.filter((c) => c.method === 'POST').length, 0);
    assert.deepEqual(logs, ['labels: all required labels already exist']);
  });

  it('ensureGitlabLabels creates missing project labels with GitLab colors and descriptions', async () => {
    const calls: { url: string; method: string; body?: Record<string, string> }[] = [];
    globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const method = init?.method ?? 'GET';
      const body = init?.body ? JSON.parse(String(init.body)) : undefined;
      calls.push({ url, method, body });
      if (method === 'GET') {
        return new Response(JSON.stringify([{ name: 'AGENT:DEV' }, { name: 'bug' }]), { status: 200 });
      }
      if (method === 'POST') return new Response(JSON.stringify(body), { status: 201 });
      return new Response('not found', { status: 404 });
    }) as typeof fetch;

    const logs: string[] = [];
    const result = await ensureGitlabLabels(
      'glpat_testtoken123456789012345',
      'acme/demo',
      'https://gitlab.example.com/group',
      (message) => logs.push(message),
    );

    assert.deepEqual(result.existing, ['agent:dev']);
    assert.equal(result.created.length, FACTORY_LABELS.length - 1);
    const posts = calls.filter((call) => call.method === 'POST');
    assert.equal(posts.length, FACTORY_LABELS.length - 1);
    assert.deepEqual(posts[0].body, {
      name: 'agent:review',
      color: '#0e8a16',
      description: 'Trigger AI Teammate PR review leg',
    });
    assert.ok(calls.every((call) => call.url.startsWith('https://gitlab.example.com/group/api/v4/')));
    assert.ok(logs[0].startsWith('labels created: '));
  });

  it('listGitlabLabels paginates and ensureGitlabLabels skips existing labels', async () => {
    const calls: { url: string; method: string }[] = [];
    globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const method = init?.method ?? 'GET';
      calls.push({ url, method });
      if (url.includes('page=1')) {
        return new Response(JSON.stringify(Array.from({ length: 100 }, (_, index) => ({ name: `label-${index}` }))), {
          status: 200,
        });
      }
      return new Response(JSON.stringify(FACTORY_LABELS.map((label) => ({ name: label.name }))), { status: 200 });
    }) as typeof fetch;

    const labels = await listGitlabLabels('glpat_testtoken123456789012345', 'acme/demo', 'https://gitlab.com');
    assert.equal(labels.length, 100 + FACTORY_LABELS.length);
    assert.ok(calls.some((call) => call.url.includes('page=2')));

    calls.length = 0;
    const result = await ensureGitlabLabels('glpat_testtoken123456789012345', 'acme/demo', 'https://gitlab.com');
    assert.deepEqual(result.created, []);
    assert.equal(result.existing.length, FACTORY_LABELS.length);
    assert.equal(calls.filter((call) => call.method === 'POST').length, 0);
  });

  it('prBody reflects label creation status (createLabels: true vs false)', () => {
    const bodyAuto = prBody(validConfig({ createLabels: true }));
    assert.match(
      bodyAuto,
      /- \[x\] Labels: agent:dev, agent:review, agent:rework, agent:skip, ai_developed, ai_validating, ai_validated, ai_pr_reviewed, pr_approved, validation_failed, blocked, needs-human \(auto-created during setup\)/,
    );

    const bodyManual = prBody(validConfig({ createLabels: false }));
    assert.match(
      bodyManual,
      /- \[ \] Labels: agent:dev, agent:review, agent:rework, agent:skip, ai_developed, ai_validating, ai_validated, ai_pr_reviewed, pr_approved, validation_failed, blocked, needs-human/,
    );
    assert.doesNotMatch(bodyManual, /auto-created during setup/);
  });
});

describe('Issue #758 — Quality workflow in an empty repository', () => {
  it('crap4py skips gracefully when no Python files or no tests are collected (pytest exit code 5) and when coverage.json is missing', () => {
    const wf = renderQualityWorkflow(validConfig({ ciEnabled: true, qualityPreset: 'crap4py' }))!;
    assert.match(wf, /No Python files found — skipping test and coverage execution\./);
    assert.match(wf, /if \[ \$rc -eq 5 \]; then/);
    assert.match(wf, /No tests collected \(pytest exit code 5\) — skipping coverage report and CRAP analysis\./);
    assert.match(wf, /if \[ ! -f coverage\.json \]; then/);
    assert.match(wf, /No coverage\.json found — skipping CRAP analysis\./);
  });

  it('crap4js avoids hard cache: npm failure and skips gracefully when package.json, tests, or coverage data are missing', () => {
    const wf = renderQualityWorkflow(validConfig({ ciEnabled: true, qualityPreset: 'crap4js' }))!;
    assert.doesNotMatch(wf, /cache:\s*npm/);
    assert.match(wf, /No package\.json found — skipping dependency install\./);
    assert.match(wf, /No tests configured or no package\.json found — skipping test and coverage execution\./);
    assert.match(wf, /if \[ ! -f coverage\/coverage-final\.json \]; then/);
    assert.match(wf, /No coverage data found — skipping CRAP analysis\./);
  });

  it('crap4dart skips gracefully when pubspec.yaml, test/*_test.dart, or coverage/lcov.info are missing', () => {
    const wf = renderQualityWorkflow(validConfig({ ciEnabled: true, qualityPreset: 'crap4dart' }))!;
    assert.match(wf, /if \[ ! -f pubspec\.yaml \]; then/);
    assert.match(wf, /No pubspec\.yaml found — skipping Dart quality checks\./);
    assert.match(wf, /No Dart tests found in test\/ — skipping test coverage and CRAP analysis\./);
    assert.match(wf, /if \[ ! -f coverage\/lcov\.info \]; then/);
    assert.match(wf, /No coverage\/lcov\.info found — skipping CRAP analysis\./);
  });

  it('crap4java skips gracefully when no Java build file or source files exist', () => {
    const wf = renderQualityWorkflow(validConfig({ ciEnabled: true, qualityPreset: 'crap4java' }))!;
    assert.match(wf, /No Java build file or source files found — skipping Java build and CRAP analysis\./);
  });

  it('crap4go skips gracefully when go.mod, Go sources, test files, or coverage.out are missing', () => {
    const wf = renderQualityWorkflow(validConfig({ ciEnabled: true, qualityPreset: 'crap4go' }))!;
    assert.match(wf, /No Go source files found — skipping format check\./);
    assert.match(wf, /No go\.mod or Go source files found — skipping Go test coverage and CRAP analysis\./);
    assert.match(wf, /No Go test files found — skipping coverage and CRAP analysis\./);
    assert.match(wf, /if \[ ! -f coverage\.out \]; then/);
    assert.match(wf, /No coverage\.out found — skipping CRAP analysis\./);
  });
});

describe('FA Universal Agent & Project Memory options', () => {
  it('defaults faUniversal and faMemory to true and generates .github/workflows/fa-universal.yml and .fah/config.yaml', () => {
    const cfg = validConfig();
    assert.equal(cfg.faUniversal, true);
    assert.equal(cfg.faMemory, true);
    assert.equal(isFaUniversalEnabled(cfg), true);
    assert.equal(isFaMemoryEnabled(cfg), true);

    const files = renderFiles(cfg);
    const byPath = Object.fromEntries(files.map((f) => [f.path, f.content]));

    assert.ok(byPath['.github/workflows/fa-universal.yml'], 'should generate fa-universal.yml');
    assert.match(byPath['.github/workflows/fa-universal.yml'], /uses:\s*IstiN\/dmtools-agentic-workflows\/\.github\/workflows\/fa-universal\.yml@main/);
    assert.match(byPath['.github/workflows/fa-universal.yml'], /persist_memory:\s*true/);
    assert.match(byPath['.github/workflows/fa-universal.yml'], /CUSTOM_PROVIDER_KEY1_NAME:\s*'COPILOT_GITHUB_TOKEN'/);
    assert.match(byPath['.github/workflows/fa-universal.yml'], /CUSTOM_PROVIDER_KEY1:\s*\$\{\{\s*secrets\.COPILOT_GITHUB_TOKEN\s*\}\}/);

    assert.ok(byPath['.fah/config.yaml'], 'should generate .fah/config.yaml');
    assert.equal(byPath['.fah/config.yaml'], 'memory:\n  projectPath: .fah/memory\n');

    const body = prBody(cfg);
    assert.match(body, /FA Universal Agent/);
    assert.match(body, /Project Memory/);
  });

  it('propagates custom runner label to fa-universal.yml', () => {
    const cfg = validConfig({ runner: 'my-corp-self-hosted' });
    const files = renderFiles(cfg);
    const byPath = Object.fromEntries(files.map((f) => [f.path, f.content]));
    assert.match(byPath['.github/workflows/fa-universal.yml'], /runner:\s*my-corp-self-hosted/);
  });

  it('omits fa-universal.yml when faUniversal is false and omits .fah/config.yaml when faMemory is false', () => {
    const cfg = validConfig({ faUniversal: false, faMemory: false });
    assert.equal(isFaUniversalEnabled(cfg), false);
    assert.equal(isFaMemoryEnabled(cfg), false);
    assert.equal(renderFahConfig(cfg), null);

    const files = renderFiles(cfg);
    const byPath = Object.fromEntries(files.map((f) => [f.path, f.content]));
    assert.equal(byPath['.github/workflows/fa-universal.yml'], undefined);
    assert.equal(byPath['.fah/config.yaml'], undefined);

    const body = prBody(cfg);
    assert.doesNotMatch(body, /FA Universal Agent/);
    assert.doesNotMatch(body, /Project Memory/);
  });

  it('sets persist_memory: false in fa-universal.yml when faUniversal is true and faMemory is false', () => {
    const cfg = validConfig({ faUniversal: true, faMemory: false });
    const files = renderFiles(cfg);
    const byPath = Object.fromEntries(files.map((f) => [f.path, f.content]));
    assert.ok(byPath['.github/workflows/fa-universal.yml']);
    assert.match(byPath['.github/workflows/fa-universal.yml'], /persist_memory:\s*false/);
    assert.equal(byPath['.fah/config.yaml'], undefined);
  });
});
