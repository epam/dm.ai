import aiTeammate from './factory-templates/ai-teammate.yml?raw';
import machineSm from './factory-templates/machine-sm.yml?raw';
import machineMerge from './factory-templates/machine-merge.yml?raw';
import mergeTrigger from './factory-templates/merge-trigger.yml?raw';
import smKicker from './factory-templates/sm-kicker.yml?raw';
import dmtoolsConfig from './factory-templates/config.js?raw';
import runnerBug from './factory-templates/runner-bug.json?raw';
import runnerStory from './factory-templates/runner-story.json?raw';
import runnerRework from './factory-templates/runner-rework.json?raw';
import runnerReview from './factory-templates/runner-review.json?raw';

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

export interface FactoryConfig {
  repo: string; // owner/name
  createRepo?: boolean; // Greenfield: create the repository before opening the factory PR
  repoPrivate?: boolean; // Greenfield repository visibility (default true = private)
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
export const SCM: ScmOption[] = [
  { id: 'github', label: 'GitHub', available: true },
  { id: 'gitlab', label: 'GitLab (coming soon)', available: false },
];

export interface FactoryFile {
  path: string;
  content: string;
}

export const defaults: FactoryConfig = {
  repo: '',
  createRepo: false,
  repoPrivate: true,
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
};

const LOGIN = /^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$/;
const REPO = /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/;

export function isCiEnabled(c: FactoryConfig): boolean {
  return c.ciEnabled !== false;
}

export function validate(c: FactoryConfig): Record<string, string> {
  const e: Record<string, string> = {};
  if (!REPO.test(c.repo)) e.repo = 'Use owner/name';
  if (!LOGIN.test(c.machine)) e.machine = 'Not a valid GitHub login';
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

function runnerJson(tpl: string, c: FactoryConfig, review: boolean): string {
  const json = JSON.parse(tpl);
  const list = review && c.providers.reviewSeparate ? c.providers.reviewEntries : c.providers.entries;
  if (json.params.customParams?.targetRepository) json.params.customParams.targetRepository.baseBranch = c.baseBranch;
  json.params.envVariables = { ...(json.params.envVariables.DEFAULT_TRACKER ? { DEFAULT_TRACKER: 'github' } : {}), ...providerEnv(list, c.providers.mode) };
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
  return tpl
    .replaceAll('@@MACHINE@@', c.machine)
    .replaceAll('@@CI_NAME@@', ciName)
    .replaceAll('@@CI_FILE@@', ciFile)
    .replaceAll('@@CHECKS_JSON@@', JSON.stringify(checks))
    .replaceAll('@@CHECKS_CSV@@', checks.join(','))
    .replaceAll('@@RUNNER@@', c.runner)
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

export function renderFiles(c: FactoryConfig): FactoryFile[] {
  const wf = (name: string, body: string) => ({ path: `.github/workflows/${name}`, content: fill(body, c) });
  const quality = renderQualityWorkflow(c);
  return [
    ...(quality ? [{ path: `.github/workflows/${c.ciFile || 'quality.yml'}`, content: quality }] : []),
    wf('ai-teammate.yml', aiTeammate),
    wf('machine-sm.yml', machineSm),
    wf('machine-merge.yml', machineMerge),
    wf('merge-trigger.yml', mergeTrigger),
    wf('sm-kicker.yml', smKicker),
    { path: '.dmtools/config.js', content: fill(dmtoolsConfig, c) },
    { path: '.dmtools/runners/fa-bug-dev.json', content: runnerJson(runnerBug, c, false) },
    { path: '.dmtools/runners/fa-story-dev.json', content: runnerJson(runnerStory, c, false) },
    { path: '.dmtools/runners/fa-rework.json', content: runnerJson(runnerRework, c, false) },
    { path: '.dmtools/runners/fa-review.json', content: runnerJson(runnerReview, c, true) },
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
- [ ] Labels: agent:dev, agent:review, agent:rework, agent:skip, ai_validating, ai_validated, ai_pr_reviewed, pr_approved, blocked
${ciChecklist(c)}- [ ] Optional repo variables \`AGENTS_VERSION\` / \`DMTOOLS_VERSION\` (default \`latest\`) — pin agent packs / CLI to a release tag

Smoke test: assign an issue to \`${c.machine}\` or dispatch **AI Teammate** with leg=dev.`;
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

export interface RepoSummary { full_name: string; private: boolean; permissions?: { push?: boolean } }

/** Repositories the token can push to, most recently pushed first (owner/org filter optional). */
export async function listRepos(token: string): Promise<RepoSummary[]> {
  const out: RepoSummary[] = [];
  for (let page = 1; page <= 5; page++) {
    const r = await gh<RepoSummary[]>(token, `/user/repos?per_page=100&sort=pushed&page=${page}&affiliation=owner,collaborator,organization_member`);
    out.push(...r.filter((x) => x.permissions?.push));
    if (r.length < 100) break;
  }
  return out;
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
export function requiredSecrets(c: FactoryConfig): { name: string; hint: string; url?: string; multiline?: boolean }[] {
  const out: { name: string; hint: string; url?: string; multiline?: boolean }[] = [
    { name: 'SOURCE_GITHUB_TOKEN', hint: `Classic PAT created while logged in as ${c.machine || 'the AI teammate account'} — scopes repo + workflow (workflow lets agents edit .github/workflows). The account needs write access to the repository.`,
      url: TOKEN_LINKS.agent },
  ];
  const seen = new Set<string>();
  for (const e of allEntries(c)) {
    if (seen.has(e.keyEnv)) continue;
    seen.add(e.keyEnv);
    const p = PROVIDER_PRESETS.find((x) => x.id === e.preset)!;
    out.push({ name: e.keyEnv, hint: `${p.label}: ${p.keyHelp}`, url: p.keyUrl });
  }
  if (c.merge.mode === 'token') out.push({ name: c.merge.tokenSecret, hint: 'Classic PAT of the merge account — repo scope; the account must be allowed to merge into the target branch.', url: TOKEN_LINKS.merge });
  if (c.merge.mode === 'app') {
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
  kind: 'classic' | 'fine-grained' | 'unknown';
  scopes: string[]; // classic only
  missingScopes: string[];
  admin: boolean | null; // on the selected repo
  push: boolean | null;
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
