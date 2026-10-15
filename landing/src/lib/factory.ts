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

export interface FactoryConfig {
  repo: string; // owner/name
  machine: string; // GitHub login of the AI teammate
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

export type ToolchainId = 'java' | 'node' | 'python' | 'flutter';

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
  machine: 'ai-teammate',
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

export function validate(c: FactoryConfig): Record<string, string> {
  const e: Record<string, string> = {};
  if (!REPO.test(c.repo)) e.repo = 'Use owner/name';
  if (!LOGIN.test(c.machine)) e.machine = 'Not a valid GitHub login';
  if (!/^[\w.-]+\.ya?ml$/.test(c.ciFile)) e.ciFile = 'Workflow file name, e.g. ci.yml';
  if (!c.ciName.trim() || /['"\n]/.test(c.ciName)) e.ciName = 'Workflow display name (no quotes)';
  if (!c.checks.length || c.checks.some((x) => !x.trim() || /['",\n]/.test(x))) e.checks = 'At least one check name (no quotes or commas)';
  const entries = allEntries(c);
  if (!entries.length) e.providers = 'Add at least one provider';
  else if (entries.some((x) => !x.model.trim())) e.providers = 'Every provider needs a model';
  else if (entries.some((x) => !/^[A-Z][A-Z0-9_]*$/.test(x.keyEnv))) e.providers = 'Key name: UPPER_SNAKE_CASE';
  else if (entries.some((x) => !/^https:\/\/\S+$/.test(x.baseUrl))) e.providers = 'Base URL must start with https://';
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

const fill = (tpl: string, c: FactoryConfig) =>
  tpl
    .replaceAll('@@MACHINE@@', c.machine)
    .replaceAll('@@CI_NAME@@', c.ciName)
    .replaceAll('@@CI_FILE@@', c.ciFile)
    .replaceAll('@@CHECKS_JSON@@', JSON.stringify(c.checks))
    .replaceAll('@@CHECKS_CSV@@', c.checks.join(','))
    .replaceAll('@@RUNNER@@', c.runner)
    .replace(/^@@TOOLCHAINS@@\n/m, toolchainLines(c) + slotLines(c).inputs)
    .replace(/^@@PROVIDER_SECRETS@@\n/m, slotLines(c).secrets)
    .replaceAll('@@MERGE_TOKEN@@', c.merge.mode === 'token' ? c.merge.tokenSecret : 'SOURCE_GITHUB_TOKEN')
    .replace(/^@@MERGE_APP@@\n/m, c.merge.mode === 'app'
      ? '      MERGE_APP_ID: ${{ secrets.MERGE_APP_ID }}\n      MERGE_APP_PRIVATE_KEY: ${{ secrets.MERGE_APP_PRIVATE_KEY }}\n'
      : '');

function toolchainLines(c: FactoryConfig): string {
  const picked = TOOLCHAINS.filter((t) => c.toolchains[t.id] !== undefined);
  if (!picked.length) return '';
  const head = '      # Toolchains the agent legs install (cached). Unlisted = nothing installed.\n';
  return head + picked.map((t) => `      ${t.input}: '${c.toolchains[t.id] || t.fallback}'\n`).join('');
}

export function renderFiles(c: FactoryConfig): FactoryFile[] {
  const wf = (name: string, body: string) => ({ path: `.github/workflows/${name}`, content: fill(body, c) });
  return [
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

export function prBody(c: FactoryConfig): string {
  return `Sets up the DMTools agent factory for \`${c.repo}\`, generated by https://dmtools.lab.epam.com/factory/.

## Owner checklist
- [ ] Secret \`SOURCE_GITHUB_TOKEN\` (PAT, repo scope)
${mergeChecklist(c)}${keySlots(c).map((k) => `- [ ] Secret \`${k}\` (AI provider key)`).join('\n')}
- [ ] \`${c.machine}\` is a collaborator with write access
- [ ] Labels: agent:dev, agent:review, agent:rework, agent:skip, ai_validating, ai_validated, ai_pr_reviewed, pr_approved, blocked
- [ ] \`${c.ciFile}\` has a \`workflow_dispatch\` trigger (the SM dispatches it on PR heads) and publishes: ${c.checks.map((x) => `\`${x}\``).join(', ')}
- [ ] Optional repo variables \`AGENTS_VERSION\` / \`DMTOOLS_VERSION\` (default \`latest\`) — pin agent packs / CLI to a release tag

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
