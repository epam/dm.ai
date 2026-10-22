import { useEffect, useMemo, useState } from 'react';
import {
  PROVIDER_PRESETS, QUALITY_PRESETS, RUNNERS, SCM, TOOLCHAINS, keySlots, newEntry, createFactoryPr, createRepo, defaults, detectCi, isCiEnabled, TOKEN_LINKS, checkToken, listBranches, listRepos, listSecretNames, putSecrets, requiredSecrets, renderFiles, validate,
  type CiDetection, type TokenCheck, type CiWorkflow, type FactoryConfig, type ProviderEntry, type QualityPresetId, type RepoSummary, type ToolchainId,
} from '../lib/factory';

const CUSTOM = '__custom__';

const LABELS: Record<string, string> = {
  repo: 'Repository', machine: 'AI teammate account', ciFile: 'CI workflow file', ciName: 'CI workflow name',
  checks: 'Checks', baseBranch: 'Target branch', merge: 'Merge identity', runner: 'Runner', providers: 'AI provider',
};

/** GitHub matches check contexts case-insensitively. */
const same = (a: string, b: string) => a.toLowerCase() === b.toLowerCase();
const uniqCi = (xs: string[]) => xs.filter((x, i) => xs.findIndex((y) => same(x, y)) === i);

function ProviderRow({ entry, onChange, onRemove }: { entry: ProviderEntry; onChange: (e: ProviderEntry) => void; onRemove?: () => void }) {
  const preset = PROVIDER_PRESETS.find((p) => p.id === entry.preset)!;
  const pick = (id: string) => onChange(newEntry(id));
  const listId = `models-${entry.preset}`;
  return (
    <div className="factory__provider">
      <select aria-label="Provider" value={entry.preset} onChange={(e) => pick(e.target.value)}>
        {PROVIDER_PRESETS.map((p) => <option key={p.id} value={p.id}>{p.label}</option>)}
      </select>
      <input aria-label="Model" list={listId} value={entry.model} placeholder="model id"
        onChange={(e) => onChange({ ...entry, model: e.target.value.trim() })} />
      <datalist id={listId}>{preset.models.map((m) => <option key={m} value={m} />)}</datalist>
      <input aria-label="API key secret name" title="Name of the repository secret that will hold this provider's API key" value={entry.keyEnv} placeholder="KEY_ENV_NAME"
        onChange={(e) => onChange({ ...entry, keyEnv: e.target.value.trim().toUpperCase() })} />
      {(['custom', 'dial', 'openrouter', 'codemie'].includes(entry.preset)) && (
        <input aria-label="Base URL" className="factory__wide" value={entry.baseUrl} placeholder="https://… or http://…"
          onChange={(e) => onChange({ ...entry, baseUrl: e.target.value.trim() })} />
      )}
      <p className="factory__keyhelp factory__wide">
        <strong>Secret <code>{entry.keyEnv || 'KEY_NAME'}</code></strong> — the NAME of a repository secret, not the key itself.{' '}
        {preset.keyHelp}{' '}
        {preset.keyUrl && <a href={preset.keyUrl} target="_blank" rel="noopener">Get a key ↗</a>}
        <br />After the PR is created: repository → Settings → Secrets and variables → Actions → New repository secret → name <code>{entry.keyEnv || 'KEY_NAME'}</code>.
      </p>
      {onRemove && <button type="button" className="factory__x" aria-label="Remove provider" onClick={onRemove}>×</button>}
    </div>
  );
}

function ProviderList({ list, mode, onChange }: { list: ProviderEntry[]; mode: 'default' | 'queue'; onChange: (l: ProviderEntry[]) => void }) {
  return (
    <div className="factory__providers">
      {list.map((e, i) => (
        <div key={i} className="factory__queueRow">
          {mode === 'queue' && <span className="factory__rank">{i + 1}</span>}
          <ProviderRow entry={e} onChange={(n) => onChange(list.map((x, j) => (j === i ? n : x)))}
            onRemove={mode === 'queue' && list.length > 1 ? () => onChange(list.filter((_, j) => j !== i)) : undefined} />
        </div>
      ))}
      {mode === 'queue' && (
        <button type="button" className="btn btn--ghost factory__mini" onClick={() => onChange([...list, newEntry('openai')])}>+ Add fallback provider</button>
      )}
    </div>
  );
}

export default function FactorySetup() {
  const [cfg, setCfg] = useState<FactoryConfig>(defaults);
  const [token, setToken] = useState('');
  const [repos, setRepos] = useState<RepoSummary[]>([]);
  const [comboOpen, setComboOpen] = useState(false);
  const [active, setActive] = useState(0);
  const [tokenInfo, setTokenInfo] = useState<TokenCheck | null>(null);
  const [tokenErr, setTokenErr] = useState('');
  const [secretVals, setSecretVals] = useState<Record<string, string>>({});
  const [existing, setExisting] = useState<string[]>([]);
  const [branches, setBranches] = useState<string[]>([]);
  const [ci, setCi] = useState<CiDetection | null>(null);
  const [gatesBusy, setGatesBusy] = useState(false);
  const [runnerMode, setRunnerMode] = useState(defaults.runner);
  const [busy, setBusy] = useState(false);
  const [lines, setLines] = useState<string[]>([]);
  const [prUrl, setPrUrl] = useState('');
  const [error, setError] = useState('');
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const [open, setOpen] = useState(0);

  const errors = useMemo(() => validate(cfg), [cfg]);
  const files = useMemo(() => (Object.keys(errors).length ? [] : renderFiles(cfg)), [cfg, errors]);
  const set = <K extends keyof FactoryConfig>(k: K, v: FactoryConfig[K]) => setCfg((c) => ({ ...c, [k]: v }));
  const touch = (k: string) => setTouched((t) => ({ ...t, [k]: true }));
  const show = (k: string) => (touched[k] ? errors[k] : undefined);

  useEffect(() => {
    setRepos([]);
    if (token.length < 20) return;
    const t = setTimeout(() => {
      listRepos(token).then(setRepos).catch((e) => setError((e as Error).message));
    }, 500);
    return () => clearTimeout(t);
  }, [token]);

  useEffect(() => {
    setTokenInfo(null); setTokenErr('');
    if (token.length < 20) return;
    const t = setTimeout(() => {
      checkToken(token, errors.repo || cfg.createRepo ? undefined : cfg.repo).then(setTokenInfo).catch((e) => setTokenErr((e as Error).message));
    }, 400);
    return () => clearTimeout(t);
  }, [token, cfg.repo, cfg.createRepo]);

  useEffect(() => {
    if (!cfg.repo || errors.repo || token.length < 20 || cfg.createRepo) {
      if (cfg.createRepo) {
        setBranches([]);
        setExisting([]);
        setCi(null);
      }
      return;
    }
    setBranches([]);
    setExisting([]);
    listSecretNames(token, cfg.repo).then(setExisting).catch(() => {});
    listBranches(token, cfg.repo)
      .then((b) => { setBranches(b.names); set('baseBranch', b.names.includes('main') ? 'main' : b.defaultBranch); })
      .catch(() => {});
    setCi(null);
    setGatesBusy(true);
    detectCi(token, cfg.repo)
      .then((d) => { setCi(d); if (d.workflows[0] && isCiEnabled(cfg) && (!cfg.qualityPreset || cfg.qualityPreset === 'none')) applyWorkflow(d.workflows[0], d.required); })
      .catch(() => setCi({ required: [], workflows: [] }))
      .finally(() => setGatesBusy(false));
  }, [cfg.repo, token, cfg.createRepo]);

  const filtered = useMemo(() => {
    const q = cfg.repo.toLowerCase();
    if (repos.some((r) => r.full_name.toLowerCase() === q)) return [];
    return repos.filter((r) => r.full_name.toLowerCase().includes(q)).slice(0, 50);
  }, [repos, cfg.repo]);

  function applyWorkflow(w: CiWorkflow, required: string[]) {
    // Check-context matching is case-insensitive; keep the spelling branch protection uses.
    const req = required.filter((r) => w.jobs.some((j) => same(j, r)));
    // No protection rules: prefer jobs that look like aggregate gates, never matrix-expanded names.
    const gate = w.jobs.filter((j) => !j.includes('${{') && /gate|unit tests|^test$/i.test(j));
    setCfg((c) => ({ ...c, ciEnabled: true, qualityPreset: 'none', ciFile: w.file, ciName: w.name, checks: req.length ? req : gate }));
  }

  function selectQualityPreset(presetId: QualityPresetId) {
    setCfg((c) => {
      if (presetId === 'none') {
        return { ...c, ciEnabled: true, qualityPreset: 'none' };
      }
      const preset = QUALITY_PRESETS.find((p) => p.id === presetId);
      const nextTools = { ...c.toolchains };
      if (preset?.toolchain && nextTools[preset.toolchain] === undefined) {
        const tc = TOOLCHAINS.find((t) => t.id === preset.toolchain);
        if (tc) nextTools[preset.toolchain] = tc.fallback;
      }
      return {
        ...c,
        ciEnabled: true,
        qualityPreset: presetId,
        ciFile: 'quality.yml',
        ciName: 'Quality',
        checks: ['quality'],
        toolchains: nextTools,
      };
    });
  }

  function toggleCheck(name: string, on: boolean) {
    setCfg((c) => {
      const rest = c.checks.filter((x) => !same(x, name));
      return { ...c, checks: on ? [...rest, name] : rest };
    });
  }

  function chooseRepo(name: string) {
    set('repo', name);
    setComboOpen(false);
    touch('repo');
  }

  function toggleTool(id: ToolchainId, on: boolean) {
    setCfg((c) => {
      const next = { ...c.toolchains };
      if (on) next[id] = TOOLCHAINS.find((t) => t.id === id)!.fallback;
      else delete next[id];
      return { ...c, toolchains: next };
    });
  }

  async function submit() {
    setBusy(true); setError(''); setLines([]);
    const log = (m: string) => setLines((l) => [...l, m]);
    try {
      let targetCfg = cfg;
      if (cfg.createRepo) {
        const created = await createRepo(token, cfg.repo, cfg.repoPrivate !== false, tokenInfo?.login, log);
        targetCfg = { ...cfg, repo: created.full_name, baseBranch: cfg.baseBranch || created.default_branch };
      }
      const wanted = Object.fromEntries(requiredSecrets(targetCfg).map((x) => [x.name, secretVals[x.name] ?? '']));
      if (!cfg.createRepo && Object.values(wanted).some(Boolean) && tokenInfo?.admin === false) {
        throw new Error(`Writing secrets needs admin rights on ${targetCfg.repo} — clear the secret fields or use an admin token.`);
      }
      await putSecrets(token, targetCfg.repo, wanted, log);
      setSecretVals({});
      setPrUrl(await createFactoryPr(token, targetCfg, log));
    }
    catch (e) { setError((e as Error).message); }
    finally { setBusy(false); }
  }

  const canSubmit = !busy && token.length > 20 && Object.keys(errors).length === 0
    && !(tokenInfo?.missingScopes.length) && (cfg.createRepo || tokenInfo?.push !== false);

  const ciMode: 'existing' | 'crap4' | 'none' = !isCiEnabled(cfg)
    ? 'none'
    : (cfg.qualityPreset && cfg.qualityPreset !== 'none' ? 'crap4' : 'existing');

  return (
    <div className="factory">
      <div className="factory__form">
        <div className="factory__field">
          <label htmlFor="f-scm">Source control</label>
          <select id="f-scm" defaultValue="github">
            {SCM.map((s) => <option key={s.id} value={s.id} disabled={!s.available}>{s.label}</option>)}
          </select>
        </div>

        <div className="factory__field">
          <label htmlFor="f-token">GitHub token</label>
          <input id="f-token" type="password" autoComplete="off" spellCheck={false} value={token}
            onChange={(e) => setToken(e.target.value.trim())} placeholder="ghp_… or github_pat_…" />
          <a className="btn btn--ghost factory__mini" target="_blank" rel="noopener"
            href={TOKEN_LINKS.setup}>
            Create token on GitHub ↗
          </a>
          {tokenErr && <small className="is-error">{tokenErr}</small>}
          {tokenInfo && (
            <ul className="factory__tokencheck">
              <li className="is-ok">Signed in as <strong>{tokenInfo.login}</strong> ({tokenInfo.kind} token)</li>
              {tokenInfo.kind === 'classic' && (tokenInfo.missingScopes.length
                ? <li className="is-bad">Missing scopes: {tokenInfo.missingScopes.join(', ')} — regenerate with the button below</li>
                : <li className="is-ok">Scopes: repo, workflow</li>)}
              {tokenInfo.kind === 'fine-grained' && <li className="is-warn">Fine-grained token: needs Contents, Pull requests, Workflows: write and Secrets: write on the repository — scopes cannot be verified, use a classic token if unsure</li>}
              {!cfg.createRepo && tokenInfo.push === false && <li className="is-bad">No write access to {cfg.repo}</li>}
              {!cfg.createRepo && tokenInfo.push && (tokenInfo.admin
                ? <li className="is-ok">Admin on {cfg.repo} — can create the PR and write secrets</li>
                : <li className="is-warn">Write but not admin on {cfg.repo} — the PR works, secrets must be added by an admin</li>)}
            </ul>
          )}
          <small>Opens GitHub with exactly <code>repo</code> (commit, branch, PR, secrets) + <code>workflow</code> (files under .github/workflows) pre-selected — set a short expiry, generate, paste it here.
            The token stays in this tab and goes only to api.github.com — never stored or logged. Revoke it after the PR is created.</small>
        </div>

        <div className="factory__field">
          <label htmlFor="f-repo">Repository</label>
          <div className="factory__seg" role="radiogroup" aria-label="Repository mode">
            {([
              [false, 'Existing repository'],
              [true, 'Create new (Greenfield)'],
            ] as const).map(([isNew, label]) => (
              <button key={String(isNew)} type="button" role="radio" aria-checked={Boolean(cfg.createRepo) === isNew}
                className={Boolean(cfg.createRepo) === isNew ? 'is-on' : ''}
                onClick={() => {
                  setCfg((c) => ({
                    ...c,
                    createRepo: isNew,
                    repo: isNew && !c.repo && tokenInfo?.login ? `${tokenInfo.login}/` : c.repo,
                  }));
                }}>
                {label}
              </button>
            ))}
          </div>
          <div className="factory__combo">
            <input id="f-repo" role="combobox" aria-expanded={!cfg.createRepo && comboOpen && filtered.length > 0} aria-controls="f-repo-list"
              aria-autocomplete="list" autoComplete="off" value={cfg.repo} aria-invalid={!!show('repo')}
              placeholder={cfg.createRepo ? `${tokenInfo?.login || 'owner'}/new-repo-name` : (repos.length ? 'Start typing to search your repositories…' : 'owner/name')}
              onFocus={() => setComboOpen(true)}
              onBlur={() => { touch('repo'); setTimeout(() => setComboOpen(false), 120); }}
              onChange={(e) => { set('repo', e.target.value.trim()); setComboOpen(true); setActive(0); }}
              onKeyDown={(e) => {
                if (cfg.createRepo || !comboOpen || !filtered.length) return;
                if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => Math.min(a + 1, filtered.length - 1)); }
                else if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)); }
                else if (e.key === 'Enter') { e.preventDefault(); chooseRepo(filtered[active].full_name); }
                else if (e.key === 'Escape') setComboOpen(false);
              }} />
            {!cfg.createRepo && comboOpen && filtered.length > 0 && (
              <ul id="f-repo-list" role="listbox" className="factory__options">
                {filtered.map((r, i) => (
                  <li key={r.full_name} role="option" aria-selected={i === active}
                    className={i === active ? 'is-active' : ''}
                    onMouseEnter={() => setActive(i)}
                    onMouseDown={(e) => { e.preventDefault(); chooseRepo(r.full_name); }}>
                    {r.full_name}{r.private ? ' 🔒' : ''}
                  </li>
                ))}
              </ul>
            )}
          </div>
          {cfg.createRepo && (
            <div className="factory__seg" role="radiogroup" aria-label="New repository visibility">
              {([
                [true, 'Private 🔒'],
                [false, 'Public'],
              ] as const).map(([priv, label]) => (
                <button key={String(priv)} type="button" role="radio" aria-checked={(cfg.repoPrivate !== false) === priv}
                  className={(cfg.repoPrivate !== false) === priv ? 'is-on' : ''}
                  onClick={() => set('repoPrivate', priv)}>
                  {label}
                </button>
              ))}
            </div>
          )}
          <small className={show('repo') ? 'is-error' : ''}>
            {show('repo') ?? (cfg.createRepo
              ? 'Creates the repository on GitHub (with an initial commit on main) under your account or organization before opening the factory PR'
              : (repos.length ? `${repos.length} repositories you can push to` : 'Enter a token to pick from your repositories, or type owner/name'))}
          </small>
        </div>

        <div className="factory__field">
          <label htmlFor="f-base">Target branch</label>
          {branches.length > 0 && !cfg.createRepo ? (
            <select id="f-base" value={cfg.baseBranch} onChange={(e) => set('baseBranch', e.target.value)}>
              {branches.map((b) => <option key={b} value={b}>{b}</option>)}
            </select>
          ) : (
            <input id="f-base" value={cfg.baseBranch} aria-invalid={!!errors.baseBranch}
              onChange={(e) => set('baseBranch', e.target.value.trim())} />
          )}
          <small className={errors.baseBranch ? 'is-error' : ''}>{errors.baseBranch ?? 'Agents branch from it and open their PRs against it; the setup PR targets it too'}</small>
        </div>

        <div className="factory__field">
          <label htmlFor="f-machine">AI teammate account</label>
          <input id="f-machine" value={cfg.machine} aria-invalid={!!show('machine')}
            onBlur={() => touch('machine')} onChange={(e) => set('machine', e.target.value.trim())} />
          <small className={show('machine') ? 'is-error' : ''}>{show('machine') ?? 'GitHub login that issues are assigned to'}</small>
        </div>

        <fieldset className="factory__group">
          <legend>CI &amp; quality checks</legend>
          <div className="factory__seg" role="radiogroup" aria-label="CI mode">
            {([
              ['existing', 'Existing CI'],
              ['crap4', 'Generate crap4* quality gate'],
              ['none', 'No CI workflow'],
            ] as const).map(([m, label]) => (
              <button key={m} type="button" role="radio" aria-checked={ciMode === m}
                className={ciMode === m ? 'is-on' : ''}
                onClick={() => {
                  if (m === 'none') {
                    setCfg((c) => ({ ...c, ciEnabled: false, qualityPreset: 'none' }));
                  } else if (m === 'crap4') {
                    selectQualityPreset(cfg.qualityPreset && cfg.qualityPreset !== 'none' ? cfg.qualityPreset : 'crap4js');
                  } else {
                    setCfg((c) => ({
                      ...c,
                      ciEnabled: true,
                      qualityPreset: 'none',
                      ciFile: c.ciFile || 'ci.yml',
                      ciName: c.ciName || 'CI',
                      checks: c.checks.length ? c.checks : ['test'],
                    }));
                  }
                }}>
                {label}
              </button>
            ))}
          </div>

          {ciMode === 'none' && (
            <small>
              No CI gate: the factory will not dispatch or wait for a quality workflow before merging approved PRs. You can add a CI workflow later.
            </small>
          )}

          {ciMode === 'crap4' && (
            <>
              <small>
                Generates a ready-to-use <code>.github/workflows/quality.yml</code> powered by the <strong>crap4*</strong> CRAP metric (Change Risk Anti-Patterns = cyclomatic complexity + test coverage) for your tech stack.
              </small>
              <div className="factory__field">
                <label htmlFor="f-quality-preset">Tech stack preset (crap4*)</label>
                <select id="f-quality-preset" value={cfg.qualityPreset && cfg.qualityPreset !== 'none' ? cfg.qualityPreset : 'crap4js'}
                  onChange={(e) => selectQualityPreset(e.target.value as QualityPresetId)}>
                  {QUALITY_PRESETS.map((p) => (
                    <option key={p.id} value={p.id}>{p.label}</option>
                  ))}
                </select>
                {(() => {
                  const p = QUALITY_PRESETS.find((x) => x.id === cfg.qualityPreset);
                  return p ? (
                    <small>
                      {p.description} — <a href={p.repoUrl} target="_blank" rel="noopener">{p.repoUrl.replace('https://github.com/', '')} ↗</a>
                    </small>
                  ) : null;
                })()}
              </div>
            </>
          )}

          {ciMode === 'existing' && (
            <>
              <small>
                The factory does not wait for push/PR-triggered CI: its state machine <strong>dispatches</strong> the CI workflow
                on the PR head when validation is due and merges once the checks below are green — so the workflow needs a
                <code> workflow_dispatch</code> trigger.
              </small>
              {gatesBusy && <small>Reading branch protection and workflows…</small>}
              {ci && ci.workflows.length === 0 && <small>No CI workflow found in .github/workflows — fill in the fields manually, generate a crap4* quality gate, or switch to “No CI workflow”.</small>}
              {ci && ci.workflows.length > 0 && (
                <div className="factory__gates" role="radiogroup" aria-label="CI workflow">
                  {ci.workflows.map((w) => {
                    const on = cfg.ciFile === w.file;
                    return (
                      <button key={w.file} type="button" role="radio" aria-checked={on}
                        className={`factory__gate ${on ? 'is-on' : ''}`} onClick={() => applyWorkflow(w, ci.required)}>
                        <span className="factory__gateName">{w.name}<span className="factory__gateFile">{w.file}</span></span>
                        <span className="factory__gateMeta">
                          {w.dispatchable
                            ? <em className="factory__badge">dispatchable</em>
                            : <em className="factory__badge is-warn">add workflow_dispatch</em>}
                          {w.onPullRequest && <span>also runs on pull_request — redundant under the factory</span>}
                        </span>
                      </button>
                    );
                  })}
                </div>
              )}
              {ci && (() => {
                const w = ci.workflows.find((x) => x.file === cfg.ciFile);
                const names = uniqCi([...ci.required, ...cfg.checks, ...(w?.jobs ?? []).filter((j) => !j.includes('${{'))]);
                return names.length > 0 && (
                  <div className="factory__tools">
                    <small>Checks the factory waits for before merging:</small>
                    {names.map((n) => (
                      <label key={n} className="factory__check">
                        <input type="checkbox" checked={cfg.checks.some((c) => same(c, n))} onChange={(e) => toggleCheck(n, e.target.checked)} />
                        <code>{n}</code>
                        {ci.required.some((r) => same(r, n)) && <em className="factory__badge">required</em>}
                        {w && !w.jobs.some((j) => same(j, n)) && <span className="factory__gateMeta">not published by {cfg.ciFile}</span>}
                      </label>
                    ))}
                  </div>
                );
              })()}
            </>
          )}

          {ciMode !== 'none' && (
            <>
              {([
                ['ciFile', 'CI workflow file', 'Dispatched by the factory on PR heads'],
                ['ciName', 'CI workflow name', 'The `name:` of that workflow — its completion wakes the factory'],
              ] as const).map(([k, label, hint]) => (
                <div className="factory__field" key={k}>
                  <label htmlFor={`f-${k}`}>{label}</label>
                  <input id={`f-${k}`} value={cfg[k]} aria-invalid={!!show(k)} onBlur={() => touch(k)}
                    onChange={(e) => set(k, e.target.value)} />
                  <small className={show(k) ? 'is-error' : ''}>{show(k) ?? hint}</small>
                </div>
              ))}
              <div className="factory__field">
                <label htmlFor="f-checks">Checks (comma-separated)</label>
                <input id="f-checks" value={cfg.checks.join(', ')} aria-invalid={!!errors.checks}
                  onChange={(e) => set('checks', uniqCi(e.target.value.split(',').map((x) => x.trim()).filter(Boolean)))} />
                <small className={errors.checks ? 'is-error' : ''}>{errors.checks ?? 'Must match the job names in branch protection'}</small>
              </div>
            </>
          )}
        </fieldset>

        <div className="factory__field">
          <label htmlFor="f-runner">Runner</label>
          <select id="f-runner" value={RUNNERS.includes(runnerMode) ? runnerMode : CUSTOM}
            onChange={(e) => { setRunnerMode(e.target.value); if (e.target.value !== CUSTOM) set('runner', e.target.value); }}>
            {RUNNERS.map((r) => <option key={r} value={r}>{r}</option>)}
            <option value={CUSTOM}>Custom label…</option>
          </select>
          {runnerMode === CUSTOM && (
            <input value={cfg.runner} aria-label="Custom runner label" aria-invalid={!!errors.runner}
              placeholder="self-hosted-label" onChange={(e) => set('runner', e.target.value.trim())} />
          )}
          <small className={errors.runner ? 'is-error' : ''}>{errors.runner ?? 'Where the factory legs run'}</small>
        </div>

        <fieldset className="factory__group">
          <legend>Merge identity</legend>
          <small>Who merges the machine's PRs once the checks are green. A separate identity keeps merges auditable and lets
            branch rules allow only it to merge.</small>
          {([
            ['factory', 'Factory token', 'SOURCE_GITHUB_TOKEN — the same PAT the agents use'],
            ['token', 'Separate token', 'A PAT of another account, stored in its own secret'],
            ['app', 'GitHub App', 'Short-lived installation token minted per run; merges appear as the App and trigger downstream workflows'],
          ] as const).map(([m, label, hint]) => (
            <label key={m} className="factory__check factory__radio">
              <input type="radio" name="merge-mode" checked={cfg.merge.mode === m}
                onChange={() => set('merge', { ...cfg.merge, mode: m })} />
              <span><strong>{label}</strong><br /><small>{hint}</small></span>
            </label>
          ))}
          {cfg.merge.mode === 'token' && (
            <div className="factory__field">
              <label htmlFor="f-merge-secret">Merge token secret name</label>
              <input id="f-merge-secret" value={cfg.merge.tokenSecret} aria-invalid={!!errors.merge}
                onChange={(e) => set('merge', { ...cfg.merge, tokenSecret: e.target.value.trim().toUpperCase() })} />
              <small className={errors.merge ? 'is-error' : ''}>{errors.merge ?? 'Create a classic PAT with the repo scope for the merge account, then add it as this repository secret.'}</small>
              <a className="btn btn--ghost factory__mini" target="_blank" rel="noopener"
                href={TOKEN_LINKS.merge}>Create merge token ↗</a>
            </div>
          )}
          {cfg.merge.mode === 'app' && (
            <p className="factory__keyhelp">
              <strong>Secrets <code>MERGE_APP_ID</code> + <code>MERGE_APP_PRIVATE_KEY</code>.</strong>{' '}
              Create an App (Settings → Developer settings → GitHub Apps → New) with Repository permissions
              Contents, Pull requests, Issues: <em>Read and write</em>, Checks: <em>Read</em>; no webhook. Install it on the
              repository, copy the App ID, generate a private key and paste the whole PEM into the secret.{' '}
              <a href={TOKEN_LINKS.app(cfg.repo)}
                target="_blank" rel="noopener">Create App ↗</a>
            </p>
          )}
        </fieldset>

        <fieldset className="factory__group">
          <legend>AI provider</legend>
          <div className="factory__seg" role="radiogroup" aria-label="Provider mode">
            {(['default', 'queue'] as const).map((m) => (
              <button key={m} type="button" role="radio" aria-checked={cfg.providers.mode === m}
                className={cfg.providers.mode === m ? 'is-on' : ''}
                onClick={() => set('providers', { ...cfg.providers, mode: m, entries: m === 'default' ? cfg.providers.entries.slice(0, 1) : cfg.providers.entries })}>
                {m === 'default' ? 'Default' : 'Queue (failover)'}
              </button>
            ))}
          </div>
          <small>{cfg.providers.mode === 'default'
            ? 'One provider and model for all agent legs.'
            : 'Ordered list: the next provider is used when the previous one is rate-limited or down (FA_PROVIDERS_QUEUE).'}</small>
          <ProviderList list={cfg.providers.entries} mode={cfg.providers.mode}
            onChange={(entries) => set('providers', { ...cfg.providers, entries })} />
          <label className="factory__check">
            <input type="checkbox" checked={cfg.providers.reviewSeparate}
              onChange={(e) => set('providers', { ...cfg.providers, reviewSeparate: e.target.checked })} /> Different provider for PR review
          </label>
          {cfg.providers.reviewSeparate && (
            <ProviderList list={cfg.providers.reviewEntries} mode={cfg.providers.mode}
              onChange={(reviewEntries) => set('providers', { ...cfg.providers, reviewEntries })} />
          )}
          <small className={errors.providers ? 'is-error' : ''}>
            {errors.providers ?? `Add repository secrets: ${keySlots(cfg).join(', ')} — keys are never entered here.`}
          </small>
        </fieldset>

        <fieldset className="factory__group">
          <legend>Frameworks &amp; tools</legend>
          <small>Installed for the agent legs. Nothing is selected by default.</small>
          <div className="factory__tools">
            {TOOLCHAINS.map((t) => {
              const on = cfg.toolchains[t.id] !== undefined;
              return (
                <div key={t.id} className="factory__tool">
                  <label><input type="checkbox" checked={on} onChange={(e) => toggleTool(t.id, e.target.checked)} /> {t.label}</label>
                  {on && (
                    <select aria-label={`${t.label} version`} value={cfg.toolchains[t.id]}
                      onChange={(e) => set('toolchains', { ...cfg.toolchains, [t.id]: e.target.value })}>
                      {t.versions.map((v) => <option key={v} value={v}>{v}</option>)}
                    </select>
                  )}
                </div>
              );
            })}
          </div>
        </fieldset>

        <fieldset className="factory__group">
          <legend>Repository secrets</legend>
          <small>Optional — fill a value and it is stored as an encrypted Actions secret before the PR opens. Values are
            sealed in this browser with the repository public key (libsodium), sent only to api.github.com and never
            readable afterwards. Leave a field empty to add it yourself later.</small>
          {requiredSecrets(cfg).map((x) => (
            <div className="factory__field" key={x.name}>
              <label htmlFor={`s-${x.name}`}>
                <code>{x.name}</code>
                {existing.includes(x.name) && <em className="factory__badge">already set</em>}
              </label>
              {x.multiline ? (
                <textarea id={`s-${x.name}`} rows={4} spellCheck={false} autoComplete="off" value={secretVals[x.name] ?? ''}
                  placeholder={existing.includes(x.name) ? 'leave empty to keep the current value' : '-----BEGIN RSA PRIVATE KEY-----'}
                  onChange={(e) => setSecretVals((v) => ({ ...v, [x.name]: e.target.value }))} />
              ) : (
                <input id={`s-${x.name}`} type="password" spellCheck={false} autoComplete="off" value={secretVals[x.name] ?? ''}
                  placeholder={existing.includes(x.name) ? 'leave empty to keep the current value' : 'paste value'}
                  onChange={(e) => setSecretVals((v) => ({ ...v, [x.name]: e.target.value.trim() }))} />
              )}
              <small>{x.hint} {x.url && <a href={x.url} target="_blank" rel="noopener">Get it ↗</a>}</small>
            </div>
          ))}
          <small>Writing secrets needs a token with admin access to the repository (classic <code>repo</code> scope, or the fine-grained “Secrets: write” permission).</small>
        </fieldset>

        <button type="button" className="btn btn--solid" disabled={!canSubmit} onClick={submit}>
          {busy
            ? 'Working…'
            : cfg.createRepo
              ? (Object.values(secretVals).some(Boolean) ? 'Create repository, set secrets & open PR' : 'Create repository & open pull request')
              : (Object.values(secretVals).some(Boolean) ? 'Set secrets & create pull request' : 'Create pull request')}
        </button>
        {error && <p className="factory__error" role="alert">{error}</p>}
        {lines.length > 0 && <pre className="factory__log">{lines.join('\n')}</pre>}
        {prUrl && <p className="factory__done">Pull request created: <a href={prUrl} target="_blank" rel="noopener">{prUrl}</a></p>}
      </div>

      <div className="factory__preview">
        <p className="eyebrow">Preview — {files.length || '…'} files</p>
        {files.length === 0 && (
          <div className="factory__empty">
            <p>The preview appears once these are set:</p>
            <ul>
              {Object.entries(errors).map(([k, v]) => (
                <li key={k}><strong>{LABELS[k] ?? k}</strong> — {v}</li>
              ))}
            </ul>
          </div>
        )}
        {files.map((f, i) => (
          <div key={f.path} className={`factory__file ${open === i ? 'is-open' : ''}`}>
            <button type="button" onClick={() => setOpen(open === i ? -1 : i)} aria-expanded={open === i}>{f.path}</button>
            {open === i && <pre><code>{f.content}</code></pre>}
          </div>
        ))}
      </div>
    </div>
  );
}
