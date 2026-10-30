import gitlabIssuePoller from './factory-templates/gitlab/gitlab-factory-poll-issues.sh?raw';
import gitlabReviewPublisher from './factory-templates/gitlab/gitlab-factory-publish-review.sh?raw';
import gitlabQualityJs from './factory-templates/gitlab/gitlab-quality-crap4js.yml?raw';
import gitlabQualityDart from './factory-templates/gitlab/gitlab-quality-crap4dart.yml?raw';
import gitlabQualityJava from './factory-templates/gitlab/gitlab-quality-crap4java.yml?raw';
import gitlabQualityPy from './factory-templates/gitlab/gitlab-quality-crap4py.yml?raw';
import gitlabQualityGo from './factory-templates/gitlab/gitlab-quality-crap4go.yml?raw';
import gitlabCi from './factory-templates/gitlab/gitlab-ci.yml?raw';
import aiTeammate from './factory-templates/github/ai-teammate.yml?raw';
import machineSm from './factory-templates/github/machine-sm.yml?raw';
import machineMerge from './factory-templates/github/machine-merge.yml?raw';
import mergeTrigger from './factory-templates/github/merge-trigger.yml?raw';
import smKicker from './factory-templates/github/sm-kicker.yml?raw';
import faUniversal from './factory-templates/github/fa-universal.yml?raw';
import dmtoolsConfig from './factory-templates/shared/config.js?raw';
import runnerBug from './factory-templates/shared/runner-bug.json?raw';
import runnerStory from './factory-templates/shared/runner-story.json?raw';
import runnerRework from './factory-templates/shared/runner-rework.json?raw';
import runnerReview from './factory-templates/shared/runner-review.json?raw';

export type QualityPresetId = 'none' | 'crap4js' | 'crap4dart' | 'crap4java' | 'crap4py' | 'crap4go';

export interface QualityPreset {
  id: Exclude<QualityPresetId, 'none'>;
  label: string;
  repoUrl: string;
  toolchain?: ToolchainId;
  description: string;
}

export const QUALITY_PRESETS: QualityPreset[] = [
  { id: 'crap4js', label: 'JavaScript / TypeScript (crap4js)', repoUrl: 'https://github.com/IstiN/crap4js', toolchain: 'node', description: 'Node.js test coverage (c8) + IstiN/crap4js CRAP score gate (≤ 8.0)' },
  { id: 'crap4dart', label: 'Dart / Flutter (crap4dart)', repoUrl: 'https://github.com/IstiN/crap4dart', toolchain: 'flutter', description: 'dart format + analyze + test coverage + IstiN/crap4dart CRAP gate' },
  { id: 'crap4java', label: 'Java (crap4java)', repoUrl: 'https://github.com/IstiN/crap4java', toolchain: 'java', description: 'JaCoCo coverage + IstiN/crap4java CRAP score gate (≤ 8.0)' },
  { id: 'crap4py', label: 'Python (crap4py)', repoUrl: 'https://github.com/vabhzw17eg2qu4m9-bit/crap4py', toolchain: 'python', description: 'coverage.py + vabhzw17eg2qu4m9-bit/crap4py CRAP score gate (≤ 8.0)' },
  { id: 'crap4go', label: 'Go (crap4go)', repoUrl: 'https://github.com/vabhzw17eg2qu4m9-bit/crap4go', toolchain: 'go', description: 'gofmt + go vet + go test coverprofile + vabhzw17eg2qu4m9-bit/crap4go CRAP gate' },
];

export interface FactoryLabel {
  name: string;
  color: string;
  description: string;
}

export const FACTORY_LABELS: FactoryLabel[] = [
  { name: 'agent:dev', color: '1d76db', description: 'Trigger AI Teammate development leg' },
  { name: 'agent:review', color: '0e8a16', description: 'Trigger AI Teammate PR review leg' },
  { name: 'agent:rework', color: 'd93f0b', description: 'Trigger AI Teammate rework leg' },
  { name: 'agent:skip', color: 'e4e669', description: 'Skip AI Teammate processing' },
  { name: 'ai_developed', color: '5319e7', description: 'Development leg completed and PR opened' },
  { name: 'ai_validating', color: 'fbca04', description: 'Validation CI in progress on PR head' },
  { name: 'ai_validated', color: '0e8a16', description: 'Validation CI passed on PR head' },
  { name: 'ai_pr_reviewed', color: '0052cc', description: 'AI review completed on PR' },
  { name: 'pr_approved', color: '2cbe4e', description: 'PR approved by review leg and armed for merge' },
  { name: 'validation_failed', color: 'b60205', description: 'Validation CI failed on PR head' },
  { name: 'blocked', color: 'd93f0b', description: 'Freeze switch — machine skips this item until removed' },
  { name: 'needs-human', color: 'b60205', description: 'Escalated to human operator' },
];

export interface FactoryConfig {
  repo: string; // owner/name
  createRepo?: boolean; // Greenfield: create the repository before opening the factory PR
  repoPrivate?: boolean; // Greenfield repository visibility (default true = private)
  createLabels?: boolean; // default true: automatically create required factory labels
  machine: string; // GitHub login of the AI teammate
  ciEnabled?: boolean; // default true; false skips CI / quality gate
  qualityPreset?: QualityPresetId; // default 'none'; when set to crap4*, generates .github/workflows/quality.yml
  ciFile: string; // e.g. unit-tests.yml
  ciName: string; // workflow display name, e.g. "Unit Tests"
  checks: string[]; // contexts the SM waits for (validation-checks / required_contexts)
  baseBranch: string; // branch the agents open PRs against
  runner: string; // runs-on label for the factory legs
  toolchains: Partial<Record<ToolchainId, string>>; // selected toolchain -> version
  providers: ProvidersConfig;
  merge: MergeIdentity;
  faUniversal?: boolean; // default true: generate .github/workflows/fa-universal.yml (@fa interactive agent)
  faMemory?: boolean; // default true: generate .fah/config.yaml & persist repo memory under .fah/memory
}

/** Who merges machine PRs: the factory PAT, a separate PAT, or a GitHub App (job-scoped token). */
export interface MergeIdentity {
  mode: 'factory' | 'token' | 'app';
  tokenSecret: string; // mode=token: repository secret holding the merge PAT
}

export interface ProviderPreset {
  id: string;
  label: string;
  kind: string; // fa adapter kind (provider_type)
  baseUrl: string;
  keyEnv: string; // env var NAME that holds the API key
  models: string[];
  keyHelp: string; // where to get the key
  keyUrl?: string;
}

/** Mirrors flutter_agent lib/src/model_roles/provider_catalog.dart. */
export const PROVIDER_PRESETS: ProviderPreset[] = [
  { id: 'copilot', label: 'GitHub Copilot', kind: 'copilot', baseUrl: 'https://api.githubcopilot.com', keyEnv: 'COPILOT_GITHUB_TOKEN', models: ['claude-sonnet-5', 'gpt-5.5', 'gpt-5.3-codex', 'claude-opus-5', 'gpt-5.4-mini', 'gemini-3.8-flash', 'gpt-5-mini'], keyHelp: 'Fine-grained PAT of an account with an active Copilot plan: resource owner = your user account, Account permissions → Copilot Requests: Read. Must be a PAT (not an OAuth/App token).', keyUrl: 'https://github.com/settings/personal-access-tokens/new?name=COPILOT_GITHUB_TOKEN&description=DMTools%20factory%20-%20Copilot%20inference&user_copilot_requests=read' },
  { id: 'zai', label: 'z.ai (GLM)', kind: 'zai', baseUrl: 'https://api.z.ai/api/coding/paas/v4', keyEnv: 'ZAI_API_KEY', models: ['glm-5.3-flash', 'glm-4.6'], keyHelp: 'z.ai console → API Keys (GLM Coding Plan key).', keyUrl: 'https://z.ai/manage-apikey/apikey-list' },
  { id: 'kimi', label: 'Kimi', kind: 'openai-completions', baseUrl: 'https://api.kimi.com/coding/v1', keyEnv: 'KIMI_API_KEY', models: ['kimi-for-coding'], keyHelp: 'Kimi Code console → API Keys.', keyUrl: 'https://www.kimi.com/coding' },
  { id: 'openai', label: 'OpenAI', kind: 'openai-completions', baseUrl: 'https://api.openai.com/v1', keyEnv: 'OPENAI_API_KEY', models: ['gpt-5.5', 'gpt-5.4-mini', 'gpt-5.3-codex'], keyHelp: 'OpenAI platform → API keys.', keyUrl: 'https://platform.openai.com/api-keys' },
  { id: 'anthropic', label: 'Anthropic', kind: 'anthropic', baseUrl: 'https://api.anthropic.com', keyEnv: 'ANTHROPIC_API_KEY', models: ['claude-sonnet-5', 'claude-opus-5', 'claude-haiku-4-5'], keyHelp: 'Anthropic console → Settings → API keys.', keyUrl: 'https://console.anthropic.com/settings/keys' },
  { id: 'google', label: 'Google Gemini', kind: 'google', baseUrl: 'https://generativelanguage.googleapis.com/v1beta', keyEnv: 'GOOGLE_API_KEY', models: ['gemini-3.8-flash', 'gemini-4-argon'], keyHelp: 'Google AI Studio → Get API key.', keyUrl: 'https://aistudio.google.com/app/apikey' },
  { id: 'openrouter', label: 'OpenRouter', kind: 'openai-completions', baseUrl: 'https://openrouter.ai/api/v1', keyEnv: 'OPENROUTER_API_KEY', models: ['openai/gpt-4.1-mini', 'openai/gpt-4o-mini'], keyHelp: 'OpenRouter → Settings → Keys.', keyUrl: 'https://openrouter.ai/settings/keys' },
  { id: 'codemie', label: 'EPAM CodeMie', kind: 'openai-completions', baseUrl: 'https://codemie.lab.epam.com/code-assistant-api/v1', keyEnv: 'CODEMIE_API_KEY', models: [], keyHelp: 'EPAM CodeMie → Profile → API keys (EPAM network).', keyUrl: 'https://codemie.lab.epam.com' },
  { id: 'dial', label: 'EPAM DIAL', kind: 'dial', baseUrl: 'https://ai-proxy.lab.epam.com', keyEnv: 'DIAL_API_KEY', models: [], keyHelp: 'EPAM DIAL key — request it via the DIAL portal (EPAM network).', keyUrl: 'https://ai-proxy.lab.epam.com' },
  { id: 'minimax', label: 'MiniMax', kind: 'minimax', baseUrl: 'https://api.minimax.io/v1', keyEnv: 'MINIMAX_API_KEY', models: [], keyHelp: 'MiniMax platform → API Keys.', keyUrl: 'https://platform.minimax.io' },
  { id: 'custom', label: 'Custom (OpenAI-compatible)', kind: 'openai-completions', baseUrl: '', keyEnv: 'CUSTOM_API_KEY', models: [], keyHelp: 'The API key of your OpenAI-compatible endpoint.' },
];

export interface ProviderEntry {
  preset: string;
  model: string;
  baseUrl: string;
  keyEnv: string;
}

/** `default` = one provider; `queue` = ordered failover (FA_PROVIDERS_QUEUE). */
export interface ProvidersConfig {
  mode: 'default' | 'queue';
  entries: ProviderEntry[];
  reviewSeparate: boolean; // review leg uses its own list
  reviewEntries: ProviderEntry[];
}

export function newEntry(presetId = 'copilot'): ProviderEntry {
  const p = PROVIDER_PRESETS.find((x) => x.id === presetId)!;
  return { preset: p.id, model: p.models[0] ?? '', baseUrl: p.baseUrl, keyEnv: p.keyEnv };
}

export type ToolchainId = 'java' | 'node' | 'python' | 'flutter' | 'go';

export interface Toolchain {
  id: ToolchainId;
  label: string;
  input: string; // factory-teammate.yml input name
  versions: string[];
  fallback: string;
}

export const TOOLCHAINS: Toolchain[] = [
  { id: 'java', label: 'Java (Temurin)', input: 'java-version', versions: ['25', '21', '17'], fallback: '21' },
  { id: 'node', label: 'Node.js', input: 'node-version', versions: ['24', '22', '20'], fallback: '22' },
  { id: 'python', label: 'Python', input: 'python-version', versions: ['3.13', '3.12', '3.11'], fallback: '3.12' },
  { id: 'flutter', label: 'Flutter', input: 'flutter-version', versions: ['stable', 'beta'], fallback: 'stable' },
  { id: 'go', label: 'Go', input: 'go-version', versions: ['1.24', '1.23', '1.22'], fallback: '1.23' },
];

export const RUNNERS = ['ubuntu-latest', 'ubuntu-24.04', 'ubuntu-22.04', 'ubuntu-slim', 'macos-latest', 'windows-latest'];

export interface ScmOption { id: 'github' | 'gitlab'; label: string; available: boolean }
export type ScmId = ScmOption['id'];
export const SCM: ScmOption[] = [
  { id: 'github', label: 'GitHub', available: true },
  { id: 'gitlab', label: 'GitLab', available: true },
];

export interface FactoryFile {
  path: string;
  content: string;
}

export const defaults: FactoryConfig = {
  repo: '',
  createRepo: false,
  repoPrivate: true,
  createLabels: true,
  machine: 'ai-teammate',
  ciEnabled: true,
  qualityPreset: 'none',
  ciFile: 'ci.yml',
  ciName: 'CI',
  checks: ['test'],
  baseBranch: 'main',
  runner: 'ubuntu-latest',
  toolchains: {},
  merge: { mode: 'factory', tokenSecret: 'MERGE_GITHUB_TOKEN' },
  providers: { mode: 'default', entries: [newEntry('copilot')], reviewSeparate: false, reviewEntries: [newEntry('copilot')] },
  faUniversal: true,
  faMemory: true,
};

const LOGIN = /^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$/;
const GITLAB_LOGIN = /^[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}$/;
const REPO = /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/;
const GITLAB_REPO = /^[A-Za-z0-9_.-]+(?:\/[A-Za-z0-9_.-]+)+$/;

export function isCiEnabled(c: FactoryConfig): boolean {
  return c.ciEnabled !== false;
}

export function isFaUniversalEnabled(c: FactoryConfig): boolean {
  return c.faUniversal !== false;
}

export function isFaMemoryEnabled(c: FactoryConfig): boolean {
  return c.faMemory !== false;
}

export function validate(c: FactoryConfig, scm: ScmId = 'github'): Record<string, string> {
  const e: Record<string, string> = {};
  if (!(scm === 'gitlab' ? GITLAB_REPO : REPO).test(c.repo)) {
    e.repo = scm === 'gitlab' ? 'Use group/project' : 'Use owner/name';
  }
  if (!(scm === 'gitlab' ? GITLAB_LOGIN : LOGIN).test(c.machine)) {
    e.machine = scm === 'gitlab' ? 'Not a valid GitLab username' : 'Not a valid GitHub login';
  }
  if (isCiEnabled(c)) {
    if (!/^[\w.-]+\.ya?ml$/.test(c.ciFile)) e.ciFile = 'Workflow file name, e.g. ci.yml';
    if (!c.ciName.trim() || /['"\n]/.test(c.ciName)) e.ciName = 'Workflow display name (no quotes)';
    if (!c.checks.length || c.checks.some((x) => !x.trim() || /['",\n]/.test(x))) e.checks = 'At least one check name (no quotes or commas)';
  }
  const entries = allEntries(c);
  if (!entries.length) e.providers = 'Add at least one provider';
  else if (entries.some((x) => !x.model.trim())) e.providers = 'Every provider needs a model';
  else if (entries.some((x) => !/^[A-Z][A-Z0-9_]*$/.test(x.keyEnv))) e.providers = 'Key name: UPPER_SNAKE_CASE';
  else if (entries.some((x) => !/^https?:\/\/\S+$/.test(x.baseUrl))) e.providers = 'Base URL must start with http:// or https://';
  else if (keySlots(c).length > 3) e.providers = 'At most 3 distinct API keys (factory key slots)';
  if (c.merge.mode === 'token' && !/^[A-Z][A-Z0-9_]*$/.test(c.merge.tokenSecret)) e.merge = 'Secret name: UPPER_SNAKE_CASE';
  if (!/^[\w./-]+$/.test(c.baseBranch) || c.baseBranch.includes('..')) e.baseBranch = 'Branch name, e.g. main';
  if (!/^[\w.-]+$/.test(c.runner)) e.runner = 'Runner label, e.g. ubuntu-latest';
  return e;
}

export const allEntries = (c: FactoryConfig): ProviderEntry[] => [
  ...c.providers.entries,
  ...(c.providers.reviewSeparate ? c.providers.reviewEntries : []),
];

/** Distinct key env names, in order — each takes one CUSTOM_PROVIDER_KEYn factory slot. */
export const keySlots = (c: FactoryConfig): string[] => [...new Set(allEntries(c).map((x) => x.keyEnv))];

function providerEnv(list: ProviderEntry[], mode: 'default' | 'queue'): Record<string, string> {
  const spec = (x: ProviderEntry) => {
    const preset = PROVIDER_PRESETS.find((p) => p.id === x.preset)!;
    return { kind: preset.kind, cfg: { baseUrl: x.baseUrl, model: x.model, apiKeyEnv: x.keyEnv, input: ['text', 'image'] } };
  };
  const first = spec(list[0]);
  const env: Record<string, string> = {
    AI_AGENT_PROVIDER: 'fa',
    FA_PROVIDER_TYPE: first.kind,
    FA_PROVIDER_CONFIG: JSON.stringify({ ...first.cfg, apiKeyEnv: undefined, apiKeyEnvVar: list[0].keyEnv }),
  };
  if (mode === 'queue') {
    env.FA_PROVIDERS_QUEUE = JSON.stringify(list.map((x) => ({ provider_type: spec(x).kind, provider_config: spec(x).cfg })));
  }
  return env;
}

function runnerJson(
  tpl: string,
  c: FactoryConfig,
  review: boolean,
  scm: ScmId = 'github',
  gitlabBaseUrl = 'https://gitlab.com',
): string {
  const json = JSON.parse(tpl);
  const list = review && c.providers.reviewSeparate ? c.providers.reviewEntries : c.providers.entries;
  if (json.params.customParams?.targetRepository) json.params.customParams.targetRepository.baseBranch = c.baseBranch;
  json.params.envVariables = {
    ...(scm === 'gitlab'
      ? {
          DEFAULT_TRACKER: 'gitlab',
          DEFAULT_SCM: 'gitlab',
          DEFAULT_CI: 'gitlab-ci',
          GITLAB_BASE_PATH: gitlabBaseUrl,
          GITLAB_WORKSPACE: c.repo.split('/').slice(0, -1).join('/'),
          GITLAB_REPOSITORY: c.repo.split('/').at(-1) ?? '',
          GITLAB_BRANCH: c.baseBranch,
        }
      : json.params.envVariables.DEFAULT_TRACKER
        ? { DEFAULT_TRACKER: scm }
        : {}),
    ...providerEnv(list, c.providers.mode),
  };
  return JSON.stringify(json, null, 2) + '\n';
}

const slotLines = (c: FactoryConfig) => {
  const keys = keySlots(c);
  const inputs = keys.map((k, i) => `      CUSTOM_PROVIDER_KEY${i + 1}_NAME: '${k}'\n`).join('');
  const secrets = keys.map((k, i) => `      CUSTOM_PROVIDER_KEY${i + 1}: \${{ secrets.${k} }}\n`).join('');
  return { inputs, secrets };
};

const fill = (tpl: string, c: FactoryConfig) => {
  const ci = isCiEnabled(c);
  const ciName = ci ? c.ciName : '';
  const ciFile = ci ? c.ciFile : '';
  const checks = ci ? c.checks : [];
  const workflowRun = ci
    ? `  workflow_run:\n    workflows: ['${ciName}']\n    types: [completed]\n`
    : '';
  const pEnv = c.providers.entries.length
    ? providerEnv(c.providers.entries, c.providers.mode)
    : { FA_PROVIDER_TYPE: '', FA_PROVIDER_CONFIG: '' };
  return tpl
    .replaceAll('@@MACHINE@@', c.machine)
    .replaceAll('@@CI_NAME@@', ciName)
    .replaceAll('@@CI_FILE@@', ciFile)
    .replaceAll('@@CHECKS_JSON@@', JSON.stringify(checks))
    .replaceAll('@@CHECKS_CSV@@', checks.join(','))
    .replaceAll('@@RUNNER@@', c.runner)
    .replaceAll('@@FA_MEMORY@@', isFaMemoryEnabled(c) ? 'true' : 'false')
    .replaceAll('@@FA_PROVIDER_TYPE@@', pEnv.FA_PROVIDER_TYPE ?? '')
    .replaceAll('@@FA_PROVIDER_CONFIG@@', pEnv.FA_PROVIDER_CONFIG ?? '')
    .replaceAll('@@FA_PROVIDERS_QUEUE@@', pEnv.FA_PROVIDERS_QUEUE ?? '')
    .replace(/^@@FA_SLOT_INPUTS@@/m, slotLines(c).inputs)
    .replace(/^@@WORKFLOW_RUN@@/m, workflowRun)
    .replace(/^@@TOOLCHAINS@@\n/m, toolchainLines(c) + slotLines(c).inputs)
    .replace(/^@@PROVIDER_SECRETS@@\n/m, slotLines(c).secrets)
    .replaceAll('@@MERGE_TOKEN@@', c.merge.mode === 'token' ? c.merge.tokenSecret : 'SOURCE_GITHUB_TOKEN')
    .replace(/^@@MERGE_APP@@\n/m, c.merge.mode === 'app'
      ? '      MERGE_APP_ID: ${{ secrets.MERGE_APP_ID }}\n      MERGE_APP_PRIVATE_KEY: ${{ secrets.MERGE_APP_PRIVATE_KEY }}\n'
      : '');
};

function toolchainLines(c: FactoryConfig): string {
  const picked = TOOLCHAINS.filter((t) => c.toolchains[t.id] !== undefined && t.id !== 'go');
  if (!picked.length) return '';
  const head = '      # Toolchains the agent legs install (cached). Unlisted = nothing installed.\n';
  return head + picked.map((t) => `      ${t.input}: '${c.toolchains[t.id] || t.fallback}'\n`).join('');
}

export function renderQualityWorkflow(c: FactoryConfig): string | null {
  const preset = c.qualityPreset ?? 'none';
  if (!isCiEnabled(c) || preset === 'none') return null;
  const runner = c.runner;
  switch (preset) {
    case 'crap4js': {
      const ver = c.toolchains.node || '22';
      return `# Quality gate powered by IstiN/crap4js
name: Quality

on:
  push:
  pull_request:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  quality:
    runs-on: ${runner}
    steps:
      - uses: actions/checkout@v5
      - uses: actions/setup-node@v5
        with:
          node-version: '${ver}'
      - name: Install dependencies
        run: |
          if [ ! -f package.json ]; then
            echo "No package.json found — skipping dependency install."
          elif [ -f package-lock.json ]; then
            npm ci
          else
            npm install
          fi
      - name: Test with coverage
        run: |
          if [ ! -f package.json ] || ! node -e "const p=require('./package.json'); process.exit(p.scripts && p.scripts.test && !p.scripts.test.includes('Error: no test specified') ? 0 : 1)"; then
            echo "No tests configured or no package.json found — skipping test and coverage execution."
            exit 0
          fi
          npx c8 --reporter=json --reporter=text npm test
      - name: CRAP analysis (IstiN/crap4js)
        run: |
          if [ ! -f coverage/coverage-final.json ]; then
            echo "No coverage data found — skipping CRAP analysis."
            exit 0
          fi
          npx --yes github:IstiN/crap4js --coverage coverage/coverage-final.json
`;
    }
    case 'crap4dart': {
      return `# Quality gate powered by IstiN/crap4dart
name: Quality

on:
  push:
  pull_request:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  quality:
    runs-on: ${runner}
    steps:
      - uses: actions/checkout@v5
      - uses: dart-lang/setup-dart@v1
        with:
          sdk: stable
      - name: Run Dart checks and coverage
        run: |
          if [ ! -f pubspec.yaml ]; then
            echo "No pubspec.yaml found — skipping Dart quality checks."
            exit 0
          fi
          dart pub get
          dart format --set-exit-if-changed .
          dart analyze
          if [ ! -d test ] || [ -z "$(find test -name '*_test.dart' -print -quit 2>/dev/null)" ]; then
            echo "No Dart tests found in test/ — skipping test coverage and CRAP analysis."
            exit 0
          fi
          dart test --coverage=coverage
          dart pub global activate coverage
          dart pub global run coverage:format_coverage --lcov --in coverage --out coverage/lcov.info --report-on lib
      - name: CRAP analysis (IstiN/crap4dart)
        run: |
          if [ ! -f coverage/lcov.info ]; then
            echo "No coverage/lcov.info found — skipping CRAP analysis."
            exit 0
          fi
          dart pub global activate -sgit https://github.com/IstiN/crap4dart.git
          crap4dart analyze --lcov coverage/lcov.info
`;
    }
    case 'crap4java': {
      const ver = c.toolchains.java || '21';
      return `# Quality gate powered by IstiN/crap4java
name: Quality

on:
  push:
  pull_request:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  quality:
    runs-on: ${runner}
    steps:
      - uses: actions/checkout@v5
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '${ver}'
      - name: Build and run CRAP analysis (IstiN/crap4java)
        run: |
          if ( [ ! -f pom.xml ] && [ ! -f build.gradle ] && [ ! -f build.gradle.kts ] ) || [ -z "$(find . -name '*.java' -not -path './.*' -print -quit)" ]; then
            echo "No Java build file or source files found — skipping Java build and CRAP analysis."
            exit 0
          fi
          git clone --depth 1 https://github.com/IstiN/crap4java.git /tmp/crap4java
          mvn -q -f /tmp/crap4java/pom.xml -DskipTests package
          java -jar /tmp/crap4java/target/crap4java-*.jar
`;
    }
    case 'crap4py': {
      const ver = c.toolchains.python || '3.12';
      return `# Quality gate powered by vabhzw17eg2qu4m9-bit/crap4py
name: Quality

on:
  push:
  pull_request:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  quality:
    runs-on: ${runner}
    steps:
      - uses: actions/checkout@v5
      - uses: actions/setup-python@v6
        with:
          python-version: '${ver}'
      - name: Install dependencies + crap4py
        run: |
          python -m pip install --upgrade pip
          if [ -f pyproject.toml ] || [ -f setup.py ]; then pip install -e .; fi
          if [ -f requirements.txt ]; then pip install -r requirements.txt; fi
          pip install coverage pytest git+https://github.com/vabhzw17eg2qu4m9-bit/crap4py.git
      - name: Test with coverage
        run: |
          if [ -z "$(find . -name '*.py' -not -path './.*' -not -path './venv/*' -print -quit)" ]; then
            echo "No Python files found — skipping test and coverage execution."
            exit 0
          fi
          set +e
          coverage run -m pytest
          rc=$?
          set -e
          if [ $rc -eq 5 ]; then
            echo "No tests collected (pytest exit code 5) — skipping coverage report and CRAP analysis."
            exit 0
          elif [ $rc -ne 0 ]; then
            exit $rc
          fi
          coverage json -o coverage.json
      - name: CRAP analysis (vabhzw17eg2qu4m9-bit/crap4py)
        run: |
          if [ ! -f coverage.json ]; then
            echo "No coverage.json found — skipping CRAP analysis."
            exit 0
          fi
          crap4py --coverage coverage.json
`;
    }
    case 'crap4go': {
      const ver = c.toolchains.go || '1.23';
      return `# Quality gate powered by vabhzw17eg2qu4m9-bit/crap4go
name: Quality

on:
  push:
  pull_request:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  quality:
    runs-on: ${runner}
    steps:
      - uses: actions/checkout@v5
      - uses: actions/setup-go@v6
        with:
          go-version: '${ver}'
      - name: Format check
        run: |
          if [ -z "$(find . -name '*.go' -not -path './.*' -print -quit)" ]; then
            echo "No Go source files found — skipping format check."
            exit 0
          fi
          unformatted=$(gofmt -l .)
          if [ -n "$unformatted" ]; then
            echo "::error::Files need gofmt:"
            echo "$unformatted"
            exit 1
          fi
      - name: Vet and test with coverage
        run: |
          if [ ! -f go.mod ] || [ -z "$(find . -name '*.go' -not -path './.*' -print -quit)" ]; then
            echo "No go.mod or Go source files found — skipping Go test coverage and CRAP analysis."
            exit 0
          fi
          go vet ./...
          if [ -z "$(find . -name '*_test.go' -not -path './.*' -print -quit)" ]; then
            echo "No Go test files found — skipping coverage and CRAP analysis."
            exit 0
          fi
          go test -race -coverprofile=coverage.out -covermode=atomic ./...
      - name: CRAP analysis (vabhzw17eg2qu4m9-bit/crap4go)
        run: |
          if [ ! -f coverage.out ]; then
            echo "No coverage.out found — skipping CRAP analysis."
            exit 0
          fi
          go install github.com/vabhzw17eg2qu4m9-bit/crap4go@latest
          "$(go env GOPATH)/bin/crap4go" --coverage coverage.out
`;
    }
  }
}

export function renderFahConfig(c: FactoryConfig): string | null {
  if (!isFaMemoryEnabled(c)) return null;
  return `memory:\n  projectPath: .fah/memory\n`;
}

export function renderFiles(c: FactoryConfig): FactoryFile[] {
  const wf = (name: string, body: string) => ({ path: `.github/workflows/${name}`, content: fill(body, c) });
  const quality = renderQualityWorkflow(c);
  const fahConfig = renderFahConfig(c);
  return [
    ...(quality ? [{ path: `.github/workflows/${c.ciFile || 'quality.yml'}`, content: quality }] : []),
    wf('ai-teammate.yml', aiTeammate),
    ...(isFaUniversalEnabled(c) ? [wf('fa-universal.yml', faUniversal)] : []),
    wf('machine-sm.yml', machineSm),
    wf('machine-merge.yml', machineMerge),
    wf('merge-trigger.yml', mergeTrigger),
    wf('sm-kicker.yml', smKicker),
    ...(fahConfig ? [{ path: '.fah/config.yaml', content: fahConfig }] : []),
    { path: '.dmtools/config.js', content: fill(dmtoolsConfig, c) },
    { path: '.dmtools/runners/fa-bug-dev.json', content: runnerJson(runnerBug, c, false) },
    { path: '.dmtools/runners/fa-story-dev.json', content: runnerJson(runnerStory, c, false) },
    { path: '.dmtools/runners/fa-rework.json', content: runnerJson(runnerRework, c, false) },
    { path: '.dmtools/runners/fa-review.json', content: runnerJson(runnerReview, c, true) },
  ];
}

function renderGitlabQualityJob(c: FactoryConfig): string | null {
  if (!isCiEnabled(c)) return null;
  const preset = c.qualityPreset ?? 'none';
  if (preset === 'none') return null;
  const jobs = {
    crap4js: { template: gitlabQualityJs, version: c.toolchains.node || '22' },
    crap4dart: { template: gitlabQualityDart, version: c.toolchains.flutter || 'stable' },
    crap4java: { template: gitlabQualityJava, version: c.toolchains.java || '21' },
    crap4py: { template: gitlabQualityPy, version: c.toolchains.python || '3.12' },
    crap4go: { template: gitlabQualityGo, version: c.toolchains.go || '1.23' },
  };
  const job = jobs[preset];
  return job.template.replaceAll('@@VERSION@@', job.version);
}

/** GitLab projects use native GitLab CI jobs; GitHub Actions files are intentionally omitted. */
export function renderGitlabFiles(c: FactoryConfig, gitlabBaseUrl = 'https://gitlab.com'): FactoryFile[] {
  const files = renderFiles(c).filter(
    (file) => !file.path.startsWith('.github/'),
  );
  const qualityJob = renderGitlabQualityJob(c);
  const runnerTemplates: Record<string, string> = {
    '.dmtools/runners/fa-bug-dev.json': runnerBug,
    '.dmtools/runners/fa-story-dev.json': runnerStory,
    '.dmtools/runners/fa-rework.json': runnerRework,
    '.dmtools/runners/fa-review.json': runnerReview,
  };
  return [
    ...files.map((file) => {
      const tpl = runnerTemplates[file.path];
      return tpl
        ? { ...file, content: runnerJson(tpl, c, file.path.endsWith('fa-review.json'), 'gitlab', gitlabBaseUrl) }
        : file;
    }),
    {
      path: '.dmtools/scripts/gitlab-factory-poll-issues.sh',
      content: gitlabIssuePoller
        .replaceAll('@@MACHINE@@', c.machine)
        .replaceAll('@@BASE_BRANCH@@', c.baseBranch),
    },
    {
      path: '.dmtools/scripts/gitlab-factory-publish-review.sh',
      content: gitlabReviewPublisher,
    },
    {
      path: '.gitlab-ci.yml',
      content: gitlabCi
        .replace(/^  - quality\n/m, (stage) => qualityJob ? stage : '')
        .replaceAll('@@QUALITY_JOB@@', qualityJob ?? '')
        .replaceAll('@@MACHINE@@', c.machine),
    },
  ];
}

function mergeChecklist(c: FactoryConfig): string {
  if (c.merge.mode === 'token') return `- [ ] Secret \`${c.merge.tokenSecret}\` (PAT of the merge account; repo scope, allowed to merge into the protected branch)\n`;
  if (c.merge.mode === 'app') return '- [ ] GitHub App installed on this repository (Contents, Pull requests, Issues: read & write; Checks: read) and allowed to bypass/merge in the branch ruleset\n- [ ] Secrets `MERGE_APP_ID` and `MERGE_APP_PRIVATE_KEY` (App id + generated private key PEM)\n';
  return '';
}

function ciChecklist(c: FactoryConfig): string {
  if (!isCiEnabled(c)) return '- [ ] No CI workflow configured (CI gate skipped — you can add a quality workflow later)\n';
  if (c.qualityPreset && c.qualityPreset !== 'none') {
    return `- [ ] Generated \`${c.ciFile}\` (${c.qualityPreset}) publishes: ${c.checks.map((x) => `\`${x}\``).join(', ')}\n`;
  }
  return `- [ ] \`${c.ciFile}\` has a \`workflow_dispatch\` trigger (the SM dispatches it on PR heads) and publishes: ${c.checks.map((x) => `\`${x}\``).join(', ')}\n`;
}

export function prBody(c: FactoryConfig): string {
  return `Sets up the DMTools agent factory for \`${c.repo}\`, generated by https://dmtools.lab.epam.com/factory/.

## Owner checklist
- [ ] Secret \`SOURCE_GITHUB_TOKEN\` (PAT, repo scope)
${mergeChecklist(c)}${keySlots(c).map((k) => `- [ ] Secret \`${k}\` (AI provider key)`).join('\n')}
- [ ] \`${c.machine}\` is a collaborator with write access
${c.createLabels !== false
  ? `- [x] Labels: ${FACTORY_LABELS.map((l) => l.name).join(', ')} (auto-created during setup)`
  : `- [ ] Labels: ${FACTORY_LABELS.map((l) => l.name).join(', ')}`}
${ciChecklist(c)}${isFaUniversalEnabled(c) ? `- [x] **FA Universal Agent** (\`.github/workflows/fa-universal.yml\`): mention \`@fa\` in any Issue or PR comment to invoke the interactive agent (single-queue session on \`fa-sess/<session_key>\`)\n` : ''}${isFaMemoryEnabled(c) ? `- [x] **Project Memory** (\`.fah/config.yaml\` → \`.fah/memory\`): durable agent knowledge persisted in the repository\n` : ''}- [ ] Optional repo variables \`AGENTS_VERSION\` / \`DMTOOLS_VERSION\` (default \`latest\`) — pin agent packs / CLI to a release tag

Smoke test: assign an issue to \`${c.machine}\`${isFaUniversalEnabled(c) ? `, mention \`@fa\` in any issue/PR comment,` : ''} or dispatch **AI Teammate** with leg=dev.`;
}

// ---- GitHub API (browser → api.github.com, the token never leaves the page) ----

async function gh<T>(token: string, path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(`https://api.github.com${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github+json',
      'Content-Type': 'application/json',
      ...(init.headers ?? {}),
    },
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`GitHub ${res.status} on ${path}: ${body.slice(0, 200)}`);
  }
  return res.json() as Promise<T>;
}


/** Creates a new GitHub repository (Greenfield) with an initial commit on its default branch. */
export async function createRepo(
  token: string,
  repo: string,
  isPrivate = true,
  userLogin?: string,
  log: (m: string) => void = () => {},
): Promise<{ full_name: string; default_branch: string }> {
  const [owner, name] = repo.split('/');
  if (!owner || !name) throw new Error('Repository must be owner/name');
  const login = userLogin ?? (await gh<{ login: string }>(token, '/user')).login;
  const path = owner.toLowerCase() === login.toLowerCase() ? '/user/repos' : `/orgs/${encodeURIComponent(owner)}/repos`;
  const created = await gh<{ full_name: string; default_branch: string }>(token, path, {
    method: 'POST',
    body: JSON.stringify({ name, private: isPrivate, auto_init: true }),
  });
  log(`created ${created.full_name} (${isPrivate ? 'private' : 'public'}, branch ${created.default_branch})`);
  return created;
}

/** Fetches existing label names on a repository. */
export async function listLabels(token: string, repo: string): Promise<string[]> {
  const r = await gh<{ name: string }[]>(token, `/repos/${repo}/labels?per_page=100`);
  return r.map((x) => x.name);
}

/** Ensures all required FACTORY_LABELS exist on the repository, creating any missing ones. */
export async function ensureLabels(
  token: string,
  repo: string,
  log: (m: string) => void = () => {},
): Promise<{ created: string[]; existing: string[] }> {
  const current = await listLabels(token, repo);
  const currentLower = new Set(current.map((n) => n.toLowerCase()));
  const created: string[] = [];
  const existing: string[] = [];
  for (const l of FACTORY_LABELS) {
    if (currentLower.has(l.name.toLowerCase())) {
      existing.push(l.name);
      continue;
    }
    try {
      await gh(token, `/repos/${repo}/labels`, {
        method: 'POST',
        body: JSON.stringify({ name: l.name, color: l.color, description: l.description }),
      });
      created.push(l.name);
    } catch (e) {
      const msg = (e as Error).message || '';
      if (msg.includes('GitHub 422') || msg.includes('already_exists')) {
        existing.push(l.name);
      } else {
        throw e;
      }
    }
  }
  log(created.length ? `labels created: ${created.join(', ')}` : 'labels: all required labels already exist');
  return { created, existing };
}

export async function createFactoryPr(
  token: string,
  c: FactoryConfig,
  log: (m: string) => void = () => {},
): Promise<string> {
  const { repo } = c;
  const meta = await gh<{ default_branch: string }>(token, `/repos/${repo}`);
  const base = c.baseBranch || meta.default_branch;
  const head = await gh<{ object: { sha: string } }>(token, `/repos/${repo}/git/ref/heads/${base}`);
  const baseCommit = await gh<{ tree: { sha: string } }>(token, `/repos/${repo}/git/commits/${head.object.sha}`);
  log(`base ${base} @ ${head.object.sha.slice(0, 7)}`);

  const tree = [];
  for (const f of renderFiles(c)) {
    const blob = await gh<{ sha: string }>(token, `/repos/${repo}/git/blobs`, {
      method: 'POST',
      body: JSON.stringify({ content: f.content, encoding: 'utf-8' }),
    });
    tree.push({ path: f.path, mode: '100644', type: 'blob', sha: blob.sha });
    log(`blob ${f.path}`);
  }
  const newTree = await gh<{ sha: string }>(token, `/repos/${repo}/git/trees`, {
    method: 'POST',
    body: JSON.stringify({ base_tree: baseCommit.tree.sha, tree }),
  });
  const commit = await gh<{ sha: string }>(token, `/repos/${repo}/git/commits`, {
    method: 'POST',
    body: JSON.stringify({ message: 'chore: set up DMTools agent factory', tree: newTree.sha, parents: [head.object.sha] }),
  });
  const branch = `chore/dmtools-factory-${Date.now().toString(36)}`;
  await gh(token, `/repos/${repo}/git/refs`, {
    method: 'POST',
    body: JSON.stringify({ ref: `refs/heads/${branch}`, sha: commit.sha }),
  });
  const pr = await gh<{ html_url: string }>(token, `/repos/${repo}/pulls`, {
    method: 'POST',
    body: JSON.stringify({ title: 'chore: set up DMTools agent factory', head: branch, base, body: prBody(c) }),
  });
  return pr.html_url;
}

export interface RepoSummary {
  full_name: string;
  private: boolean;
  default_branch?: string;
  permissions?: { push?: boolean };
}

/** Validates a GitLab instance URL and returns its API root without a trailing slash. */
export function normalizeGitlabBaseUrl(baseUrl: string): string {
  let url: URL;
  try {
    url = new URL(baseUrl.trim());
  } catch {
    throw new Error('Enter a valid GitLab instance URL, such as https://gitlab.com');
  }
  const loopback = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  if (
    (url.protocol !== 'https:' && !(url.protocol === 'http:' && loopback)) ||
    url.username ||
    url.password ||
    url.search ||
    url.hash
  ) {
    throw new Error('GitLab instance URL must use HTTPS (HTTP is allowed only on localhost) and cannot include credentials, a query, or a fragment');
  }
  const path = url.pathname.replace(/\/+$/, '').replace(/\/api\/v4$/i, '');
  return `${url.origin}${path}`;
}

/** Repositories the token can push to, most recently pushed first (owner/org filter optional). */
export async function listRepos(
  token: string,
  scm: ScmId = 'github',
  gitlabBaseUrl = 'https://gitlab.com',
): Promise<RepoSummary[]> {
  if (scm === 'gitlab') return listGitlabRepos(token, gitlabBaseUrl);
  const out: RepoSummary[] = [];
  for (let page = 1; page <= 5; page++) {
    const r = await gh<RepoSummary[]>(token, `/user/repos?per_page=100&sort=pushed&page=${page}&affiliation=owner,collaborator,organization_member`);
    out.push(...r.filter((x) => x.permissions?.push));
    if (r.length < 100) break;
  }
  return out;
}

async function listGitlabRepos(token: string, baseUrl: string): Promise<RepoSummary[]> {
  const apiUrl = `${normalizeGitlabBaseUrl(baseUrl)}/api/v4`;
  const out: RepoSummary[] = [];
  for (let page = 1; page <= 5; page++) {
    const res = await fetch(
      `${apiUrl}/projects?membership=true&min_access_level=30&order_by=last_activity_at&per_page=100&page=${page}`,
      { headers: { 'PRIVATE-TOKEN': token, Accept: 'application/json' } },
    );
    if (!res.ok) {
      throw new Error(`GitLab ${res.status} while listing projects: ${(await res.text()).slice(0, 200)}`);
    }
    const projects = (await res.json()) as {
      path_with_namespace: string;
      visibility: string;
      default_branch?: string;
    }[];
    out.push(...projects.map((p) => ({
      full_name: p.path_with_namespace,
      private: p.visibility !== 'public',
      ...(p.default_branch ? { default_branch: p.default_branch } : {}),
    })));
    if (projects.length < 100) break;
  }
  return out;
}

function gitlabProjectPath(repo: string): string {
  if (!GITLAB_REPO.test(repo)) throw new Error('GitLab project must be group/project');
  return encodeURIComponent(repo);
}

async function gitlabRequest<T>(
  token: string,
  baseUrl: string,
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const res = await fetch(`${normalizeGitlabBaseUrl(baseUrl)}/api/v4${path}`, {
    ...init,
    headers: {
      'PRIVATE-TOKEN': token,
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...(init.headers ?? {}),
    },
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`GitLab ${res.status} on ${path}: ${body.slice(0, 200)}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

/** Fetches all existing label names on a GitLab project. */
export async function listGitlabLabels(token: string, repo: string, baseUrl: string): Promise<string[]> {
  const project = gitlabProjectPath(repo);
  const labels: { name: string }[] = [];
  for (let page = 1; page <= 10; page++) {
    const current = await gitlabRequest<{ name: string }[]>(
      token,
      baseUrl,
      `/projects/${project}/labels?per_page=100&page=${page}`,
    );
    labels.push(...current);
    if (current.length < 100) break;
  }
  return labels.map((label) => label.name);
}

/** Ensures all required FACTORY_LABELS exist on a GitLab project. */
export async function ensureGitlabLabels(
  token: string,
  repo: string,
  baseUrl: string,
  log: (message: string) => void = () => {},
): Promise<{ created: string[]; existing: string[] }> {
  const project = gitlabProjectPath(repo);
  const current = await listGitlabLabels(token, repo, baseUrl);
  const currentLower = new Set(current.map((name) => name.toLowerCase()));
  const created: string[] = [];
  const existing: string[] = [];
  for (const label of FACTORY_LABELS) {
    if (currentLower.has(label.name.toLowerCase())) {
      existing.push(label.name);
      continue;
    }
    try {
      await gitlabRequest(token, baseUrl, `/projects/${project}/labels`, {
        method: 'POST',
        body: JSON.stringify({
          name: label.name,
          color: `#${label.color}`,
          description: label.description,
        }),
      });
      created.push(label.name);
    } catch (error) {
      if ((error as Error).message.startsWith('GitLab 409 ')) {
        const latest = await listGitlabLabels(token, repo, baseUrl);
        if (latest.some((name) => name.toLowerCase() === label.name.toLowerCase())) {
          existing.push(label.name);
          continue;
        }
      }
      throw error;
    }
  }
  log(created.length ? `labels created: ${created.join(', ')}` : 'labels: all required labels already exist');
  return { created, existing };
}

async function listGitlabVariables(
  token: string,
  repo: string,
  baseUrl: string,
): Promise<{ key: string; masked?: boolean; hidden?: boolean; masked_and_hidden?: boolean }[]> {
  const project = gitlabProjectPath(repo);
  const variables: { key: string; masked?: boolean; hidden?: boolean; masked_and_hidden?: boolean }[] = [];
  for (let page = 1; page <= 10; page++) {
    const current = await gitlabRequest<{ key: string; masked?: boolean; hidden?: boolean; masked_and_hidden?: boolean }[]>(
      token,
      baseUrl,
      `/projects/${project}/variables?per_page=100&page=${page}`,
    );
    variables.push(...current);
    if (current.length < 100) break;
  }
  return variables;
}

export async function listGitlabVariableNames(token: string, repo: string, baseUrl: string): Promise<string[]> {
  return (await listGitlabVariables(token, repo, baseUrl)).map((variable) => variable.key);
}

const GITLAB_ISSUE_WEBHOOK_TRIGGER_DESCRIPTION = 'DMTools factory issue webhook';
const GITLAB_ISSUE_WEBHOOK_MARKER = 'issue_webhook';

async function listGitlabIssueWebhooks(token: string, project: string, baseUrl: string) {
  const hooks: { id: number; url: string }[] = [];
  for (let page = 1; page <= 10; page++) {
    const current = await gitlabRequest<{ id: number; url: string }[]>(
      token,
      baseUrl,
      `/projects/${project}/hooks?per_page=100&page=${page}`,
    );
    hooks.push(...current);
    if (current.length < 100) break;
  }
  return hooks.filter((hook) => {
    try {
      return new URL(hook.url).searchParams.get('variables[DMTOOLS_FACTORY_TRIGGER]') === GITLAB_ISSUE_WEBHOOK_MARKER;
    } catch {
      return false;
    }
  });
}

async function deleteGitlabIssueWebhook(
  token: string,
  project: string,
  baseUrl: string,
  hook: { id: number; url: string },
): Promise<void> {
  const triggerToken = new URL(hook.url).searchParams.get('token');
  try {
    await gitlabRequest(token, baseUrl, `/projects/${project}/hooks/${hook.id}`, { method: 'DELETE' });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    throw new Error(triggerToken ? message.replaceAll(triggerToken, '[REDACTED]') : message);
  }
}

async function listGitlabIssueWebhookTriggers(token: string, project: string, baseUrl: string) {
  const triggers: { id: number; token?: string; description: string }[] = [];
  for (let page = 1; page <= 10; page++) {
    const current = await gitlabRequest<{ id: number; token?: string; description: string }[]>(
      token,
      baseUrl,
      `/projects/${project}/triggers?per_page=100&page=${page}`,
    );
    triggers.push(...current);
    if (current.length < 100) break;
  }
  return triggers.filter((trigger) => trigger.description === GITLAB_ISSUE_WEBHOOK_TRIGGER_DESCRIPTION);
}

/** Creates or updates the managed GitLab Issue-event webhook and its pipeline trigger token. */
export async function ensureGitlabIssueEventWebhook(
  token: string,
  repo: string,
  baseUrl: string,
  ref: string,
  log: (message: string) => void = () => {},
): Promise<void> {
  const project = gitlabProjectPath(repo);
  const existingTriggers = await listGitlabIssueWebhookTriggers(token, project, baseUrl);
  const reusableTrigger = existingTriggers.find((trigger) => trigger.token);
  const trigger = reusableTrigger?.token
    ? reusableTrigger
    : await gitlabRequest<{ id: number; token: string; description: string }>(
        token,
        baseUrl,
        `/projects/${project}/triggers`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: new URLSearchParams({ description: GITLAB_ISSUE_WEBHOOK_TRIGGER_DESCRIPTION }),
        },
      );
  if (!trigger.token) {
    throw new Error('GitLab did not return the factory webhook trigger token. Try setup again.');
  }

  const triggerUrl = new URL(
    `${normalizeGitlabBaseUrl(baseUrl)}/api/v4/projects/${project}/trigger/pipeline`,
  );
  triggerUrl.search = new URLSearchParams({
    token: trigger.token,
    ref,
    'variables[DMTOOLS_FACTORY_TRIGGER]': GITLAB_ISSUE_WEBHOOK_MARKER,
  }).toString();
  const hooks = await listGitlabIssueWebhooks(token, project, baseUrl);
  const currentHook = hooks[0];
  const hookPath = currentHook
    ? `/projects/${project}/hooks/${currentHook.id}`
    : `/projects/${project}/hooks`;
  try {
    await gitlabRequest(token, baseUrl, hookPath, {
      method: currentHook ? 'PUT' : 'POST',
      body: JSON.stringify({
        url: triggerUrl.toString(),
        issues_events: true,
        push_events: false,
        enable_ssl_verification: true,
      }),
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    throw new Error(message.replaceAll(trigger.token, '[REDACTED]'));
  }

  for (const duplicateHook of hooks.slice(1)) {
    await deleteGitlabIssueWebhook(token, project, baseUrl, duplicateHook);
  }
  for (const duplicateTrigger of existingTriggers) {
    if (duplicateTrigger.id !== trigger.id) {
      await gitlabRequest(token, baseUrl, `/projects/${project}/triggers/${duplicateTrigger.id}`, { method: 'DELETE' });
    }
  }
  log(`GitLab Issue-event webhook ${currentHook ? 'updated' : 'created'} for ${ref}`);
}

/** Removes the factory-managed GitLab Issue-event webhook and its trigger token. */
export async function removeGitlabIssueEventWebhook(
  token: string,
  repo: string,
  baseUrl: string,
  log: (message: string) => void = () => {},
): Promise<void> {
  const project = gitlabProjectPath(repo);
  const hooks = await listGitlabIssueWebhooks(token, project, baseUrl);
  for (const hook of hooks) {
    await deleteGitlabIssueWebhook(token, project, baseUrl, hook);
  }
  const triggers = await listGitlabIssueWebhookTriggers(token, project, baseUrl);
  for (const trigger of triggers) {
    await gitlabRequest(token, baseUrl, `/projects/${project}/triggers/${trigger.id}`, { method: 'DELETE' });
  }
  if (hooks.length || triggers.length) log('Removed the DMTools factory Issue-event webhook');
}

/** Creates secret variables as masked and hidden; values are sent only to the configured instance. */
export async function putGitlabVariables(
  token: string,
  repo: string,
  baseUrl: string,
  variables: Record<string, string>,
  log: (message: string) => void = () => {},
): Promise<void> {
  const project = gitlabProjectPath(repo);
  const entries = Object.entries(variables).filter(([, value]) => value);
  if (!entries.length) return;
  for (const [key, value] of entries) {
    if (value.includes('\n')) {
      throw new Error(`GitLab secret variable ${key} must be a single line to be masked and hidden.`);
    }
    if (value.length < 8 || /\s/.test(value) || !/^[A-Za-z0-9_+=/@:.~-]+$/.test(value)) {
      throw new Error(`GitLab variable ${key} cannot be masked and hidden. Use a value of at least 8 characters with no whitespace or unsupported characters.`);
    }
  }
  const versionInfo = await gitlabRequest<{ version: string }>(token, baseUrl, '/version');
  const version = versionInfo.version.match(/^(\d+)\.(\d+)/);
  if (!version || Number(version[1]) < 17 || (Number(version[1]) === 17 && Number(version[2]) < 4)) {
    throw new Error('Masked and hidden GitLab variables require GitLab 17.4 or newer. No project variables were changed.');
  }
  const existing = await listGitlabVariables(token, repo, baseUrl);
  for (const [key, value] of entries) {
    const current = existing.find((variable) => variable.key === key);
    const body = {
      key,
      value,
      variable_type: 'env_var',
      masked: true,
      protected: false,
      raw: true,
    };
    const path = current
      ? `/projects/${project}/variables/${encodeURIComponent(key)}`
      : `/projects/${project}/variables`;
    if (current && !(current.hidden || current.masked_and_hidden)) {
      await gitlabRequest<void>(token, baseUrl, path, { method: 'DELETE' });
      await gitlabRequest(token, baseUrl, `/projects/${project}/variables`, {
        method: 'POST',
        body: JSON.stringify({ ...body, masked_and_hidden: true }),
      });
      log(`GitLab project variable ${key} recreated as masked and hidden`);
      continue;
    }
    const method = current ? 'PUT' : 'POST';
    await gitlabRequest(token, baseUrl, path, {
      method,
      body: JSON.stringify({ ...body, ...(!current ? { masked_and_hidden: true } : {}) }),
    });
    log(`GitLab project variable ${key} ${current ? 'updated' : 'created'} as masked and hidden`);
  }
}

/** Creates a GitLab branch, commits generated GitLab setup files, and opens a merge request. */
export async function createGitlabMergeRequest(
  token: string,
  repo: string,
  baseUrl: string,
  c: FactoryConfig,
  log: (message: string) => void = () => {},
): Promise<string> {
  const project = gitlabProjectPath(repo);
  const projectInfo = await gitlabRequest<{ default_branch: string }>(token, baseUrl, `/projects/${project}`);
  const base = c.baseBranch || projectInfo.default_branch;
  const files = renderGitlabFiles(c, baseUrl);
  const fileExists = async (path: string) => {
    try {
      await gitlabRequest(token, baseUrl, `/projects/${project}/repository/files/${encodeURIComponent(path)}?ref=${encodeURIComponent(base)}`);
      return true;
    } catch (error) {
      if ((error as Error).message.startsWith('GitLab 404 ')) return false;
      throw error;
    }
  };
  const existing = await Promise.all(files.map((file) => fileExists(file.path)));
  const branch = `chore/dmtools-factory-${Date.now().toString(36)}`;
  await gitlabRequest(token, baseUrl, `/projects/${project}/repository/branches`, {
    method: 'POST',
    body: JSON.stringify({ branch, ref: base }),
  });
  const actions = files.map((file, index) => ({
    action: existing[index] ? 'update' : 'create',
    file_path: file.path,
    content: file.content,
  }));
  await gitlabRequest(token, baseUrl, `/projects/${project}/repository/commits`, {
    method: 'POST',
    body: JSON.stringify({
      branch,
      commit_message: 'chore: set up DMTools for GitLab',
      actions,
    }),
  });
  log(`committed ${files.length} GitLab setup files to ${branch}`);
  const mr = await gitlabRequest<{ web_url: string }>(token, baseUrl, `/projects/${project}/merge_requests`, {
    method: 'POST',
    body: JSON.stringify({
      source_branch: branch,
      target_branch: base,
      title: 'chore: set up DMTools for GitLab',
      description: `Sets up the DMTools factory for \`${repo}\` using native GitLab CI.\n\nAfter merging this request, a GitLab Issue-event webhook triggers the poller pipeline for Issue events. The poller filters for open issues assigned to the configured AI teammate or labeled with \`agent:dev\`, \`agent:review\`, or \`agent:rework\`. You can also run an agent manually from **Build > Pipelines** by setting \`DMTOOLS_TICKET_ID\` and \`DMTOOLS_AGENT_CONFIG\`. Merge-request pipelines can run the review or rework agent when the corresponding label is present, and approved merge requests by \`${c.machine}\` are merged after the pipeline succeeds.\n\nThe poller requires the \`GITLAB_TOKEN\` project CI/CD variable created by this setup.`,
      remove_source_branch: true,
    }),
  });
  log(`merge request created: ${mr.web_url}`);
  return mr.web_url;
}

export interface CiWorkflow {
  file: string; // e.g. ci.yml
  name: string; // workflow `name:`
  dispatchable: boolean; // has workflow_dispatch — the SM can trigger it
  onPullRequest: boolean; // also auto-runs on PRs (duplicate runs under the factory)
  jobs: string[]; // check-run names its jobs publish
}

export interface CiDetection {
  required: string[]; // required status contexts on the default branch
  workflows: CiWorkflow[];
}

/** Light YAML scan (no parser dependency): workflow name, triggers, job check names. */
export function scanWorkflow(file: string, text: string): CiWorkflow {
  const lines = text.split('\n');
  const top = (key: string) => lines.find((l) => l.startsWith(`${key}:`));
  const name = (top('name') ?? '').replace(/^name:\s*/, '').replace(/^['"]|['"]$/g, '').trim() || file;
  const onIdx = lines.findIndex((l) => /^(on|"on"|'on'):/.test(l));
  let onBlock = onIdx >= 0 ? lines[onIdx] : '';
  for (let k = onIdx + 1; onIdx >= 0 && k < lines.length && !/^\S/.test(lines[k]); k++) onBlock += '\n' + lines[k];
  const jobs: string[] = [];
  const jobsIdx = lines.findIndex((l) => /^jobs:\s*$/.test(l));
  for (let k = jobsIdx + 1; jobsIdx >= 0 && k < lines.length && !/^\S/.test(lines[k]); k++) {
    const m = lines[k].match(/^ {2}([\w-]+):\s*$/);
    if (!m) continue;
    let label = m[1];
    for (let j = k + 1; j < lines.length && !/^ {0,2}\S/.test(lines[j]); j++) {
      const n = lines[j].match(/^ {4}name:\s*(.+)$/);
      if (n) { label = n[1].replace(/^['"]|['"]$/g, '').trim(); break; }
    }
    jobs.push(label);
  }
  return {
    file,
    name,
    dispatchable: /workflow_dispatch/.test(onBlock),
    onPullRequest: /pull_request\b/.test(onBlock),
    jobs,
  };
}

/** Workflows that are not CI: the factory's own stubs and housekeeping bots. */
const NOT_CI = /(machine-|merge-trigger|sm-kicker|ai-teammate|dependabot|auto-update|deploy|pages|label|stale|release|publish)/i;

/**
 * CI gates for the factory. The SM DISPATCHES the CI workflow on PR heads (workflow_dispatch) and
 * waits for the required check contexts, so detection reads the source of truth directly:
 * required contexts from rulesets / branch protection, and the workflow files whose jobs publish them.
 */
export async function detectCi(token: string, repo: string): Promise<CiDetection> {
  const meta = await gh<{ default_branch: string }>(token, `/repos/${repo}`);
  const branch = meta.default_branch;
  const required = new Set<string>();
  try {
    const rules = await gh<{ type: string; parameters?: { required_status_checks?: { context: string }[] } }[]>(
      token, `/repos/${repo}/rules/branches/${encodeURIComponent(branch)}`);
    for (const r of rules) if (r.type === 'required_status_checks') r.parameters?.required_status_checks?.forEach((c) => required.add(c.context));
  } catch { /* no rulesets */ }
  try {
    const p = await gh<{ contexts: string[] }>(token, `/repos/${repo}/branches/${encodeURIComponent(branch)}/protection/required_status_checks`);
    p.contexts.forEach((c) => required.add(c));
  } catch { /* not admin or unprotected */ }

  let files: { name: string; path: string; type: string }[] = [];
  try { files = await gh(token, `/repos/${repo}/contents/.github/workflows?ref=${encodeURIComponent(branch)}`); } catch { /* none */ }
  const workflows: CiWorkflow[] = [];
  for (const f of files.filter((x) => x.type === 'file' && /\.ya?ml$/.test(x.name) && !NOT_CI.test(x.name))) {
    const c = await gh<{ content: string }>(token, `/repos/${repo}/contents/${f.path}?ref=${encodeURIComponent(branch)}`);
    const text = new TextDecoder().decode(Uint8Array.from(atob(c.content.replace(/\n/g, '')), (ch) => ch.charCodeAt(0)));
    workflows.push(scanWorkflow(f.name, text));
  }
  const score = (w: CiWorkflow) => w.jobs.filter((j) => required.has(j)).length * 10 + (w.dispatchable ? 2 : 0) + (w.onPullRequest ? 1 : 0);
  workflows.sort((a, b) => score(b) - score(a));
  return { required: [...required], workflows: workflows.filter((w) => w.jobs.length) };
}

/** Branch names of a repository (first 300), default branch first. */
export async function listBranches(token: string, repo: string): Promise<{ names: string[]; defaultBranch: string }> {
  const meta = await gh<{ default_branch: string }>(token, `/repos/${repo}`);
  const names: string[] = [];
  for (let page = 1; page <= 3; page++) {
    const r = await gh<{ name: string }[]>(token, `/repos/${repo}/branches?per_page=100&page=${page}`);
    names.push(...r.map((b) => b.name));
    if (r.length < 100) break;
  }
  return { names: [meta.default_branch, ...names.filter((n) => n !== meta.default_branch)], defaultBranch: meta.default_branch };
}

// ---- Repository secrets (encrypted in the browser with the repo public key) ----

/**
 * Encrypts a secret for GitHub Actions: a libsodium-compatible sealed box (crypto_box_seal) built from
 * tweetnacl + blake2b — ephemeral X25519 key, nonce = blake2b(epk || pk, 24), output = epk || box.
 */
export async function sealSecret(publicKeyB64: string, value: string): Promise<string> {
  const [{ default: nacl }, { blake2b }] = await Promise.all([import('tweetnacl'), import('blakejs')]);
  const pk = Uint8Array.from(atob(publicKeyB64), (c) => c.charCodeAt(0));
  const eph = nacl.box.keyPair();
  const nonceIn = new Uint8Array(64);
  nonceIn.set(eph.publicKey, 0);
  nonceIn.set(pk, 32);
  const nonce = blake2b(nonceIn, undefined, 24);
  const box = nacl.box(new TextEncoder().encode(value), nonce, pk, eph.secretKey);
  const out = new Uint8Array(32 + box.length);
  out.set(eph.publicKey, 0);
  out.set(box, 32);
  let bin = '';
  out.forEach((b) => (bin += String.fromCharCode(b)));
  return btoa(bin);
}

/** Creates or updates repository Actions secrets. Values never leave the page unencrypted. */
export async function putSecrets(token: string, repo: string, secrets: Record<string, string>, log: (m: string) => void = () => {}) {
  const entries = Object.entries(secrets).filter(([, v]) => v);
  if (!entries.length) return;
  const pk = await gh<{ key_id: string; key: string }>(token, `/repos/${repo}/actions/secrets/public-key`);
  for (const [name, value] of entries) {
    const encrypted_value = await sealSecret(pk.key, value);
    await ghRaw(token, `/repos/${repo}/actions/secrets/${name}`, {
      method: 'PUT',
      body: JSON.stringify({ encrypted_value, key_id: pk.key_id }),
    });
    log(`secret ${name} set`);
  }
}

/** Names of secrets that already exist (values are never readable). */
export async function listSecretNames(token: string, repo: string): Promise<string[]> {
  const r = await gh<{ secrets: { name: string }[] }>(token, `/repos/${repo}/actions/secrets?per_page=100`);
  return r.secrets.map((s) => s.name);
}

/** Every secret the generated factory reads, with a human hint. */
export function requiredSecrets(
  c: FactoryConfig,
  scm: ScmId = 'github',
  gitlabBaseUrl = 'https://gitlab.com',
): { name: string; hint: string; url?: string; multiline?: boolean }[] {
  const out: { name: string; hint: string; url?: string; multiline?: boolean }[] = scm === 'gitlab'
    ? [{
        name: 'GITLAB_TOKEN',
        hint: `GitLab personal access token for ${c.machine || 'the AI teammate account'} with api and write_repository scopes. ` +
          `Its user must have Developer or higher access to ${c.repo}.`,
        url: TOKEN_LINKS.setupGitlab(gitlabBaseUrl),
      }]
    : [{
        name: 'SOURCE_GITHUB_TOKEN',
        hint: `Classic PAT created while logged in as ${c.machine || 'the AI teammate account'} — scopes repo + workflow (workflow lets agents edit .github/workflows). The account needs write access to the repository.`,
        url: TOKEN_LINKS.agent,
      }];
  const seen = new Set<string>();
  for (const e of allEntries(c)) {
    if (seen.has(e.keyEnv)) continue;
    seen.add(e.keyEnv);
    const p = PROVIDER_PRESETS.find((x) => x.id === e.preset)!;
    out.push({ name: e.keyEnv, hint: `${p.label}: ${p.keyHelp}`, url: p.keyUrl });
  }
  if (scm === 'github' && c.merge.mode === 'token') out.push({ name: c.merge.tokenSecret, hint: 'Classic PAT of the merge account — repo scope; the account must be allowed to merge into the protected branch.', url: TOKEN_LINKS.merge });
  if (scm === 'github' && c.merge.mode === 'app') {
    out.push({ name: 'MERGE_APP_ID', hint: 'GitHub App ID (numeric, App settings → General). Install the App on this repository first.', url: TOKEN_LINKS.app(c.repo) });
    out.push({ name: 'MERGE_APP_PRIVATE_KEY', hint: 'GitHub App private key — paste the whole .pem file.', multiline: true });
  }
  return out;
}

/** Creates a repository variable, or updates it when it already exists. */
export async function putVariable(token: string, repo: string, name: string, value: string) {
  try {
    await ghRaw(token, `/repos/${repo}/actions/variables/${name}`, { method: 'PATCH', body: JSON.stringify({ name, value }) });
  } catch {
    await ghRaw(token, `/repos/${repo}/actions/variables`, { method: 'POST', body: JSON.stringify({ name, value }) });
  }
}

async function ghRaw(token: string, path: string, init: RequestInit): Promise<void> {
  const res = await fetch(`https://api.github.com${path}`, {
    ...init,
    headers: { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json', 'Content-Type': 'application/json' },
  });
  if (!res.ok) throw new Error(`GitHub ${res.status} on ${path}: ${(await res.text()).slice(0, 200)}`);
}

/** Token-creation links with exactly the permissions each credential needs. */
export const TOKEN_LINKS = {
  /** Setup token used by THIS page: commit + PR (repo), workflow files (workflow); secrets need repo-admin rights. */
  setup: 'https://github.com/settings/tokens/new?scopes=repo,workflow&description=DMTools%20factory%20setup',
  setupGitlab: (baseUrl = 'https://gitlab.com') =>
    `${normalizeGitlabBaseUrl(baseUrl)}/-/user_settings/personal_access_tokens`,
  /** SOURCE_GITHUB_TOKEN of the AI teammate: push branches incl. workflow edits, labels, PRs, issues, dispatch. */
  agent: 'https://github.com/settings/tokens/new?scopes=repo,workflow&description=DMTools%20factory%20agent',
  /** Separate merge PAT: merge PRs + labels. */
  merge: 'https://github.com/settings/tokens/new?scopes=repo&description=DMTools%20merge%20bot',
  /** Merge GitHub App with the permissions the merge bot uses. */
  app: (repo: string) =>
    `https://github.com/settings/apps/new?name=${encodeURIComponent(`DMTools merge ${repo.split('/')[1] ?? ''}`.trim())}` +
    '&url=https%3A%2F%2Fdmtools.lab.epam.com&public=false&webhook_active=false' +
    '&contents=write&pull_requests=write&issues=write&checks=read&actions=read&statuses=read&metadata=read',
};

export interface TokenCheck {
  login: string;
  kind: 'classic' | 'fine-grained' | 'personal access' | 'unknown';
  scopes: string[]; // classic only
  missingScopes: string[];
  admin: boolean | null; // on the selected repo
  push: boolean | null;
}

/** Validates a GitLab personal access token and returns the authenticated user. */
export async function checkGitlabToken(token: string, baseUrl = 'https://gitlab.com'): Promise<TokenCheck> {
  const apiUrl = `${normalizeGitlabBaseUrl(baseUrl)}/api/v4`;
  const res = await fetch(`${apiUrl}/user`, {
    headers: { 'PRIVATE-TOKEN': token, Accept: 'application/json' },
  });
  if (!res.ok) {
    throw new Error(
      res.status === 401
        ? 'GitLab token rejected (401) — expired, mistyped, or missing API access'
        : `GitLab ${res.status} on /user`,
    );
  }
  const user = (await res.json()) as { username: string };
  return { login: user.username, kind: 'personal access', scopes: [], missingScopes: [], admin: null, push: null };
}

/** Inspects the token: classic scopes from X-OAuth-Scopes, and the user's permissions on the repo. */
export async function checkToken(token: string, repo?: string): Promise<TokenCheck> {
  const res = await fetch('https://api.github.com/user', {
    headers: { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json' },
  });
  if (!res.ok) throw new Error(res.status === 401 ? 'Token rejected (401) — expired or mistyped' : `GitHub ${res.status} on /user`);
  const user = (await res.json()) as { login: string };
  const header = res.headers.get('x-oauth-scopes');
  const scopes = header ? header.split(',').map((x) => x.trim()).filter(Boolean) : [];
  const kind = token.startsWith('github_pat_') ? 'fine-grained' : header !== null ? 'classic' : 'unknown';
  const missingScopes = kind === 'classic' ? ['repo', 'workflow'].filter((x) => !scopes.includes(x)) : [];
  let admin: boolean | null = null;
  let push: boolean | null = null;
  if (repo && /^[\w.-]+\/[\w.-]+$/.test(repo)) {
    try {
      const r = await gh<{ permissions?: { admin?: boolean; push?: boolean } }>(token, `/repos/${repo}`);
      admin = !!r.permissions?.admin;
      push = !!r.permissions?.push;
    } catch { /* no access */ }
  }
  return { login: user.login, kind, scopes, missingScopes, admin, push };
}
