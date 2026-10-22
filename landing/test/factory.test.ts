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
  QUALITY_PRESETS,
  TOOLCHAINS,
  isCiEnabled,
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
