import { describe, it, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { spawnSync, type SpawnSyncReturns } from 'node:child_process';
import { resolve, dirname, join } from 'node:path';
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
  renderGitlabMachineSmFiles,
  loadGitlabTemplates,
  GITLAB_TEMPLATE_NAMES,
  GITLAB_WORKFLOW_SOURCE,
  type GitlabTemplates,
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
const gitlabTemplates = Object.fromEntries(GITLAB_TEMPLATE_NAMES.map((name) => [
  name, readFileSync(resolve(here, 'fixtures/gitlab', name), 'utf8'),
])) as GitlabTemplates;

describe('GitLab parent workflow loading', () => {
  const originalFetch = globalThis.fetch;
  afterEach(() => { globalThis.fetch = originalFetch; });

  it('loads all templates from the requested parent branch without credentials', async () => {
    const calls: { url: string; init?: RequestInit }[] = [];
    globalThis.fetch = (async (input, init) => {
      const url = String(input);
      calls.push({ url, init });
      const name = url.split('/').at(-1) as keyof GitlabTemplates;
      return new Response(gitlabTemplates[name]);
    }) as typeof fetch;
    const controller = new AbortController();
    assert.deepEqual(await loadGitlabTemplates(controller.signal), gitlabTemplates);
    assert.equal(calls.length, GITLAB_TEMPLATE_NAMES.length);
    for (const { url, init } of calls) {
      assert.ok(url.startsWith('https://raw.githubusercontent.com/IstiN/dmtools-agentic-workflows/feat/gitlab-agent-workflows/gitlab/'));
      assert.equal(init?.credentials, 'omit');
      assert.equal(init?.headers, undefined, 'GitLab tokens must never be sent to the parent repository');
      assert.equal(init?.signal, controller.signal);
    }
    assert.equal(GITLAB_WORKFLOW_SOURCE.ref, 'feat/gitlab-agent-workflows');
  });

  for (const status of [404, 500]) {
    it(`rejects HTTP ${status} instead of using local templates`, async () => {
      globalThis.fetch = (async () => new Response('unavailable', { status })) as typeof fetch;
      await assert.rejects(loadGitlabTemplates(), new RegExp(`Failed to load GitLab template.*HTTP ${status}`));
    });
  }

  it('rejects empty templates', async () => {
    globalThis.fetch = (async () => new Response(' \n')) as typeof fetch;
    await assert.rejects(loadGitlabTemplates(), /GitLab template.*is empty/);
  });

  it('propagates network and cancellation failures', async () => {
    globalThis.fetch = (async () => { throw new Error('network unavailable'); }) as typeof fetch;
    await assert.rejects(loadGitlabTemplates(), /network unavailable/);
    globalThis.fetch = (async (_input, init) => {
      init?.signal?.throwIfAborted();
      return new Response('');
    }) as typeof fetch;
    const controller = new AbortController();
    controller.abort();
    await assert.rejects(loadGitlabTemplates(controller.signal), { name: 'AbortError' });
  });

  it('uses the fetched parent content when rendering setup files', async () => {
    globalThis.fetch = (async (input) => {
      const name = String(input).split('/').at(-1) as keyof GitlabTemplates;
      return new Response(name === 'gitlab-ci.yml'
        ? `# parent workflow revision\n${gitlabTemplates[name]}`
        : gitlabTemplates[name]);
    }) as typeof fetch;
    const files = renderGitlabFiles(validConfig(), await loadGitlabTemplates());
    assert.match(files.find((file) => file.path === '.gitlab-ci.yml')!.content, /^# parent workflow revision/);
  });

  it('sources the entire automatic lifecycle and account selector from downloaded templates', () => {
    const names = [
      'gitlab-auto-ci.yml', 'gitlab-factory-common.sh', 'gitlab-factory-job.sh',
      'gitlab-factory-lifecycle.sh', 'gitlab-factory-intake.sh', 'gitlab-factory-rework.sh',
      'gitlab-factory-select-teammate.sh',
    ] as const;
    const templates = { ...gitlabTemplates };
    for (const name of names) templates[name] = `# fetched:${name}\n${templates[name]}`;
    const files = renderGitlabFiles(validConfig({ gitlabAutoLifecycle: true, gitlabMultipleTeammates: true }), templates);
    for (const name of names) {
      assert.ok(files.some((file) => file.content.includes(`# fetched:${name}`)), `${name} must come from the parent`);
    }
  });

  it('generates a scheduled and manually selectable GitLab machine SM workflow', () => {
    const files = renderGitlabMachineSmFiles(validConfig());
    const machineSm = files[0].content;
    const teammate = files[1].content;
    assert.match(machineSm, /gitlab\/factory-sm\.yml/);
    assert.match(machineSm, /1a3b5f373654def6871a91bf616e0280d00c42b7/);
    assert.match(machineSm, /local: ai-teammate\.yml/);
    assert.match(machineSm, /^factory-sm:\n/m);
    assert.match(machineSm, /^factory-sm:\n  extends: \.factory-sm/m);
    assert.match(machineSm, /CI_PIPELINE_SOURCE == "schedule"/);
    assert.doesNotMatch(machineSm, /DMTOOLS_AI_TEAMMATE_SCAN/);
    assert.match(machineSm, /CI_PIPELINE_SOURCE == "web" && \$DMTOOLS_MACHINE_SM == "true"/);
    assert.match(machineSm, /SM_DRY_RUN: "false"/);
    assert.match(machineSm, /SM_DRY_RUN: "true"/);
    assert.match(machineSm, /SM_MACHINE_AUTHOR: "ai-teammate"/);
    assert.match(machineSm, /FACTORY_ISSUE:\n    value: ""/);
    assert.match(machineSm, /FACTORY_MR:\n    value: ""/);
    assert.match(machineSm, /FACTORY_LEG:\n    value: ""/);
    assert.match(machineSm, /project-local issue IID for the ai-teammate job/);
    assert.match(machineSm, /project-local merge request IID for the ai-teammate job/);
    assert.match(machineSm, /Acquire::Retries=3 update/);
    assert.match(machineSm, /apt-get .*Acquire::Retries=3 install .*curl jq git nodejs gh unzip/);
    assert.match(machineSm, /Package install failed; refreshing apt indexes/);
    assert.doesNotMatch(machineSm, /dmtools run sm_gitlab@latest/);
    assert.doesNotMatch(machineSm, /@@GITLAB_TOKEN_MAPPING@@/);
    assert.doesNotMatch(machineSm, /@@[A-Z_]+@@/);
    assert.deepEqual(files.map((file) => file.path), [
      '.gitlab-ci.yml',
      'ai-teammate.yml',
      '.dmtools/config.js',
      '.dmtools/runners/fa-bug-dev.json',
      '.dmtools/runners/fa-story-dev.json',
      '.dmtools/runners/fa-review.json',
      '.dmtools/runners/fa-rework.json',
    ]);
    assert.match(teammate, /gitlab\/factory-teammate\.yml/);
    assert.match(teammate, /^ai-teammate:\n  extends: \.factory-teammate/m);
    assert.match(teammate, /FACTORY_HOME_REF: "1a3b5f373654def6871a91bf616e0280d00c42b7"/);
    assert.match(teammate, /FACTORY_ISSUE/);
    assert.match(teammate, /FACTORY_MR/);
    assert.match(teammate, /^ai-teammate-auto:\n  extends: \.factory-teammate/m);
    assert.match(teammate, /AGENT_HANDLE: "\$DMTOOLS_AI_DEV_USERNAME"/);
    assert.match(teammate, /key: "DMTOOLS_AI_TEAMMATE_SCAN", value: "true"/);
    assert.match(teammate, /key: "FACTORY_HOME_REF", value: "[0-9a-f]{40}"/);
    assert.match(teammate, /key: "DMTOOLS_AI_DEV_USERNAME", value: \$username/);
    assert.match(teammate, /assignee="\$\{DMTOOLS_AI_DEV_USERNAME:-ai-dev\}"/);
    assert.match(teammate, /ai-teammate-scan:[\s\S]*?rules:\n    - if: '\$CI_COMMIT_BRANCH == \$CI_DEFAULT_BRANCH && \$CI_PIPELINE_SOURCE == "schedule"'\n/);
    assert.match(teammate, /No eligible queued issues found on page/);
    assert.match(teammate, /Starting ai-teammate API pipeline for issue/);
    assert.doesNotMatch(teammate, /--data-urlencode "labels=agent:dev"/);
    assert.match(teammate, /index\("agent:rework"\)/);
    assert.match(teammate, /key: "FACTORY_ISSUE", value: \$issue/);
    assert.match(teammate, /curl --fail-with-body/);
    assert.doesNotMatch(teammate, /--form/);
    assert.doesNotMatch(teammate, /@@GITLAB_TOKEN_MAPPING@@/);
    assert.doesNotMatch(teammate, /@@[A-Z_]+@@/);
    const machineConfig = files.find((file) => file.path === '.dmtools/config.js')!;
    assert.match(machineConfig.content, /machineAuthor: 'ai-teammate'/);
    assert.match(machineConfig.content, /bug: '.dmtools\/runners\/fa-bug-dev\.json'/);
    assert.match(machineConfig.content, /story: '.dmtools\/runners\/fa-story-dev\.json'/);
    assert.match(machineConfig.content, /review: '.dmtools\/runners\/fa-review\.json'/);
    assert.match(machineConfig.content, /rework: '.dmtools\/runners\/fa-rework\.json'/);
    const storyRunner = JSON.parse(files.find((file) => file.path === '.dmtools/runners/fa-story-dev.json')!.content);
    assert.equal(storyRunner.params.envVariables.DEFAULT_TRACKER, 'gitlab');
    assert.equal(storyRunner.params.envVariables.DEFAULT_SCM, 'gitlab');
    assert.equal(storyRunner.params.envVariables.GITLAB_BASE_PATH, 'https://gitlab.com');
    for (const path of ['.dmtools/runners/fa-bug-dev.json', '.dmtools/runners/fa-story-dev.json']) {
      const devRunner = JSON.parse(files.find((file) => file.path === path)!.content);
      assert.equal(devRunner.params.customParams.scmProvider, 'gitlab', `${path} must create GitLab MRs`);
      assert.equal(devRunner.params.customParams.targetRepository.owner, 'acme');
      assert.equal(devRunner.params.customParams.targetRepository.repo, 'demo');
      assert.equal(devRunner.params.customParams.targetRepository.workingDir, '.');
      assert.ok(devRunner.params.cliPrompts.some((prompt: string) => /first line must be exactly "Closes #<IID>"/.test(prompt)));
      assert.equal(devRunner.params.postJSAction, undefined, 'pack post-action must commit, push and create the MR');
    }
    const reworkRunner = JSON.parse(files.find((file) => file.path === '.dmtools/runners/fa-rework.json')!.content);
    assert.equal(reworkRunner.params.customParams.scmProvider, 'gitlab');
    assert.equal(reworkRunner.params.customParams.targetRepository.owner, 'acme');
    for (const action of ['preJSAction', 'preCliJSAction', 'postJSAction']) {
      assert.equal(reworkRunner.params[action], undefined, `rework must keep the pack ${action} that pushes to the MR`);
    }
    assert.doesNotMatch(JSON.stringify(reworkRunner), /DMTOOLS_REWORK_CONTEXT/);
    const reviewRunner = JSON.parse(files.find((file) => file.path === '.dmtools/runners/fa-review.json')!.content);
    assert.equal(reviewRunner.params.customParams.scmProvider, 'gitlab');
    assert.equal(reviewRunner.params.customParams.targetRepository.repo, 'demo');
    assert.equal(reviewRunner.params.postJSAction, undefined, 'review must keep the pack publisher');
    assert.doesNotMatch(JSON.stringify(reviewRunner), /DMTOOLS_REVIEW_CONTEXT/);
    assert.ok(reviewRunner.params.cliPrompts.includes('pack:instructions/pr_review/review_verdict_rules.md'));
    assert.ok(reviewRunner.params.cliPrompts.some((prompt: string) => /top-level "decision" field/.test(prompt)));
    for (const auto of [true, false]) {
      const review = JSON.parse(renderGitlabMachineSmFiles(validConfig({ gitlabAutoLifecycle: auto }))
        .find((file) => file.path === '.dmtools/runners/fa-review.json')!.content);
      assert.equal(review.params.preCliJSAction, undefined);
    }
    const multiTeammateWorkflow = renderGitlabMachineSmFiles(validConfig({
      gitlabMultipleTeammates: true,
      gitlabDev: 'dev-bot',
    }));
    const multiMachineSm = multiTeammateWorkflow[0].content;
    const multiTeammate = multiTeammateWorkflow[1].content;
    const multiMachineConfig = multiTeammateWorkflow.find((file) => file.path === '.dmtools/config.js')!;
    assert.match(multiMachineSm, /SM_MACHINE_AUTHOR: "dev-bot"/);
    assert.match(multiMachineSm, /DMTOOLS_GITLAB_TEAMMATES: "multiple"/);
    assert.match(machineSm, /DMTOOLS_GITLAB_TEAMMATES: "single"/);
    assert.match(multiTeammate, /AGENT_HANDLE: "dev-bot"/);
    for (const content of [machineSm, multiMachineSm, teammate, multiTeammate]) {
      assert.doesNotMatch(content, /GITLAB_TOKEN: "\$GITLAB_(DEV|REVIEWER)_TOKEN"/);
      assert.doesNotMatch(content, /@@/);
    }
    for (const content of [machineSm, multiMachineSm]) {
      assert.match(content, /default:\n  image: [^\n]+\n  before_script:\n    - !reference \[\.dmtools-install-tools, script\]\n    - !reference \[\.dmtools-use-dev-token, script\]\n/);
    }
    assert.match(multiTeammate, /ai-teammate:[\s\S]*?GITLAB_REVIEWER_HANDLE: "ai-reviewer"/);
    assert.match(multiTeammate, /ai-teammate-auto:[\s\S]*?GITLAB_REVIEWER_HANDLE: "ai-reviewer"/);
    assert.doesNotMatch(teammate, /GITLAB_REVIEWER_HANDLE: "/);
    assert.match(multiMachineConfig.content, /machineAuthor: 'dev-bot'/);
    for (const [content, mode] of [[teammate, 'single'], [multiTeammate, 'multi']] as const) {
      for (const job of ['ai-teammate', 'ai-teammate-auto']) {
        const body = content.split(`\n${job}:\n`)[1].split(/\n\n/)[0];
        assert.equal(body.match(/^  variables:$/gm)?.length, 1, `${job} must have one variables block (${mode})`);
        assert.match(body, /FACTORY_HOME_REF: "[0-9a-f]{40}"/);
        assert.match(body, /before_script:\n    - !reference \[\.dmtools-install-tools, script\]\n    - !reference \[\.dmtools-use-dev-token, script\]\n    - !reference \[\.ai-teammate-select-token, script\]\n    - !reference \[\.ai-teammate-git-auth, script\]\n    - !reference \[\.ai-teammate-resolve-handle, script\]/);
      }
    }
    assert.match(machineSm, /before_script:\n    - !reference \[\.dmtools-install-tools, script\]/);
    assert.match(machineSm, /^\.dmtools-install-tools:\n  script:\n    - \|/m);
    assert.doesNotMatch(multiTeammate, /@@GITLAB_TOKEN_VARIABLE@@/);
  });

  it('removes runner job-token git rewrites, including included config, and checks GITLAB_TOKEN push access', () => {
    const teammate = renderGitlabMachineSmFiles(validConfig())[1].content;
    const script = teammate.split('\n.ai-teammate-git-auth:\n')[1].split('    - |\n')[1].split('\n\n')[0]
      .split('\n').map((line) => line.replace(/^      /, '')).join('\n');
    const dir = mkdtempSync(join(tmpdir(), 'gl-auth-'));
    try {
      let runs = 0;
      const run = (access: number, scopes: string, options: { rewritten?: boolean; remote?: 'ok' | 'missing' } = {}) => {
        const { rewritten = true, remote = 'ok' } = options;
        const id = runs++;
        const repo = join(dir, `r${id}`);
        const included = join(dir, `r${id}.tmp-git-template-config`);
        const bare = join(dir, `remote${id}`);
        const setup = spawnSync('bash', ['-c', `
          set -e
          mkdir -p tpl && git init -q --template=tpl "${repo}" && cd "${repo}"
          git -c user.name=t -c user.email=t@t commit -q --allow-empty -m init
          if [ "${remote}" = ok ]; then git init -q --bare "${bare}.git"; fi
          if [ "${rewritten}" = true ]; then
            git config -f "${included}" url."https://gitlab-ci-token:SECRET@git.example.com/".insteadOf "https://git.example.com/"
            git config -f "${included}" credential."https://git.example.com".username gitlab-ci-token
            git config --local include.path "${included}"
            git config --local url."https://gitlab-ci-token:SECRET@git.example.com/".pushInsteadOf "${bare}"
            git config url."https://mirror.example.com/".insteadOf "git@mirror:"
          fi
          git remote add origin https://git.example.com/mobile/factory.git
        `], { cwd: dir, encoding: 'utf8' });
        assert.equal(setup.status, 0, setup.stderr);
        return spawnSync('bash', ['-c', `
          set -euo pipefail
          curl() {
            case "$*" in
              *personal_access_tokens/self*) printf '{"scopes":[%s]}' "$SCOPES" ;;
              *) printf '{"permissions":{"project_access":{"access_level":%s},"group_access":null}}' "$ACCESS" ;;
            esac
          }
          ${script}
          printf 'PUSH=%s\\n' "$(git remote get-url --push origin)"
          git config --list --show-origin
          test -d .git/info && echo INFO_OK
        `], {
          cwd: repo, encoding: 'utf8',
          env: { PATH: process.env.PATH, HOME: dir, GIT_CONFIG_NOSYSTEM: '1', GITLAB_TOKEN: 't',
            CI_API_V4_URL: 'https://git.example.com/api/v4', CI_PROJECT_ID: '1', CI_JOB_ID: '7', CI_PROJECT_URL: bare,
            ACCESS: String(access), SCOPES: scopes },
        });
      };
      const ok = run(40, '"api"');
      assert.equal(ok.status, 0, ok.stderr);
      assert.match(ok.stdout, /Removed CI job-token git config from .*tmp-git-template-config/);
      assert.match(ok.stdout, /PUSH=.*remote0\.git\n/);
      assert.doesNotMatch(ok.stdout, /gitlab-ci-token|SECRET/);
      assert.match(ok.stdout, /url\.https:\/\/mirror\.example\.com\/\.insteadof=git@mirror:/);
      assert.match(ok.stdout, /INFO_OK/);
      assert.match(ok.stdout, /GITLAB_TOKEN project access level: 40; scopes: api/);
      assert.match(ok.stdout, /Git push access check with GITLAB_TOKEN: OK/);
      assert.equal(ok.stderr, '');
      const weak = run(20, '"read_api","read_repository"');
      assert.equal(weak.status, 0, weak.stderr);
      assert.match(weak.stderr, /needs at least the Developer role/);
      assert.match(weak.stderr, /needs the api or write_repository scope/);
      const clean = run(40, '"api"', { rewritten: false });
      assert.equal(clean.status, 0, clean.stderr);
      assert.match(clean.stdout, /Git push URL: .*remote2\.git\n/);
      assert.doesNotMatch(clean.stdout, /Removed CI job-token/);
      const denied = run(40, '"api"', { remote: 'missing' });
      assert.equal(denied.status, 0, denied.stderr);
      assert.match(denied.stderr, /git push access check with GITLAB_TOKEN failed/);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  it('fails dev legs that finish without an open merge request instead of queueing review', () => {
    const teammate = renderGitlabMachineSmFiles(validConfig())[1].content;
    for (const job of ['ai-teammate', 'ai-teammate-auto']) {
      const body = teammate.split(`\n${job}:\n`)[1].split(/\n\n/)[0];
      assert.match(body, /\n  script:\n    - !reference \[\.factory-teammate, script\]\n    - !reference \[\.ai-teammate-verify-mr, script\]/);
    }
    const script = teammate.split('\n.ai-teammate-verify-mr:\n')[1].split('    - |\n')[1].split('\n\n')[0]
      .split('\n').map((line) => line.replace(/^      /, '')).join('\n');
    const run = (env: Record<string, string>) => spawnSync('bash', ['-c', `
      set -euo pipefail
      api() {
        printf 'API %s\\n' "$*" >&2
        case "$1" in
          issues/*/related_merge_requests*) printf '%s' "$RELATED" ;;
          merge_requests\\?*) printf '%s' "$BRANCH_MRS" ;;
          issues/17) printf '%s' "$ISSUE" ;;
          *) printf '{}' ;;
        esac
      }
      update_labels() { printf 'LABELS add=%s remove=%s\\n' "$1" "$2"; }
      note() { printf 'NOTE %s\\n' "$1"; }
      leg="$LEG"; number=17; anchor=gl-17; agent_result=0
      ${script}
      echo DONE
    `], { encoding: 'utf8', env: { PATH: process.env.PATH, CI_PROJECT_ID: '5', BRANCH_MRS: '[]', LEG: 'dev',
      ISSUE: '{"labels":["agent:dev"]}', ...env } });
    const linked = run({ RELATED: '[{"iid":3,"state":"opened","project_id":5}]' });
    assert.equal(linked.status, 0, linked.stderr);
    assert.match(linked.stdout, /LABELS add=ai_developed remove=agent:dev\n/);
    assert.match(linked.stdout, /Issue #17 has 1 open merge request\(s\); labeled ai_developed and removed agent:dev\nDONE/);
    const byBranch = run({ RELATED: '[]', BRANCH_MRS: '[{"iid":9,"description":"Body"}]' });
    assert.equal(byBranch.status, 0, byBranch.stderr);
    assert.match(byBranch.stderr, /source_branch=ai%2Fgl-17/);
    assert.match(byBranch.stderr, /API merge_requests\/9 --request PUT --data-urlencode description=Closes #17\n\nBody/);
    assert.match(byBranch.stdout, /Linked merge request !9 to issue #17/);
    assert.match(byBranch.stdout, /LABELS add=ai_developed remove=agent:dev/);
    const missing = run({ RELATED: '[{"iid":4,"state":"closed","project_id":5},{"iid":2,"state":"opened","project_id":6}]' });
    assert.equal(missing.status, 1);
    assert.match(missing.stdout, /LABELS add=needs-human remove=agent:review/);
    assert.doesNotMatch(missing.stdout, /add=ai_developed/);
    assert.match(missing.stdout, /NOTE AI Teammate dev run finished without an open merge request/);
    assert.doesNotMatch(missing.stdout, /DONE/);
    const asked = run({ RELATED: '[]', ISSUE: '{"labels":["agent:dev","ai_questions_asked"]}' });
    assert.equal(asked.status, 0, asked.stderr);
    assert.match(asked.stdout, /waiting for answers \(ai_questions_asked\); no merge request expected yet/);
    assert.doesNotMatch(asked.stdout, /needs-human|ai_developed|DONE/);
    const review = run({ LEG: 'review', RELATED: '[]' });
    assert.equal(review.status, 0, review.stderr);
    assert.doesNotMatch(review.stderr, /API/);
  });

  it('selects GITLAB_DEV_TOKEN for development legs and GITLAB_REVIEWER_TOKEN for review legs at runtime', () => {
    const extract = (content: string, job: string) => content.split(`\n${job}:\n`)[1].split('    - |\n')[1].split('\n\n')[0]
      .split('\n').map((line) => line.replace(/^      /, '')).join('\n');
    const render = (multi: boolean) => {
      const files = renderGitlabMachineSmFiles(validConfig(multi ? { gitlabMultipleTeammates: true, gitlabDev: 'dev-bot' } : {}));
      return `${extract(files[0].content, '.dmtools-use-dev-token')}\n${extract(files[1].content, '.ai-teammate-select-token')}`;
    };
    const run = (multi: boolean, labels: string, env: Record<string, string | undefined> = {}) => spawnSync('bash', ['-c', `
      set -euo pipefail
      curl() {
        case "$*" in
          */user) case "$*" in *review-token*) echo '{"username":"ai-reviewer"}' ;; *dev-token*) echo '{"username":"dev-bot"}' ;;
            *) echo '{"username":"setup-user"}' ;; esac ;;
          *) printf '%s' '{"labels":'"$LABELS"'}' ;;
        esac
      }
      ${render(multi)}
      printf 'TOKEN=%s HANDLE=%s ROLE=%s\\n' "$GITLAB_TOKEN" "$AGENT_HANDLE" "$FACTORY_TEAMMATE_ROLE"
    `], {
      encoding: 'utf8',
      env: Object.fromEntries(Object.entries({
        PATH: process.env.PATH, FACTORY_ISSUE: '17', GITLAB_TOKEN: 'setup-token', GITLAB_DEV_TOKEN: 'dev-token',
        GITLAB_REVIEWER_TOKEN: 'review-token', AGENT_HANDLE: 'dev-bot',
        DMTOOLS_GITLAB_TEAMMATES: multi ? 'multiple' : 'single', ...(multi ? { GITLAB_REVIEWER_HANDLE: 'ai-reviewer' } : {}),
        CI_API_V4_URL: 'https://gitlab.example.com/api/v4', CI_PROJECT_ID: '1', LABELS: labels, ...env,
      }).filter((entry): entry is [string, string] => entry[1] !== undefined)),
    });
    const dev = run(true, '["agent:dev"]');
    assert.equal(dev.status, 0, dev.stderr);
    assert.match(dev.stdout, /Using GITLAB_DEV_TOKEN as GITLAB_TOKEN \(GitLab user: dev-bot\)/);
    assert.match(dev.stdout, /Development leg \(dev\): keeping the development token/);
    assert.match(dev.stdout, /TOKEN=dev-token HANDLE=dev-bot ROLE=dev/);
    const rework = run(true, '["agent:dev","agent:rework"]');
    assert.match(rework.stdout, /TOKEN=dev-token HANDLE=dev-bot ROLE=dev/);
    const review = run(true, '["agent:dev","ai_developed","agent:review"]');
    assert.equal(review.status, 0, review.stderr);
    assert.match(review.stdout, /Review leg: using GITLAB_REVIEWER_TOKEN as GITLAB_TOKEN \(GitLab user: ai-reviewer\)/);
    assert.match(review.stdout, /TOKEN=review-token HANDLE=ai-reviewer ROLE=reviewer/);
    const inReview = run(true, '["agent:review","status:In Review"]');
    assert.match(inReview.stdout, /TOKEN=dev-token .*ROLE=dev/);
    const explicit = run(true, '["agent:dev"]', { FACTORY_LEG: 'review' });
    assert.match(explicit.stdout, /TOKEN=review-token HANDLE=ai-reviewer ROLE=reviewer/);
    const missingReviewer = run(true, '["agent:review"]', { GITLAB_REVIEWER_TOKEN: undefined });
    assert.notEqual(missingReviewer.status, 0);
    assert.match(missingReviewer.stderr, /GITLAB_REVIEWER_TOKEN is required/);
    const missingDev = run(true, '["agent:dev"]', { GITLAB_DEV_TOKEN: undefined });
    assert.notEqual(missingDev.status, 0);
    assert.match(missingDev.stderr, /GITLAB_DEV_TOKEN is required in multi-teammate mode/);
    const singleSetup = run(false, '["agent:review"]', { GITLAB_DEV_TOKEN: undefined, GITLAB_REVIEWER_TOKEN: undefined });
    assert.equal(singleSetup.status, 0, singleSetup.stderr);
    assert.match(singleSetup.stdout, /Using GITLAB_TOKEN as GITLAB_TOKEN \(GitLab user: setup-user\)/);
    assert.match(singleSetup.stdout, /TOKEN=setup-token HANDLE=dev-bot ROLE=dev/);
    const singleDev = run(false, '["agent:dev"]', { GITLAB_REVIEWER_TOKEN: undefined });
    assert.match(singleDev.stdout, /TOKEN=dev-token HANDLE=dev-bot ROLE=dev/);
  });

  it('resolves AGENT_HANDLE from issue assignee display names before the teammate parent runs', () => {
    const teammate = renderGitlabMachineSmFiles(validConfig())[1].content;
    const resolver = teammate.split('\n.ai-teammate-resolve-handle:\n')[1];
    const script = resolver.split('    - |\n')[1].split('\n\n')[0]
      .split('\n').map((line) => line.replace(/^      /, '')).join('\n');
    const run = (handle: string, extra = '', labels = '') => spawnSync('bash', ['-c', `
      set -euo pipefail
      curl() {
        if [ "\${FAIL:-}" = true ]; then return 22; fi
        case " $* " in *" PUT "*) printf 'PUT %s\\n' "$*" >&2; return 0 ;; esac
        printf '%s' '{"state":"opened","labels":'"$LABELS"',"assignees":[{"username":"project_297486_bot_abc","name":"ai-dev"},{"username":"human","name":"Human"}]}'
      }
      ${script}
      printf 'HANDLE=%s\\n' "$AGENT_HANDLE"
    `], {
      encoding: 'utf8',
      env: {
        PATH: process.env.PATH, FACTORY_ISSUE: '17', FACTORY_MR: '', GITLAB_TOKEN: 't', AGENT_HANDLE: handle,
        CI_API_V4_URL: 'https://gitlab.example.com/api/v4', CI_PROJECT_ID: '1', ...(extra ? { FAIL: extra } : {}),
        LABELS: labels || '["agent:dev"]',
      },
    });
    const byName = run('ai-dev');
    assert.equal(byName.status, 0, byName.stderr);
    assert.match(byName.stdout, /Resolved AGENT_HANDLE ai-dev to assignee username project_297486_bot_abc/);
    assert.match(byName.stdout, /HANDLE=project_297486_bot_abc/);
    assert.match(byName.stdout, /labels: agent:dev/);
    assert.match(byName.stdout, /assignees: project_297486_bot_abc \(ai-dev\), human \(Human\)/);
    assert.doesNotMatch(byName.stderr, /PUT/);
    const stale = run('ai-dev', '', '["agent:dev","ai_developed"]');
    assert.equal(stale.status, 0, stale.stderr);
    assert.match(stale.stderr, /PUT .*remove_labels=ai_developed/);
    assert.match(stale.stdout, /Cleared stale ai_developed from issue #17 before a new dev leg/);
    const reviewing = run('ai-dev', '', '["agent:dev","ai_developed","agent:review"]');
    assert.doesNotMatch(reviewing.stderr, /PUT/);
    const byUsername = run('human');
    assert.match(byUsername.stdout, /HANDLE=human\n/);
    assert.doesNotMatch(byUsername.stdout, /Resolved/);
    const missing = run('nobody');
    assert.equal(missing.status, 0);
    assert.match(missing.stdout, /HANDLE=nobody/);
    assert.match(missing.stderr, /no assignee of issue #17 matches AGENT_HANDLE=nobody/);
    const failed = run('ai-dev', 'true');
    assert.equal(failed.status, 0);
    assert.match(failed.stdout, /HANDLE=ai-dev/);
    assert.match(failed.stderr, /unable to read issue #17/);
  });

  it('scans queued dev and rework issues, respecting holds and active legs', () => {
    const teammate = renderGitlabMachineSmFiles(validConfig())[1].content;
    const script = teammate.split('\nai-teammate-scan:\n')[1].split('  script:\n    - |\n')[1]
      .split('\n').map((line) => line.replace(/^      /, '')).join('\n');
    const run = (issues: unknown, extraEnv: Record<string, string> = {}) => spawnSync('bash', ['-c', `
      curl() {
        case " $* " in
          *"/merge_requests"*)
            printf 'MR %s\\n' "$*" >&2
            case " $* " in
              *" --request PUT "*) if [ "\${FAIL_MR_PUT:-}" = true ]; then return 22; fi; printf '{}' ;;
              *" --get "*) case " $* " in *" page=1 "*) printf '%s' "\${MRS:-[]}" ;; *) printf '[]' ;; esac ;;
              *) url="\${@: -1}"; printf '%s' "\${MRS:-[]}" | jq -c --arg iid "\${url##*/}" 'first(.[] | select((.iid | tostring) == $iid))' ;;
            esac
            ;;
          *" --get "*)
            printf 'LIST %s\\n' "$*" >&2
            if [ "\${FAIL_LIST:-}" = true ]; then return 22; fi
            case " $* " in
              *" page=2 "*) printf '%s' "\${SECOND_PAGE:-[]}" ;;
              *" page=3 "*) printf '%s' '[]' ;;
              *) printf '%s' "$ISSUES" ;;
            esac
            ;;
          *)
            local data="" content_type=""
            while [ "$#" -gt 0 ]; do
              case "$1" in
                --data) data="$2"; shift 2 ;;
                --header)
                  if [ "$2" = "Content-Type: application/json" ]; then content_type=json; fi
                  shift 2 ;;
                *) shift ;;
              esac
            done
            [ "$content_type" = json ] || return 22
            local request
            request="$(printf '%s' "$data" | jq -ce '
              select((.ref | type == "string") and (.variables | type == "array")) |
              select(.variables | all(.[]; (.key | type == "string") and (.value | type == "string")))
            ')" || return 22
            printf 'DISPATCH %s\\n' "$request" >&2
            if [ "\${FAIL_DISPATCH:-}" = true ]; then
              printf '%s' '{"message":{"variables":["is invalid"]}}'
              return 22
            fi
            printf '%s' '{"id":123}'
            ;;
        esac
      }
      ${script}
    `], {
      encoding: 'utf8',
      env: {
        PATH: process.env.PATH,
        GITLAB_TOKEN: 'test-token',
        CI_API_V4_URL: 'https://gitlab.example.com/api/v4',
        CI_PROJECT_ID: '123',
        CI_DEFAULT_BRANCH: 'main',
        ISSUES: JSON.stringify(issues),
        ...extraEnv,
      },
    });
    const issue = (iid: number, username: string, name: string, labels = ['agent:dev']) => ({
      iid, state: 'opened', labels, assignees: [{ username, name }],
    });
    const eligible = run([
      issue(17, 'project_bot_123', 'ai-dev'),
      issue(18, 'ai-dev', 'Developer'),
      issue(19, 'other-bot', 'Other'),
      { ...issue(20, 'ai-dev', 'Developer'), state: 'closed' },
      { ...issue(21, 'ai-dev', 'Developer'), labels: ['agent:review'] },
      { ...issue(22, 'ai-dev', 'Developer'), assignees: [] },
    ]);
    assert.equal(eligible.status, 0, eligible.stderr);
    const requests = eligible.stderr.split('\n')
      .filter((line) => line.startsWith('DISPATCH '))
      .map((line) => JSON.parse(line.slice('DISPATCH '.length)));
    assert.deepEqual(requests, [
      {
        ref: 'main',
        variables: [
          { key: 'FACTORY_ISSUE', value: '17' },
          { key: 'FACTORY_MR', value: '' },
          { key: 'FACTORY_HOME_REF', value: '1a3b5f373654def6871a91bf616e0280d00c42b7' },
          { key: 'DMTOOLS_AI_TEAMMATE_SCAN', value: 'true' },
          { key: 'DMTOOLS_AI_DEV_USERNAME', value: 'project_bot_123' },
        ],
      },
      {
        ref: 'main',
        variables: [
          { key: 'FACTORY_ISSUE', value: '18' },
          { key: 'FACTORY_MR', value: '' },
          { key: 'FACTORY_HOME_REF', value: '1a3b5f373654def6871a91bf616e0280d00c42b7' },
          { key: 'DMTOOLS_AI_TEAMMATE_SCAN', value: 'true' },
          { key: 'DMTOOLS_AI_DEV_USERNAME', value: 'ai-dev' },
        ],
      },
    ]);
    assert.match(eligible.stdout, /Dispatched 2 ai-teammate pipelines/);

    const rework = run([
      issue(30, 'project_bot_123', 'ai-dev', ['agent:rework', 'ai_developed', 'ai_pr_reviewed']),
      { ...issue(31, 'other', 'Other', ['agent:rework']), assignees: [] },
      issue(32, 'other', 'Other', ['agent:dev', 'agent:rework', 'agent:review', 'rework-round:1']),
      issue(33, 'ai-dev', 'Developer', ['agent:dev', 'agent:rework', 'status:In Rework']),
      { ...issue(34, 'ai-dev', 'Developer', ['agent:rework']), state: 'closed' },
      ...['agent:skip', 'blocked', 'provider_quota', 'needs-human'].flatMap((hold, index) => [
        issue(40 + index, 'ai-dev', 'Developer', ['agent:rework', hold]),
        issue(50 + index, 'ai-dev', 'Developer', ['agent:dev', hold]),
      ]),
      ...['agent:review', 'status:In Development', 'status:In Review', 'status:In Rework', 'ai_questions_asked', 'ai_pr_reviewed']
        .map((label, index) => issue(60 + index, 'ai-dev', 'Developer', ['agent:dev', label])),
    ]);
    assert.equal(rework.status, 0, rework.stderr);
    const reworkRequests = rework.stderr.split('\n')
      .filter((line) => line.startsWith('DISPATCH '))
      .map((line) => JSON.parse(line.slice('DISPATCH '.length)));
    assert.deepEqual(reworkRequests, [
      ['30', 'project_bot_123'], ['31', 'ai-dev'], ['32', 'ai-dev'],
    ].map(([iid, username]) => ({
      ref: 'main',
      variables: [
        { key: 'FACTORY_ISSUE', value: iid },
        { key: 'FACTORY_MR', value: '' },
        { key: 'FACTORY_HOME_REF', value: '1a3b5f373654def6871a91bf616e0280d00c42b7' },
        { key: 'DMTOOLS_AI_TEAMMATE_SCAN', value: 'true' },
        { key: 'DMTOOLS_AI_DEV_USERNAME', value: username },
      ],
    })));
    assert.match(rework.stdout, /Dispatched 3 ai-teammate pipelines/);
    assert.doesNotMatch(rework.stderr, /labels=agent:dev/);
    assert.ok(reworkRequests.every((request) => !request.variables.some(
      (variable: { key: string }) => variable.key === 'FACTORY_LEG',
    )), 'the parent must derive the leg from live labels rather than a stale scan snapshot');

    const empty = run([]);
    assert.equal(empty.status, 0, empty.stderr);
    assert.match(empty.stdout, /Dispatched 0 ai-teammate pipelines/);
    assert.doesNotMatch(empty.stderr, /DISPATCH/);

    const custom = run([issue(23, 'custom-bot', 'Developer')], { DMTOOLS_AI_DEV_USERNAME: 'custom-bot' });
    assert.equal(custom.status, 0, custom.stderr);
    assert.match(custom.stderr, /"key":"DMTOOLS_AI_DEV_USERNAME","value":"custom-bot"/);
    const customRework = run([{ ...issue(24, 'other', 'Other', ['agent:rework']), assignees: [] }],
      { DMTOOLS_AI_DEV_USERNAME: 'custom-bot' });
    assert.equal(customRework.status, 0, customRework.stderr);
    assert.match(customRework.stderr, /"key":"DMTOOLS_AI_DEV_USERNAME","value":"custom-bot"/);

    const paged = run(Array.from({ length: 100 }, (_, index) => issue(index + 1, 'other', 'Other')), {
      SECOND_PAGE: JSON.stringify([issue(101, 'other', 'Other', ['agent:rework'])]),
    });
    assert.equal(paged.status, 0, paged.stderr);
    assert.match(paged.stderr, /page=2/);
    assert.match(paged.stderr, /"key":"FACTORY_ISSUE","value":"101"/);
    assert.match(paged.stdout, /Dispatched 1 ai-teammate pipelines/);

    const errorCases: Record<string, string>[] = [
      { FAIL_LIST: 'true' }, { FAIL_DISPATCH: 'true' }, { GITLAB_TOKEN: '' },
    ];
    for (const extraEnv of errorCases) {
      const failed = run([issue(17, 'ai-dev', 'Developer')], extraEnv);
      assert.notEqual(failed.status, 0, 'API or missing-token errors must fail the scan');
      assert.doesNotMatch(failed.stdout, /Dispatched \d+ ai-teammate pipelines/);
      if (extraEnv.FAIL_DISPATCH) {
        assert.match(failed.stdout, /"variables":\["is invalid"\]/);
      }
    }
    const malformed = run({ message: 'Unexpected API response' });
    assert.notEqual(malformed.status, 0);
    assert.match(malformed.stderr, /Expected an issues array/);
  });

  it('closes rework/review cycles on the linked issue and MR, like GitHub close-*-cycle scripts', () => {
    const teammate = renderGitlabMachineSmFiles(validConfig())[1].content;
    for (const job of ['ai-teammate', 'ai-teammate-auto']) {
      const body = teammate.split(`\n${job}:\n`)[1].split(/\n\n/)[0];
      assert.match(body, /\n  after_script:\n    - !reference \[\.factory-teammate, after_script\]\n    - !reference \[\.ai-teammate-close-cycle, script\]/);
    }
    const script = teammate.split('\n.ai-teammate-close-cycle:\n')[1].split('    - |\n')[1].split('\n\n')[0]
      .split('\n').map((line) => line.replace(/^      /, '')).join('\n');
    const run = (state: Record<string, string> | null, env: Record<string, string> = {}, files: Record<string, string> = {}) => {
      const dir = mkdtempSync(join(tmpdir(), 'gl-close-'));
      try {
        mkdirSync(join(dir, '.dmtools'));
        mkdirSync(join(dir, 'outputs'));
        if (state) writeFileSync(join(dir, '.dmtools/factory-state.json'), JSON.stringify(state));
        for (const [name, content] of Object.entries(files)) writeFileSync(join(dir, name), content);
        return spawnSync('bash', ['-c', `
          curl() {
            local method=GET token="" data="" url="\${@: -1}"
            while [ "$#" -gt 1 ]; do
              case "$1" in
                --request) method="$2"; shift 2 ;;
                --header) token="\${2#PRIVATE-TOKEN: }"; shift 2 ;;
                --data-urlencode) data="$data $2"; shift 2 ;;
                *) shift ;;
              esac
            done
            printf 'CALL %s %s %s%s\\n' "$token" "$method" "\${url#https://gl/api/v4/projects/5/}" "$data" >&2
            [ "$method" = GET ] || { printf '{}'; return 0; }
            case "$url" in
              */related_merge_requests*) printf '%s' "\${RELATED:-[]}" ;;
              */merge_requests/*) printf '%s' "\${MR:-null}" ;;
              */issues/*) printf '%s' "\${ISSUE:-null}" ;;
            esac
          }
          ${script}
          echo DONE
        `], { cwd: dir, encoding: 'utf8', env: { PATH: process.env.PATH, CI_API_V4_URL: 'https://gl/api/v4',
          CI_PROJECT_ID: '5', GITLAB_TOKEN: 'project', GITLAB_DEV_TOKEN: 'dev', ...env } });
      } finally {
        rmSync(dir, { recursive: true, force: true });
      }
    };
    const calls = (result: SpawnSyncReturns<string>) => result.stderr.split('\n').filter((line) => line.startsWith('CALL '));
    const writes = (result: SpawnSyncReturns<string>) => calls(result).filter((line) => / PUT /.test(line));

    const related = JSON.stringify([
      { iid: 8, state: 'opened', project_id: 5, source_branch: 'feature' },
      { iid: 9, state: 'opened', project_id: 5, source_branch: 'ai/gl-17' },
      { iid: 10, state: 'merged', project_id: 5, source_branch: 'ai/gl-17' },
    ]);
    const rework = run({ endpoint: 'issues/17', leg: 'rework' }, { CI_JOB_STATUS: 'success', RELATED: related });
    assert.equal(rework.status, 0, rework.stderr);
    assert.deepEqual(writes(rework), [
      'CALL dev PUT issues/17 add_labels= remove_labels=agent:rework,status:In Rework',
      'CALL dev PUT merge_requests/9 add_labels= remove_labels=agent:rework,status:In Rework',
      'CALL dev PUT merge_requests/9 add_labels= remove_labels=ai_validated,ai_validating,validation_failed,ai_pr_reviewed,pr_approved',
    ]);

    const failedRework = run({ endpoint: 'merge_requests/9', leg: 'rework' },
      { CI_JOB_STATUS: 'failed', MR: '{"description":"Fixes #17\\n\\nBody","source_branch":"x"}' });
    assert.equal(failedRework.status, 0, failedRework.stderr);
    assert.deepEqual(writes(failedRework), [
      'CALL dev PUT issues/17 add_labels= remove_labels=agent:rework,status:In Rework',
      'CALL dev PUT merge_requests/9 add_labels= remove_labels=agent:rework,status:In Rework',
    ]);

    const approve = run({ endpoint: 'issues/17', leg: 'review' },
      { CI_JOB_STATUS: 'success', GITLAB_REVIEWER_TOKEN: 'reviewer', ISSUE: '{"labels":["rework-round:2","pr_approved"]}' },
      { '.dmtools/merge-request.json': '{"iid":9}', 'outputs/pr_review.json': '{"decision":"approve"}' });
    assert.equal(approve.status, 0, approve.stderr);
    assert.ok(calls(approve).every((line) => line.startsWith('CALL reviewer ')), 'review cleanup must use the reviewer token');
    assert.deepEqual(writes(approve), [
      'CALL reviewer PUT issues/17 add_labels= remove_labels=agent:review,status:In Review',
      'CALL reviewer PUT merge_requests/9 add_labels= remove_labels=agent:review,status:In Review',
      'CALL reviewer PUT merge_requests/9 add_labels=ai_pr_reviewed remove_labels=',
      'CALL reviewer PUT issues/17 add_labels= remove_labels=rework-round:2,needs-human',
      'CALL reviewer PUT merge_requests/9 add_labels= remove_labels=needs-human',
    ]);

    const changes = run({ endpoint: 'merge_requests/9', leg: 'review' },
      { CI_JOB_STATUS: 'success', MR: '{"description":"","source_branch":"ai/gl-17"}' },
      { 'outputs/pr_review.json': '{"decision":"REQUEST_CHANGES"}' });
    assert.deepEqual(writes(changes), [
      'CALL dev PUT issues/17 add_labels= remove_labels=agent:review,status:In Review',
      'CALL dev PUT merge_requests/9 add_labels= remove_labels=agent:review,status:In Review',
      'CALL dev PUT merge_requests/9 add_labels=ai_pr_reviewed remove_labels=',
    ]);

    const failedReview = run({ endpoint: 'merge_requests/9', leg: 'review' },
      { CI_JOB_STATUS: 'failed', MR: '{"description":"","source_branch":"other"}' });
    assert.deepEqual(writes(failedReview), [
      'CALL dev PUT merge_requests/9 add_labels= remove_labels=agent:review,status:In Review',
    ]);

    for (const result of [run({ endpoint: 'issues/17', leg: 'dev' }, { CI_JOB_STATUS: 'success' }), run(null)]) {
      assert.equal(result.status, 0, result.stderr);
      assert.deepEqual(writes(result), []);
      assert.match(result.stdout, /DONE/);
    }
  });

  it('syncs MR validation labels from the head pipeline during the scan', () => {
    const teammate = renderGitlabMachineSmFiles(validConfig())[1].content;
    const script = teammate.split('\nai-teammate-scan:\n')[1].split('  script:\n    - |\n')[1]
      .split('\n').map((line) => line.replace(/^      /, '')).join('\n');
    const run = (mrs: unknown[], extraEnv: Record<string, string> = {}) => spawnSync('bash', ['-c', `
      curl() {
        case " $* " in
          *"/merge_requests"*)
            case " $* " in
              *" --request PUT "*)
                printf 'PUT %s\\n' "$*" >&2
                if [ "\${FAIL_MR_PUT:-}" = true ]; then return 22; fi; printf '{}' ;;
              *" --get "*) case " $* " in *" page=1 "*) printf '%s' "$MRS" ;; *) printf '[]' ;; esac ;;
              *) url="\${@: -1}"; printf '%s' "$MRS" | jq -c --arg iid "\${url##*/}" 'first(.[] | select((.iid | tostring) == $iid))' ;;
            esac
            ;;
          *) printf '[]' ;;
        esac
      }
      ${script}
    `], {
      encoding: 'utf8',
      env: { PATH: process.env.PATH, GITLAB_TOKEN: 't', CI_API_V4_URL: 'https://gl/api/v4', CI_PROJECT_ID: '5',
        CI_DEFAULT_BRANCH: 'main', MRS: JSON.stringify(mrs), ...extraEnv },
    });
    const mr = (iid: number, status: string | null, labels: string[] = [], extra: Record<string, unknown> = {}) => ({
      iid, sha: 'head', labels, draft: false,
      head_pipeline: status ? { status, sha: 'head' } : null, ...extra,
    });
    const result = run([
      mr(1, 'success', ['ai_validating']),
      mr(2, 'failed', ['ai_validated']),
      mr(3, 'running', ['validation_failed']),
      mr(4, 'success', ['ai_validated']),
      mr(5, null, ['ai_validated']),
      mr(6, 'success', ['ai_validated'], { head_pipeline: { status: 'success', sha: 'old' } }),
      mr(7, 'success', [], { draft: true }),
      mr(8, 'pending', []),
    ]);
    assert.equal(result.status, 0, result.stderr);
    const puts = result.stderr.split('\n').filter((line) => line.startsWith('PUT '));
    const put = (iid: number) => puts.find((line) => line.endsWith(`/merge_requests/${iid}`));
    assert.match(put(1) ?? '', /add_labels=ai_validated .*remove_labels=ai_validating /);
    assert.match(put(2) ?? '', /add_labels=validation_failed .*remove_labels=ai_validated /);
    assert.match(put(3) ?? '', /add_labels=ai_validating .*remove_labels=validation_failed /);
    assert.equal(put(4), undefined, 'labels already in sync must not be rewritten');
    assert.match(put(5) ?? '', /add_labels= .*remove_labels=ai_validated /);
    assert.match(put(6) ?? '', /add_labels= .*remove_labels=ai_validated /, 'a moved head voids ai_validated');
    assert.equal(put(7), undefined, 'draft MRs are not validated');
    assert.match(put(8) ?? '', /add_labels=ai_validating /);
    assert.match(result.stdout, /Updated validation labels on 6 merge request\(s\)/);

    const failing = run([mr(1, 'success')], { FAIL_MR_PUT: 'true' });
    assert.equal(failing.status, 0, 'validation label errors must not fail the scan');
    assert.match(failing.stderr, /unable to update validation labels on MR !1/);
  });

  it('arms agent:rework once per red machine-authored MR head and escalates instead of looping', () => {
    const teammate = renderGitlabMachineSmFiles(validConfig())[1].content;
    const script = teammate.split('\nai-teammate-scan:\n')[1].split('  script:\n    - |\n')[1]
      .split('\n').map((line) => line.replace(/^      /, '')).join('\n');
    const run = (mrs: unknown[], extraEnv: Record<string, string> = {}) => spawnSync('bash', ['-c', `
      curl() {
        local method=GET url="\${@: -1}" data=""
        for arg in "$@"; do
          case "$arg" in PUT|POST) method="$arg" ;; add_labels=*|body=*) data="$data $arg" ;; esac
        done
        case "$method $url" in
          "GET https://gl/api/v4/projects/5/issues") printf '[]' ;;
          "GET https://gl/api/v4/projects/5/issues/"*)
            printf '%s' "$ISSUES" | jq -c --arg iid "\${url##*/}" 'first(.[] | select((.iid | tostring) == $iid)) // error("missing")' ;;
          "GET https://gl/api/v4/projects/5/merge_requests")
            case " $* " in *" page=1 "*) printf '%s' "$MRS" ;; *) printf '[]' ;; esac ;;
          "GET https://gl/api/v4/projects/5/merge_requests/"*/notes)
            if [ "\${FAIL_NOTES:-}" = true ]; then return 22; fi
            iid="\${url%/notes}"; printf '%s' "$NOTES" | jq -c --arg iid "\${iid##*/}" '.[$iid] // []' ;;
          "GET https://gl/api/v4/projects/5/merge_requests/"*)
            printf '%s' "$MRS" | jq -c --arg iid "\${url##*/}" 'first(.[] | select((.iid | tostring) == $iid))' ;;
          *) printf 'WRITE %s %s%s\\n' "$method" "\${url#https://gl/api/v4/projects/5/}" "$data" >&2; printf '{}' ;;
        esac
      }
      ${script}
    `], {
      encoding: 'utf8',
      env: { PATH: process.env.PATH, GITLAB_TOKEN: 't', CI_API_V4_URL: 'https://gl/api/v4', CI_PROJECT_ID: '5',
        CI_DEFAULT_BRANCH: 'main', SM_MACHINE_AUTHOR: 'ai-teammate', MRS: JSON.stringify(mrs),
        ISSUES: '[]', NOTES: '{}', ...extraEnv },
    });
    const mr = (iid: number, extra: Record<string, unknown> = {}) => ({
      iid, sha: `head${iid}`, labels: ['validation_failed'], draft: false, author: { username: 'ai-teammate' },
      description: '', source_branch: `feature-${iid}`, head_pipeline: { status: 'failed', sha: `head${iid}` }, ...extra,
    });
    const writes = (stderr: string) => stderr.split('\n').filter((line) => line.startsWith('WRITE '));

    const armed = run([
      mr(1, { description: 'Closes #11' }),
      mr(2),
      mr(3, { author: { username: 'guest' } }),
      mr(4, { labels: ['validation_failed', 'agent:rework'] }),
      mr(5, { labels: ['validation_failed', 'needs-human'] }),
      mr(6, { head_pipeline: { status: 'failed', sha: 'old' } }),
      mr(7, { head_pipeline: { status: 'success', sha: 'head7' }, labels: ['ai_validated'] }),
      mr(8, { source_branch: 'ai/gl-18' }),
      mr(9, { author: { username: 'project_bot_1', name: 'ai-dev' } }),
    ], { ISSUES: JSON.stringify([
      { iid: 11, state: 'opened', labels: ['ai_developed'] },
      { iid: 18, state: 'opened', labels: ['status:In Rework'] },
    ]) });
    assert.equal(armed.status, 0, armed.stderr);
    const armWrites = writes(armed.stderr);
    assert.ok(armWrites.includes('WRITE PUT issues/11 add_labels=agent:rework'), 'linked issue is armed for the scanner');
    assert.ok(armWrites.includes('WRITE PUT merge_requests/2 add_labels=agent:rework'), 'unlinked MR is armed for rework-on-label');
    assert.ok(armWrites.includes('WRITE PUT merge_requests/9 add_labels=agent:rework'), 'dev account matched by display name');
    assert.ok(armWrites.some((line) => /^WRITE POST merge_requests\/1\/notes body=.*dmtools:red-ci-arm head=head1 /.test(line)));
    for (const skipped of [3, 4, 5, 6, 7, 8]) {
      assert.ok(!armWrites.some((line) => line.includes(`add_labels=agent:rework`) &&
        (line.includes(`merge_requests/${skipped} `) || (skipped === 8 && line.includes('issues/18')))), `MR !${skipped} must not be armed`);
    }
    assert.match(armed.stdout, /Armed agent:rework for red CI on 3 merge request\(s\)/);

    const marker = (kind: string, head: string) => ({ body: `x <!-- dmtools:${kind} head=${head} -->` });
    const escalated = run([mr(1, { description: 'Fixes #11' }), mr(2), mr(3)], {
      ISSUES: JSON.stringify([{ iid: 11, state: 'opened', labels: [] }]),
      NOTES: JSON.stringify({
        1: [marker('red-ci-arm', 'head1')],
        2: [marker('red-ci-arm', 'head2'), marker('red-ci-withheld', 'head2')],
        3: [marker('red-ci-arm', 'older-head')],
      }),
    });
    assert.equal(escalated.status, 0, escalated.stderr);
    const escWrites = writes(escalated.stderr);
    assert.ok(escWrites.includes('WRITE PUT issues/11 add_labels=needs-human'));
    assert.ok(escWrites.includes('WRITE PUT merge_requests/1 add_labels=needs-human'));
    assert.ok(escWrites.some((line) => /^WRITE POST merge_requests\/1\/notes body=.*dmtools:red-ci-withheld head=head1 /.test(line)));
    assert.ok(!escWrites.some((line) => line.includes('merge_requests/2')), 'escalation is posted once per head');
    assert.ok(escWrites.includes('WRITE PUT merge_requests/3 add_labels=agent:rework'), 'a new head restarts the budget');
    assert.ok(!escWrites.some((line) => line.includes('agent:rework') && line.includes('/1')), 'no re-arm on an unchanged head');

    const noNotes = run([mr(2)], { FAIL_NOTES: 'true' });
    assert.equal(noNotes.status, 0, 'red-CI arm errors must not fail the scan');
    assert.ok(!writes(noNotes.stderr).some((line) => line.includes('agent:rework')), 'unknown marker history fails closed');
    assert.match(noNotes.stderr, /unable to read notes of red MR !2/);
  });

  it('creates GitLab machine SM setup without requesting parent templates', async () => {
    const calls: string[] = [];
    globalThis.fetch = (async (input) => {
      const url = String(input);
      calls.push(url);
      if (url.endsWith('/projects/team%2Fapp')) {
        return new Response(JSON.stringify({ default_branch: 'main' }), { status: 200 });
      }
      if (url.endsWith('/merge_requests')) {
        return new Response(JSON.stringify({ web_url: 'https://gitlab.example.com/team/app/-/merge_requests/1' }), { status: 201 });
      }
      if (url.includes('/repository/files/')) {
        return new Response(JSON.stringify({ message: '404 File Not Found' }), { status: 404 });
      }
      return new Response(JSON.stringify({ id: 1 }), { status: 201 });
    }) as typeof fetch;
    try {
      await createGitlabMergeRequest('token', 'team/app', 'https://gitlab.example.com', validConfig());
      assert.ok(calls.some((url) => url.endsWith('/repository/commits')));
      assert.ok(!calls.some((url) => url.startsWith('https://raw.githubusercontent.com/')));
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('preserves existing GitLab runner configuration in the setup merge request', async () => {
    const calls: { url: string; init?: RequestInit }[] = [];
    const logs: string[] = [];
    globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      calls.push({ url, init });
      if (url.endsWith('/projects/team%2Fapp')) {
        return new Response(JSON.stringify({ default_branch: 'main' }), { status: 200 });
      }
      if (url.includes('/repository/files/.dmtools%2Fconfig.js?') ||
          url.includes('/repository/files/.dmtools%2Frunners%2Ffa-bug-dev.json?')) {
        return new Response(JSON.stringify({ file_name: 'config.js' }), { status: 200 });
      }
      if (url.includes('/repository/files/')) {
        return new Response(JSON.stringify({ message: '404 File Not Found' }), { status: 404 });
      }
      if (url.endsWith('/merge_requests')) {
        return new Response(JSON.stringify({ web_url: 'https://gitlab.example.com/team/app/-/merge_requests/1' }), { status: 201 });
      }
      return new Response(JSON.stringify({ id: 1 }), { status: 201 });
    }) as typeof fetch;
    try {
      await createGitlabMergeRequest('token', 'team/app', 'https://gitlab.example.com', validConfig(), (message) => logs.push(message));
      const commit = calls.find((call) => call.url.endsWith('/repository/commits'));
      const actions = JSON.parse(String(commit?.init?.body)).actions as { file_path: string }[];
      assert.ok(!actions.some((action) => action.file_path === '.dmtools/config.js'));
      assert.ok(!actions.some((action) => action.file_path === '.dmtools/runners/fa-bug-dev.json'));
      assert.ok(logs.some((message) => message.includes('preserved existing runner configuration files')));
      const mergeRequest = calls.find((call) => call.url.endsWith('/merge_requests'));
      assert.match(JSON.parse(String(mergeRequest?.init?.body)).description, /Existing runner configuration files were preserved/);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });
});

describe('GitLab factory templates', () => {
  const multiConfig = () => validConfig({
    gitlabMultipleTeammates: true,
    gitlabDev: 'custom-dev',
    gitlabReviewer: 'custom-reviewer',
  });

  it('requires distinct valid accounts only in GitLab multiple-teammate mode', () => {
    const config = multiConfig();
    assert.deepEqual(validate(config, 'gitlab'), {});
    assert.equal(validate({ ...config, machine: '' }, 'gitlab').machine, undefined);
    for (const key of ['gitlabDev', 'gitlabReviewer'] as const) {
      for (const value of ['', 'bad username', "bad'username"]) {
        assert.ok(validate({ ...config, [key]: value }, 'gitlab')[key]);
        assert.throws(() => renderGitlabFiles({ ...config, [key]: value }, gitlabTemplates), /username/);
      }
    }
    assert.match(validate({ ...config, gitlabReviewer: 'CUSTOM-DEV' }, 'gitlab').gitlabReviewer, /different/);
    assert.deepEqual(validate({ ...config, gitlabDev: '', gitlabReviewer: '' }, 'github'), {});
  });

  it('lists separate GitLab token fields with the selected account names', () => {
    const secrets = requiredSecrets(multiConfig(), 'gitlab');
    assert.deepEqual(secrets.slice(0, 2).map((secret) => secret.name), ['GITLAB_DEV_TOKEN', 'GITLAB_REVIEWER_TOKEN']);
    assert.match(secrets[0].hint, /custom-dev/);
    assert.match(secrets[1].hint, /custom-reviewer/);
    assert.ok(!secrets.some((secret) => secret.name === 'GITLAB_TOKEN'));
    assert.equal(secrets[2].name, 'SOURCE_GITHUB_TOKEN');
    assert.match(secrets[2].hint, /release repositories/);
    assert.equal(requiredSecrets({ ...multiConfig(), gitlabMultipleTeammates: false }, 'gitlab')[0].name, 'GITLAB_TOKEN');
    assert.ok(requiredSecrets({ ...multiConfig(), gitlabMultipleTeammates: false }, 'gitlab')
      .some((secret) => secret.name === 'SOURCE_GITHUB_TOKEN'));
    assert.equal(requiredSecrets(multiConfig(), 'github')[0].name, 'SOURCE_GITHUB_TOKEN');
  });

  it('routes GitLab jobs, poller work and manual reviews to their account tokens', () => {
    const files = renderGitlabFiles(multiConfig(), gitlabTemplates);
    const pipeline = files.find((file) => file.path === '.gitlab-ci.yml')!.content;
    for (const job of ['poll-assigned-issues', 'ai-teammate', 'review-merge-request', 'rework-merge-request', 'merge-approved-merge-request']) {
      const token = job === 'review-merge-request' || job === 'merge-approved-merge-request' ? 'GITLAB_REVIEWER_TOKEN' : 'GITLAB_DEV_TOKEN';
      assert.ok(pipeline.includes(`${job}:\n  variables:\n    GITLAB_TOKEN: "$${token}"`));
    }
    assert.match(pipeline, /select_gitlab_teammate "\$DMTOOLS_AGENT_CONFIG"/);
    assert.match(pipeline, /author" != 'custom-dev'/);
    const poller = files.find((file) => file.path.endsWith('gitlab-factory-poll-issues.sh'))!.content;
    assert.match(poller, /machine='custom-dev'/);
    assert.equal(poller.match(/select_gitlab_teammate "\$config"/g)?.length, 2);
    assert.match(poller, /select_gitlab_teammate merge/);
    const selector = files.find((file) => file.path.endsWith('gitlab-factory-select-teammate.sh'))!.content;
    for (const role of ['dev', 'rework-merge-request', '.dmtools/runners/fa-story-dev.json', '.dmtools/runners/fa-rework.json',
      'review-merge-request', '.dmtools/runners/fa-review.json', 'merge', 'merge-approved-merge-request']) {
      const result = spawnSync('bash', ['-c', `${selector}\nselect_gitlab_teammate "$1"\nprintf '%s' "$GITLAB_TOKEN"`, '--', role], {
        encoding: 'utf8',
        env: { GITLAB_DEV_TOKEN: 'dev-test-token', GITLAB_REVIEWER_TOKEN: 'review-test-token' },
      });
      assert.equal(result.status, 0, result.stderr);
      assert.equal(result.stdout, /review|merge-approved|^merge$/.test(role) ? 'review-test-token' : 'dev-test-token');
    }
    for (const role of ['dev', 'merge']) {
      const result = spawnSync('bash', ['-c', `${selector}\nselect_gitlab_teammate "$1"`, '--', role], { encoding: 'utf8', env: {} });
      assert.notEqual(result.status, 0);
      assert.match(result.stderr, /Set the GITLAB_(DEV|REVIEWER)_TOKEN/);
    }
    for (const file of files.filter((file) => file.path.endsWith('.sh'))) {
      const result = spawnSync('bash', ['-n'], { input: file.content, encoding: 'utf8' });
      assert.equal(result.status, 0, result.stderr);
    }
    assert.ok(!renderGitlabFiles(validConfig(), gitlabTemplates).some((file) => file.path.endsWith('gitlab-factory-select-teammate.sh')));
    assert.deepEqual(renderFiles(multiConfig()), renderFiles(validConfig()));
  });

  it('fails explicitly when parent templates cannot route multiple teammates safely', () => {
    assert.throws(() => renderGitlabFiles(multiConfig(), { ...gitlabTemplates, 'gitlab-ci.yml': 'changed parent' }),
      /incompatible with factory setup/);
  });

  const template = (name: string) =>
    readFileSync(resolve(here, 'fixtures/gitlab', name), 'utf8');
  const withReworkJobs = (pipeline: string) => pipeline
    .replace('dmtools run .dmtools/runners/fa-rework.json --inputTicketId "$CI_MERGE_REQUEST_IID"',
      'bash .dmtools/scripts/gitlab-factory-rework.sh "$CI_MERGE_REQUEST_IID"')
    .replace('      dmtools run "$DMTOOLS_AGENT_CONFIG" --inputTicketId "$DMTOOLS_TICKET_ID"',
      `      if [ "$DMTOOLS_AGENT_CONFIG" = '.dmtools/runners/fa-rework.json' ]; then
        bash .dmtools/scripts/gitlab-factory-rework.sh "$DMTOOLS_TICKET_ID"
      else
        dmtools run "$DMTOOLS_AGENT_CONFIG" --inputTicketId "$DMTOOLS_TICKET_ID"
      fi`);

  const qualityCases = [
    { preset: 'crap4js', toolchain: 'node', version: '24', fallback: '22' },
    { preset: 'crap4dart', toolchain: 'flutter', version: '3.32', fallback: 'stable' },
    { preset: 'crap4java', toolchain: 'java', version: '17', fallback: '21' },
    { preset: 'crap4py', toolchain: 'python', version: '3.13', fallback: '3.12' },
    { preset: 'crap4go', toolchain: 'go', version: '1.24', fallback: '1.23' },
  ] as const;

  for (const { preset, toolchain, version, fallback } of qualityCases) {
    for (const customVersion of [false, true]) {
      it(`renders ${preset} from its template with ${customVersion ? 'custom' : 'default'} version`, () => {
        const config = validConfig({
          machine: 'factory-bot',
          baseBranch: 'release/test',
          qualityPreset: preset,
          toolchains: customVersion ? { [toolchain]: version } : {},
        });
        const files = renderGitlabFiles(config, gitlabTemplates);
        const quality = template(`gitlab-quality-${preset}.yml`)
          .replaceAll('@@VERSION@@', customVersion ? version : fallback);
        const pipeline = files.find((file) => file.path === '.gitlab-ci.yml')!.content;
        assert.equal(pipeline, withReworkJobs(template('gitlab-ci.yml')
          .replaceAll('@@QUALITY_JOB@@', quality)
          .replaceAll('@@MACHINE@@', config.machine)));
        assert.doesNotMatch(pipeline, /@@[A-Z_]+@@/);

        const script = quality.split('    - |\n')[1]
          .split('\n').map((line) => line.replace(/^      /, '')).join('\n');
        const syntax = spawnSync('bash', ['-n'], { input: script, encoding: 'utf8' });
        assert.equal(syntax.status, 0, syntax.stderr);
      });
    }
  }

  for (const config of [
    validConfig({ qualityPreset: 'none' }),
    validConfig({ ciEnabled: false, qualityPreset: 'crap4js' }),
  ]) {
    it(`omits quality stage and job when ${config.ciEnabled === false ? 'CI is disabled' : 'no preset is selected'}`, () => {
      const pipeline = renderGitlabFiles(config, gitlabTemplates).find((file) => file.path === '.gitlab-ci.yml')!.content;
      assert.equal(pipeline, withReworkJobs(template('gitlab-ci.yml')
        .replace('  - quality\n', '')
        .replaceAll('@@QUALITY_JOB@@', '')
        .replaceAll('@@MACHINE@@', config.machine)));
      assert.doesNotMatch(pipeline, /@@[A-Z_]+@@/);
    });
  }

  it('renders shell templates with configuration and preserves valid Bash syntax', () => {
    const config = validConfig({ machine: 'factory-bot', baseBranch: 'release/test' });
    const files = renderGitlabFiles(config, gitlabTemplates);
    for (const name of ['gitlab-factory-poll-issues.sh', 'gitlab-factory-publish-review.sh']) {
      const script = files.find((file) => file.path === `.dmtools/scripts/${name}`)!.content;
      assert.ok(script.startsWith(template(name)
        .split('\npage=1\n')[0]
        .replaceAll('@@MACHINE@@', config.machine)
        .replaceAll('@@BASE_BRANCH@@', config.baseBranch)));
      assert.doesNotMatch(script, /@@[A-Z_]+@@/);
      const syntax = spawnSync('bash', ['-n'], { input: script, encoding: 'utf8' });
      assert.equal(syntax.status, 0, syntax.stderr);
    }
  });
});

function validConfig(overrides: Partial<FactoryConfig> = {}): FactoryConfig {
  return {
    ...defaults,
    gitlabAutoLifecycle: false,
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

  it('does not require GitHub CI settings for the GitLab machine SM workflow', () => {
    const config = validConfig({ ciFile: '', ciName: '', checks: [] });
    assert.deepEqual(validate(config, 'gitlab'), {});
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
    const files = renderGitlabFiles(cfg, gitlabTemplates, 'https://gitlab.example.com/gitlab');
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
    assert.equal(JSON.parse(story.content).params.customParams.scmProvider, undefined,
      'lifecycle scripts create MRs themselves; the pack must not create a second one');
    const review = files.find((file) => file.path === '.dmtools/runners/fa-review.json');
    assert.ok(review);
    assert.equal(JSON.parse(review.content).params.requireCliOutputFile, false);
    assert.equal(JSON.parse(review.content).params.cleanupOutputsFolder, false);
    const publisher = files.find((file) => file.path === '.dmtools/scripts/gitlab-factory-publish-review.sh');
    assert.ok(publisher, 'GitLab setup should generate the review publisher');
    assert.match(publisher.content, /merge_requests\/\$mr_iid\/notes/);
    assert.match(publisher.content, /merge_requests\/\$mr_iid\/discussions/);
    assert.match(publisher.content, /diff_refs/);
    assert.match(publisher.content, /inlineComments/);
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
      /review-merge-request:[\s\S]*?script:\n    - \|[\s\S]*?dmtools run \.dmtools\/runners\/fa-review\.json/,
    );
    assert.match(
      pipeline,
      /dmtools run \.dmtools\/runners\/fa-review\.json --inputTicketId "\$CI_MERGE_REQUEST_IID"\s+bash \.dmtools\/scripts\/gitlab-factory-publish-review\.sh "\$CI_MERGE_REQUEST_IID"/,
    );
    assert.match(
      pipeline,
      /rework-merge-request:[\s\S]*?script:\n    - \|\n      bash \.dmtools\/scripts\/gitlab-factory-rework\.sh "\$CI_MERGE_REQUEST_IID"/,
    );
    assert.match(pipeline, /CI_PIPELINE_SOURCE == "merge_request_event"/);
    assert.match(pipeline, /CI_PIPELINE_SOURCE == "trigger" && \$DMTOOLS_FACTORY_TRIGGER == "issue_webhook"/);
    assert.match(pipeline, /poll-assigned-issues:[\s\S]*?rules:\n    - if: '\$CI_PIPELINE_SOURCE == "trigger"/);
    assert.doesNotMatch(pipeline, /CI_PIPELINE_SOURCE == "schedule"/);
    const qualityFiles = renderGitlabFiles(validConfig({ qualityPreset: 'crap4js' }), gitlabTemplates);
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
    assert.match(poller, /if \[ "\$trigger" != 'agent:review' \]; then\s+base_sha=\$\(git rev-parse HEAD\)\s+fi/);
    assert.match(poller, /if \[ "\$trigger" = 'agent:review' \]; then\s+clear_review_outputs\s+fi/);
    assert.match(poller, /run_agent "\$config" --inputTicketId "\$iid"/);
    assert.match(
      poller,
      /elif \[ "\$trigger" != 'agent:review' \]; then\s+run_agent "\$config" --inputTicketId "\$iid"\s+publish_issue_changes "\$iid" "\$base_sha"\s+else\s+run_agent "\$config" --inputTicketId "\$iid"\s+related_merge_requests=/,
      'only development should create a branch/MR; review and rework use existing MRs',
    );
    assert.match(poller, /if \[ "\$trigger" = 'agent:review' \]; then\s+result_label='ai_pr_reviewed'/);
    assert.match(poller, /issues\/\$iid\/related_merge_requests/);
    assert.match(poller, /bash \.dmtools\/scripts\/gitlab-factory-publish-review\.sh "\$mr_iid"/);
    assert.match(poller, /leaving issue\/MR labels unchanged/);
    assert.match(poller, /git switch -c "\$branch"/);
    assert.match(poller, /git -c credential\.helper= -c credential\.helper=.* push --porcelain/);
    assert.match(poller, /\[gitlab-api\] request failed method=\$method url=\$url curl_exit=\$curl_status http_status=\$http_status/);
    assert.match(poller, /\[gitlab-api\] request failed method=\$method url=\$url http_status=\$http_status/);
    assert.match(poller, /error response \(max 2000 chars\)/);
    assert.match(poller, /message\/\/"\$GITLAB_TOKEN"\/\[REDACTED\]/);
    assert.match(poller, /GET "\$CI_API_V4_URL\/user"/);
    assert.match(poller, /git_remote_url="https:\/\/\$git_username@\$\{remote_url#https:\/\/\}"/);
    assert.match(poller, /credential_helper=\$\(mktemp\)/);
    assert.match(poller, /printf 'username=%s\\npassword=%s\\n\\n'/);
    assert.match(poller, /push --porcelain/);
    assert.doesNotMatch(poller, /GIT_TRACE_CURL|GIT_TRACE=|log_git_trace/);
    assert.match(poller, /Pushed branch \$branch to GitLab/);
    assert.match(poller, /\[poller-error\] line=%s exit=%s issue=%s pipeline=%s/);
    assert.match(poller, /branch protection/);
    assert.match(poller, /projects\/\$CI_PROJECT_ID\/merge_requests/);
    assert.match(poller, /Created GitLab merge request:/);
    const lifecycleFiles = renderGitlabFiles(validConfig({ gitlabAutoLifecycle: true }), gitlabTemplates);
    const reviewRunner = JSON.parse(lifecycleFiles.find((file) => file.path === '.dmtools/runners/fa-review.json')!.content);
    const reviewPrompt = reviewRunner.params.cliPrompts.join('\n');
    assert.match(reviewPrompt, /Write all review artifacts only under its outputs\/ directory/);
    assert.match(reviewPrompt, /Treat DMTOOLS_REVIEW_CONTEXT as read-only input/);
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

  it('ignores a non-empty-directory cleanup failure only after successful agent completion', () => {
    const poller = renderGitlabFiles(validConfig({ gitlabAutoLifecycle: true }), gitlabTemplates)
      .find((file) => file.path === '.dmtools/scripts/gitlab-factory-poll-issues.sh')!.content;
    const runAgent = poller.match(/run_agent\(\) \{[\s\S]*?\n\}/)?.[0];
    assert.ok(runAgent, 'rendered poller should contain run_agent');

    const runWithOutput = (completion: string) => [
      'set -Eeuo pipefail',
      runAgent,
      `dmtools() { printf '%s\\n' '${completion}' "rmdir: failed to remove '/tmp/context': Directory not empty"; return 1; }`,
      'run_agent review.json',
    ].join('\n');
    const recovered = spawnSync('bash', ['-c', runWithOutput('Agent completed with exit code: 0')], { encoding: 'utf8' });
    assert.equal(recovered.status, 0, recovered.stderr);
    assert.match(recovered.stderr, /Ignoring temporary-directory cleanup failure/);

    const failedAgent = spawnSync('bash', ['-c', runWithOutput('Agent completed with exit code: 1')], { encoding: 'utf8' });
    assert.notEqual(failedAgent.status, 0);
  });

  it('replaces a truncated trigger token instead of configuring a webhook that returns 404', async () => {
    const origFetch = globalThis.fetch;
    const calls: { url: string; method: string; body?: Record<string, unknown> }[] = [];
    globalThis.fetch = (async (input, init) => {
      const url = String(input);
      const method = init?.method ?? 'GET';
      const body = init?.body instanceof URLSearchParams
        ? Object.fromEntries(init.body)
        : init?.body ? JSON.parse(String(init.body)) : undefined;
      calls.push({ url, method, body });
      if (url.includes('/triggers?')) {
        return Response.json([{ id: 21, token: 'abcd', description: 'DMTools factory issue webhook' }]);
      }
      if (url.endsWith('/triggers') && method === 'POST') {
        return Response.json({ id: 22, token: 'full-trigger-token', description: 'DMTools factory issue webhook' });
      }
      if (url.includes('/hooks?')) {
        return Response.json([{
          id: 34,
          url: 'https://gitlab.example.com/api/v4/projects/team%2Fapp/trigger/pipeline?token=abcd&ref=main&variables%5BDMTOOLS_FACTORY_TRIGGER%5D=issue_webhook',
        }]);
      }
      if (url.endsWith('/hooks/34') && method === 'PUT') return Response.json({ id: 34 });
      if (url.endsWith('/triggers/21') && method === 'DELETE') return new Response(null, { status: 204 });
      throw new Error(`Unexpected request: ${method} ${url}`);
    }) as typeof fetch;
    try {
      await ensureGitlabIssueEventWebhook('setup-token', 'team/app', 'https://gitlab.example.com', 'main');
      assert.equal(calls.filter((call) => call.url.endsWith('/triggers') && call.method === 'POST').length, 1);
      const updated = calls.find((call) => call.url.endsWith('/hooks/34') && call.method === 'PUT');
      assert.equal(new URL(String(updated?.body?.url)).searchParams.get('token'), 'full-trigger-token');
      assert.ok(calls.some((call) => call.url.endsWith('/triggers/21') && call.method === 'DELETE'));

      calls.length = 0;
      globalThis.fetch = (async (input) => {
        const url = String(input);
        calls.push({ url, method: 'GET' });
        return url.includes('/triggers?')
          ? Response.json([])
          : Response.json({ id: 22, token: 'abcd', description: 'DMTools factory issue webhook' });
      }) as typeof fetch;
      await assert.rejects(
        ensureGitlabIssueEventWebhook('setup-token', 'team/app', 'https://gitlab.example.com', 'main'),
        /did not return the factory webhook trigger token/,
      );
      assert.ok(calls.every((call) => !call.url.includes('/hooks')));
    } finally {
      globalThis.fetch = origFetch;
    }
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
      assert.equal(calls.length, 5);
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

  it('commits the machine SM, teammate, and runner setup without fetching templates', async () => {
      const origFetch = globalThis.fetch;
      const calls: { url: string; init?: RequestInit }[] = [];
      globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        calls.push({ url, init });
        if (url.endsWith('/projects/team%2Fapp')) {
          return new Response(JSON.stringify({ default_branch: 'main' }), { status: 200 });
        }
        if (url.endsWith('/merge_requests')) {
          return new Response(JSON.stringify({ web_url: 'https://gitlab.example.com/team/app/-/merge_requests/1' }), { status: 201 });
        }
        if (url.includes('/repository/files/')) {
          return new Response(JSON.stringify({ message: '404 File Not Found' }), { status: 404 });
        }
        return new Response(JSON.stringify({ id: 1 }), { status: 201 });
      }) as typeof fetch;

      try {
        const mrUrl = await createGitlabMergeRequest(
          'glpat_test-token-123456789',
          'team/app',
          'https://gitlab.example.com',
          validConfig({ repo: 'team/app', baseBranch: 'main' }),
          () => {},
        );
        assert.equal(mrUrl, 'https://gitlab.example.com/team/app/-/merge_requests/1');
        const commit = calls.find((call) => call.url.endsWith('/repository/commits'));
        const actions = JSON.parse(String(commit?.init?.body)).actions as { file_path: string; content: string }[];
        assert.deepEqual(
          actions.map((file) => file.file_path),
          renderGitlabMachineSmFiles(validConfig({ repo: 'team/app', baseBranch: 'main' }), 'https://gitlab.example.com')
            .map((file) => file.path),
        );
        assert.deepEqual(
          actions.map(({ file_path, content }) => ({ path: file_path, content })),
          renderGitlabMachineSmFiles(validConfig({ repo: 'team/app', baseBranch: 'main' }), 'https://gitlab.example.com')
            .map(({ path, content }) => ({ path, content })),
        );
        const storyRunner = JSON.parse(
          actions.find((file) => file.file_path === '.dmtools/runners/fa-story-dev.json')!.content,
        );
        assert.equal(storyRunner.params.envVariables.GITLAB_BASE_PATH, 'https://gitlab.example.com');
        const mergeRequest = calls.find((call) => call.url.endsWith('/merge_requests'));
        assert.ok(mergeRequest);
        assert.match(JSON.parse(String(mergeRequest.init?.body)).description, /shared parent workflows/);
        assert.equal(calls.filter((call) => call.url.startsWith('https://raw.githubusercontent.com/')).length, 0);
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
      if (new URL(url).searchParams.get('page') === '1') {
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

describe('Issue #783 — CI & Quality Gate step in FactorySetup UI', () => {
  it('renders Step 4 CI & Quality Gate section with crap4* presets, CI workflow file/name, and required checks inputs', () => {
    const setupTsx = readFileSync(resolve(here, '../src/components/FactorySetup.tsx'), 'utf8');
    assert.match(setupTsx, /href="#step-ci"/);
    assert.match(setupTsx, /<section id="step-ci" className="factory__card">/);
    assert.match(setupTsx, /CI &amp; Quality Gate/);
    assert.match(setupTsx, /\['crap4',\s*'Generate crap4\* quality gate'\]/);
    assert.match(setupTsx, /id="f-quality-preset"/);
    assert.match(setupTsx, /QUALITY_PRESETS\.map/);
    assert.match(setupTsx, /id="f-checks"/);
    assert.match(setupTsx, /Checks \(comma-separated\)/);
    assert.match(setupTsx, /\['ciFile',\s*'CI workflow file'/);
    assert.match(setupTsx, /\['ciName',\s*'CI workflow name'/);
  });
});
