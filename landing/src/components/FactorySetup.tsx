import { useEffect, useMemo, useState } from 'react';
import {
  FACTORY_LABELS,
  PROVIDER_PRESETS,
  RUNNERS,
  SCM,
  TOOLCHAINS,
  keySlots,
  newEntry,
  createFactoryPr,
  createRepo,
  defaults,
  detectCi,
  ensureGitlabIssueEventWebhook,
  ensureGitlabLabels,
  ensureLabels,
  isCiEnabled,
  isFaMemoryEnabled,
  isFaUniversalEnabled,
  listLabels,
  listGitlabLabels,
  TOKEN_LINKS,
  checkToken,
  checkGitlabToken,
  normalizeGitlabBaseUrl,
  listBranches,
  listRepos,
  listSecretNames,
  listGitlabVariableNames,
  putSecrets,
  putGitlabVariables,
  createGitlabMergeRequest,
  requiredSecrets,
  renderFiles,
  renderGitlabFiles,
  loadGitlabTemplates,
  GITLAB_WORKFLOW_SOURCE,
  validate,
  prBody,
  type TokenCheck,
  type CiWorkflow,
  type FactoryConfig,
  type GitlabTemplates,
  type ProviderEntry,
  type RepoSummary,
  type ScmId,
  type ToolchainId,
} from '../lib/factory';

const CUSTOM = '__custom__';

const LABELS: Record<string, string> = {
  repo: 'Repository',
  machine: 'AI teammate account',
  ciFile: 'CI workflow file',
  ciName: 'CI workflow name',
  checks: 'Checks',
  baseBranch: 'Target branch',
  merge: 'Merge identity',
  runner: 'Runner',
  providers: 'AI provider',
};

/** GitHub matches check contexts case-insensitively. */
const same = (a: string, b: string) => a.toLowerCase() === b.toLowerCase();

function ProviderRow({
  entry,
  onChange,
  onRemove,
}: {
  entry: ProviderEntry;
  onChange: (e: ProviderEntry) => void;
  onRemove?: () => void;
}) {
  const preset = PROVIDER_PRESETS.find((p) => p.id === entry.preset)!;
  const pick = (id: string) => onChange(newEntry(id));
  const listId = `models-${entry.preset}`;
  return (
    <div className="factory__provider">
      <select aria-label="Provider" value={entry.preset} onChange={(e) => pick(e.target.value)}>
        {PROVIDER_PRESETS.map((p) => (
          <option key={p.id} value={p.id}>
            {p.label}
          </option>
        ))}
      </select>
      <input
        aria-label="Model"
        list={listId}
        value={entry.model}
        placeholder="model id"
        onChange={(e) => onChange({ ...entry, model: e.target.value.trim() })}
      />
      <datalist id={listId}>
        {preset.models.map((m) => (
          <option key={m} value={m} />
        ))}
      </datalist>
      <input
        aria-label="API key secret name"
        title="Name of the repository secret that will hold this provider's API key"
        value={entry.keyEnv}
        placeholder="KEY_ENV_NAME"
        onChange={(e) => onChange({ ...entry, keyEnv: e.target.value.trim().toUpperCase() })}
      />
      {['custom', 'dial', 'openrouter', 'codemie'].includes(entry.preset) && (
        <input
          aria-label="Base URL"
          className="factory__wide"
          value={entry.baseUrl}
          placeholder="https://… or http://…"
          onChange={(e) => onChange({ ...entry, baseUrl: e.target.value.trim() })}
        />
      )}
      <p className="factory__keyhelp factory__wide">
        <strong>
          Secret <code>{entry.keyEnv || 'KEY_NAME'}</code>
        </strong>{' '}
        — the NAME of a repository secret, not the key itself. {preset.keyHelp}{' '}
        {preset.keyUrl && (
          <a href={preset.keyUrl} target="_blank" rel="noopener">
            Get a key ↗
          </a>
        )}
        <br />
        After the PR is created: repository → Settings → Secrets and variables → Actions → New repository secret → name{' '}
        <code>{entry.keyEnv || 'KEY_NAME'}</code>.
      </p>
      {onRemove && (
        <button type="button" className="factory__x" aria-label="Remove provider" onClick={onRemove}>
          ×
        </button>
      )}
    </div>
  );
}

function ProviderList({
  list,
  mode,
  onChange,
}: {
  list: ProviderEntry[];
  mode: 'default' | 'queue';
  onChange: (l: ProviderEntry[]) => void;
}) {
  return (
    <div className="factory__providers">
      {list.map((e, i) => (
        <div key={i} className="factory__queueRow">
          {mode === 'queue' && <span className="factory__rank">{i + 1}</span>}
          <ProviderRow
            entry={e}
            onChange={(n) => onChange(list.map((x, j) => (j === i ? n : x)))}
            onRemove={mode === 'queue' && list.length > 1 ? () => onChange(list.filter((_, j) => j !== i)) : undefined}
          />
        </div>
      ))}
      {mode === 'queue' && (
        <button
          type="button"
          className="btn btn--ghost factory__mini"
          onClick={() => onChange([...list, newEntry('openai')])}
        >
          + Add fallback provider
        </button>
      )}
    </div>
  );
}

export default function FactorySetup() {
  const [cfg, setCfg] = useState<FactoryConfig>(defaults);
  const [scm, setScm] = useState<ScmId>('github');
  const [gitlabBaseUrl, setGitlabBaseUrl] = useState('https://gitlab.com');
  const [gitlabTemplates, setGitlabTemplates] = useState<GitlabTemplates | null>(null);
  const [gitlabTemplateError, setGitlabTemplateError] = useState('');
  const [gitlabTemplateAttempt, setGitlabTemplateAttempt] = useState(0);
  const [token, setToken] = useState('');
  const [repos, setRepos] = useState<RepoSummary[]>([]);
  const [repoLoadError, setRepoLoadError] = useState('');
  const [comboOpen, setComboOpen] = useState(false);
  const [active, setActive] = useState(0);
  const [tokenInfo, setTokenInfo] = useState<TokenCheck | null>(null);
  const [tokenErr, setTokenErr] = useState('');
  const [secretVals, setSecretVals] = useState<Record<string, string>>({});
  const [existing, setExisting] = useState<string[]>([]);
  const [branches, setBranches] = useState<string[]>([]);
  const [existingLabels, setExistingLabels] = useState<string[]>([]);
  const [labelsBusy, setLabelsBusy] = useState(false);
  const [runnerMode, setRunnerMode] = useState(defaults.runner);
  const [busy, setBusy] = useState(false);
  const [lines, setLines] = useState<string[]>([]);
  const [prUrl, setPrUrl] = useState('');
  const [error, setError] = useState('');
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const [open, setOpen] = useState(0);
  const [previewTab, setPreviewTab] = useState<'files' | 'guide' | 'checklist'>('files');
  const [copiedPath, setCopiedPath] = useState<string | null>(null);
  const [activeStep, setActiveStep] = useState<string>('step-repo');

  const errors = useMemo(() => validate(cfg, scm), [cfg, scm]);
  const normalizedGitlabBaseUrl = useMemo(() => {
    try {
      return normalizeGitlabBaseUrl(gitlabBaseUrl);
    } catch {
      return '';
    }
  }, [gitlabBaseUrl]);
  const generation = useMemo(() => {
    if (Object.keys(errors).length) {
      return { files: [], error: '' };
    }
    try {
      if (scm === 'gitlab') {
        return { files: gitlabTemplates ? renderGitlabFiles(cfg, gitlabTemplates, normalizedGitlabBaseUrl) : [], error: '' };
      }
      return {
        files: renderFiles(cfg),
        error: '',
      };
    } catch (error) {
      return { files: [], error: (error as Error).message };
    }
  }, [cfg, errors, scm, normalizedGitlabBaseUrl, gitlabTemplates]);
  const files = generation.files;
  const templateError = gitlabTemplateError || generation.error;
  const set = <K extends keyof FactoryConfig>(k: K, v: FactoryConfig[K]) => setCfg((c) => ({ ...c, [k]: v }));
  const touch = (k: string) => setTouched((t) => ({ ...t, [k]: true }));
  const show = (k: string) => (touched[k] ? errors[k] : undefined);

  useEffect(() => {
    if (scm !== 'gitlab') return;
    const controller = new AbortController();
    setGitlabTemplates(null);
    setGitlabTemplateError('');
    loadGitlabTemplates(controller.signal).then((templates) => {
      if (!controller.signal.aborted) setGitlabTemplates(templates);
    }).catch((error: Error) => {
      if (!controller.signal.aborted) setGitlabTemplateError(`Unable to load GitLab parent workflows: ${error.message}`);
    });
    return () => controller.abort();
  }, [scm, gitlabTemplateAttempt]);

  useEffect(() => {
    const ids = ['step-repo', 'step-fa', 'step-providers', 'step-runtime', 'step-secrets'];
    const onScroll = () => {
      const trigger = 220;
      let current = ids[0];
      for (const id of ids) {
        const el = document.getElementById(id);
        if (el && el.getBoundingClientRect().top <= trigger) {
          current = id;
        }
      }
      setActiveStep(current);
    };
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);
  useEffect(() => {
    setRepos([]);
    setRepoLoadError('');
    if (token.length < 20) return;
    if (scm === 'gitlab' && !normalizedGitlabBaseUrl) {
      setRepoLoadError('Enter a valid GitLab instance URL before listing repositories.');
      return;
    }
    let cancelled = false;
    const t = setTimeout(() => {
      listRepos(token, scm, normalizedGitlabBaseUrl)
        .then((result) => {
          if (!cancelled) setRepos(result);
        })
        .catch((e) => {
          if (!cancelled) setRepoLoadError((e as Error).message);
        });
    }, 500);
    return () => {
      cancelled = true;
      clearTimeout(t);
    };
  }, [token, scm, normalizedGitlabBaseUrl]);

  useEffect(() => {
    setTokenInfo(null);
    setTokenErr('');
    if (token.length < 20) return;
    if (scm === 'gitlab' && !normalizedGitlabBaseUrl) {
      setTokenErr('Enter a valid GitLab instance URL to validate this token.');
      return;
    }
    let cancelled = false;
    const t = setTimeout(() => {
      const check = scm === 'gitlab'
        ? checkGitlabToken(token, normalizedGitlabBaseUrl)
        : checkToken(token, errors.repo || cfg.createRepo ? undefined : cfg.repo);
      check
        .then((result) => {
          if (!cancelled) setTokenInfo(result);
        })
        .catch((e) => {
          if (!cancelled) setTokenErr((e as Error).message);
        });
    }, 400);
    return () => {
      cancelled = true;
      clearTimeout(t);
    };
  }, [token, cfg.repo, cfg.createRepo, scm, normalizedGitlabBaseUrl]);

  useEffect(() => {
    if (scm !== 'github') {
      setBranches([]);
      setExisting([]);
      setExistingLabels([]);
      if (scm === 'gitlab' && cfg.repo && !errors.repo && token.length > 20 && normalizedGitlabBaseUrl) {
        let cancelled = false;
        listGitlabVariableNames(token, cfg.repo, normalizedGitlabBaseUrl)
          .then((names) => {
            if (!cancelled) setExisting(names);
          })
          .catch((e) => {
            if (!cancelled) setRepoLoadError((e as Error).message);
          });
        listGitlabLabels(token, cfg.repo, normalizedGitlabBaseUrl)
          .then((labels) => {
            if (!cancelled) setExistingLabels(labels);
          })
          .catch(() => {});
        return () => {
          cancelled = true;
        };
      }
      return;
    }
    if (!cfg.repo || errors.repo || token.length < 20 || cfg.createRepo) {
      if (cfg.createRepo) {
        setBranches([]);
        setExisting([]);
        setExistingLabels([]);
      }
      return;
    }
    setBranches([]);
    setExisting([]);
    listSecretNames(token, cfg.repo)
      .then(setExisting)
      .catch(() => {});
    setExistingLabels([]);
    listLabels(token, cfg.repo)
      .then(setExistingLabels)
      .catch(() => {});
    listBranches(token, cfg.repo)
      .then((b) => {
        setBranches(b.names);
        set('baseBranch', b.names.includes('main') ? 'main' : b.defaultBranch);
      })
      .catch(() => {});
    detectCi(token, cfg.repo)
      .then((d) => {
        if (d.workflows[0] && isCiEnabled(cfg) && (!cfg.qualityPreset || cfg.qualityPreset === 'none')) {
          applyWorkflow(d.workflows[0], d.required);
        }
      })
      .catch(() => {});
  }, [cfg.repo, token, cfg.createRepo, scm, normalizedGitlabBaseUrl]);

  const filtered = useMemo(() => {
    const q = cfg.repo.toLowerCase();
    if (repos.some((r) => r.full_name.toLowerCase() === q)) return [];
    return repos.filter((r) => r.full_name.toLowerCase().includes(q)).slice(0, 50);
  }, [repos, cfg.repo]);

  function applyWorkflow(w: CiWorkflow, required: string[]) {
    const req = required.filter((r) => w.jobs.some((j) => same(j, r)));
    const gate = w.jobs.filter((j) => !j.includes('${{') && /gate|unit tests|^test$/i.test(j));
    setCfg((c) => ({
      ...c,
      ciEnabled: true,
      qualityPreset: 'none',
      ciFile: w.file,
      ciName: w.name,
      checks: req.length ? req : gate,
    }));
  }

  function chooseRepo(name: string) {
    set('repo', name);
    if (scm === 'gitlab') {
      const selected = repos.find((repo) => repo.full_name === name);
      if (selected?.default_branch) set('baseBranch', selected.default_branch);
    }
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

  async function copyContent(key: string, text: string) {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedPath(key);
      setTimeout(() => setCopiedPath((prev) => (prev === key ? null : prev)), 1800);
    } catch {
      /* ignore clipboard error */
    }
  }

  async function submit() {
    setBusy(true);
    setError('');
    setLines([]);
    setPrUrl('');
    const log = (m: string) => setLines((l) => [...l, m]);
    try {
      if (scm === 'gitlab') {
        if (!gitlabTemplates || templateError) {
          throw new Error(templateError || 'GitLab parent workflows have not loaded yet.');
        }
        const wanted = Object.fromEntries(
          requiredSecrets(cfg, scm, normalizedGitlabBaseUrl).map((x) => [x.name, secretVals[x.name] ?? '']),
        );
        await putGitlabVariables(token, cfg.repo, normalizedGitlabBaseUrl, wanted, log);
        if (cfg.createLabels !== false) {
          await ensureGitlabLabels(token, cfg.repo, normalizedGitlabBaseUrl, log);
          listGitlabLabels(token, cfg.repo, normalizedGitlabBaseUrl)
            .then(setExistingLabels)
            .catch(() => {});
        }
        const mergeRequest = await createGitlabMergeRequest(token, cfg.repo, normalizedGitlabBaseUrl, cfg, log, gitlabTemplates);
        setSecretVals({});
        setPrUrl(mergeRequest);
        await ensureGitlabIssueEventWebhook(token, cfg.repo, normalizedGitlabBaseUrl, cfg.baseBranch, log);
        listGitlabVariableNames(token, cfg.repo, normalizedGitlabBaseUrl)
          .then(setExisting)
          .catch((e) => setError((e as Error).message));
        return;
      }
      let targetCfg = cfg;
      if (cfg.createRepo) {
        const created = await createRepo(token, cfg.repo, cfg.repoPrivate !== false, tokenInfo?.login, log);
        targetCfg = { ...cfg, repo: created.full_name, baseBranch: cfg.baseBranch || created.default_branch };
      }
      const wanted = Object.fromEntries(requiredSecrets(targetCfg).map((x) => [x.name, secretVals[x.name] ?? '']));
      if (!cfg.createRepo && Object.values(wanted).some(Boolean) && tokenInfo?.admin === false) {
        throw new Error(
          `Writing secrets needs admin rights on ${targetCfg.repo} — clear the secret fields or use an admin token.`,
        );
      }
      await putSecrets(token, targetCfg.repo, wanted, log);
      setSecretVals({});
      if (targetCfg.createLabels !== false) {
        await ensureLabels(token, targetCfg.repo, log);
        if (!cfg.createRepo) {
          listLabels(token, targetCfg.repo)
            .then(setExistingLabels)
            .catch(() => {});
        }
      }
      setPrUrl(await createFactoryPr(token, targetCfg, log));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const canSubmit =
    !busy &&
    token.length > 20 &&
    Boolean(tokenInfo) &&
    Object.keys(errors).length === 0 &&
    !tokenInfo?.missingScopes.length &&
    (scm === 'gitlab' || cfg.createRepo || tokenInfo?.push !== false) &&
    !generation.error &&
    (scm !== 'gitlab' || (Boolean(normalizedGitlabBaseUrl) && Boolean(gitlabTemplates) && !templateError));

  const faEnabled = scm === 'github' && isFaUniversalEnabled(cfg);
  const memEnabled = isFaMemoryEnabled(cfg);

  const stepRepoReady = Boolean(cfg.repo && !errors.repo && !errors.baseBranch && !errors.machine);
  const stepProvidersReady = Boolean(!errors.providers);
  const stepRuntimeReady = Boolean(!errors.runner && !errors.merge);
  const secretsList = requiredSecrets(cfg, scm, normalizedGitlabBaseUrl || gitlabBaseUrl);
  const configuredSecretsCount = secretsList.filter((s) => existing.includes(s.name) || Boolean(secretVals[s.name])).length;

  return (
    <div className="factory">
      {/* Top Status & Quick Navigation Bar */}
      <nav className="factory__steps" aria-label="Setup sections">
        <div className="factory__stepsInner">
          <a
            href="#step-repo"
            className={`factory__stepPill ${stepRepoReady ? 'is-ready' : ''} ${activeStep === 'step-repo' ? 'is-active' : ''}`}
          >
            <span className="factory__stepNum">1</span>
            <span>
              <strong>Repository &amp; Access</strong>
              <small>{cfg.repo || 'Select owner/repo'}</small>
            </span>
          </a>
          <a
            href="#step-fa"
            className={`factory__stepPill is-ready ${activeStep === 'step-fa' ? 'is-active' : ''}`}
          >
            <span className="factory__stepNum">2</span>
            <span>
              <strong>FA Agent &amp; Memory</strong>
              <small>
                {faEnabled ? '@fa enabled' : '@fa off'} · {memEnabled ? '.fah/memory on' : 'memory off'}
              </small>
            </span>
          </a>
          <a
            href="#step-providers"
            className={`factory__stepPill ${stepProvidersReady ? 'is-ready' : ''} ${activeStep === 'step-providers' ? 'is-active' : ''}`}
          >
            <span className="factory__stepNum">3</span>
            <span>
              <strong>AI Providers</strong>
              <small>
                {cfg.providers.entries[0]?.preset ?? 'copilot'} ({cfg.providers.mode})
              </small>
            </span>
          </a>
          <a
            href="#step-runtime"
            className={`factory__stepPill ${stepRuntimeReady ? 'is-ready' : ''} ${activeStep === 'step-runtime' ? 'is-active' : ''}`}
          >
            <span className="factory__stepNum">4</span>
            <span>
              <strong>Runner &amp; Merge</strong>
              <small>
                {cfg.runner} · {cfg.merge.mode}
              </small>
            </span>
          </a>
          <a
            href="#step-secrets"
            className={`factory__stepPill is-ready ${activeStep === 'step-secrets' ? 'is-active' : ''}`}
          >
            <span className="factory__stepNum">5</span>
            <span>
              <strong>{scm === 'gitlab' ? 'Variables & Setup' : 'Secrets & Labels'}</strong>
              <small>
                {configuredSecretsCount}/{secretsList.length} secrets · {FACTORY_LABELS.length} labels
              </small>
            </span>
          </a>
        </div>
      </nav>

      <div className="factory__form">
        {/* STEP 1: Repository & Access */}
        <section id="step-repo" className="factory__card">
          <header className="factory__cardHead">
            <span className="factory__stepBadge">Step 1</span>
            <div>
              <h2 className="factory__cardTitle">Repository &amp; Access</h2>
              <p className="factory__cardSub">
                Connect your source control repository{scm === 'github' ? ' (or create a new Greenfield repo)' : ''} and
                specify the AI teammate account.
              </p>
            </div>
          </header>

          <div className="factory__grid2">
            <div className="factory__field">
              <label htmlFor="f-scm">Source control</label>
              <select
                id="f-scm"
                value={scm}
                disabled={busy}
                onChange={(e) => {
                  const nextScm = e.target.value as ScmId;
                  setScm(nextScm);
                  setCfg((c) => ({ ...c, repo: '', createRepo: false }));
                  setTokenInfo(null);
                  setBranches([]);
                  setExisting([]);
                  setError('');
                  setPrUrl('');
                  setComboOpen(false);
                }}
              >
                {SCM.map((s) => (
                  <option key={s.id} value={s.id} disabled={!s.available}>
                    {s.label}
                  </option>
                ))}
              </select>
            </div>

            {scm === 'gitlab' && (
              <div className="factory__field factory__field--full">
                <label htmlFor="f-gitlab-url">GitLab instance URL</label>
                <input
                  id="f-gitlab-url"
                  type="url"
                  autoComplete="url"
                  value={gitlabBaseUrl}
                  aria-invalid={!normalizedGitlabBaseUrl}
                  placeholder="https://gitlab.example.com"
                  onChange={(e) => {
                    setGitlabBaseUrl(e.target.value.trim());
                    setCfg((c) => ({ ...c, repo: '' }));
                    setRepos([]);
                    setRepoLoadError('');
                    setComboOpen(false);
                  }}
                />
                {!normalizedGitlabBaseUrl && (
                  <small className="is-error" role="alert">
                    Use HTTPS for your GitLab instance (HTTP is allowed only on localhost); do not include credentials,
                    query parameters, or a fragment.
                  </small>
                )}
                <small>
                  Include the instance base URL (and any path prefix), but not <code>/api/v4</code>. Your PAT is sent
                  directly to this host over HTTPS.
                </small>
              </div>
            )}

            <div className="factory__field">
              <label htmlFor="f-machine">AI teammate account</label>
              <input
                id="f-machine"
                value={cfg.machine}
                aria-invalid={!!show('machine')}
                onBlur={() => touch('machine')}
                onChange={(e) => set('machine', e.target.value.trim())}
              />
              <small className={show('machine') ? 'is-error' : ''}>
                {show('machine') ?? `${scm === 'gitlab' ? 'GitLab' : 'GitHub'} username that issues are assigned to`}
              </small>
            </div>
          </div>

          <div className="factory__field">
            <div className="factory__labelRow">
              <label htmlFor="f-token">{scm === 'gitlab' ? 'GitLab setup token' : 'GitHub setup token'}</label>
              <a
                className="btn btn--ghost factory__mini"
                target="_blank"
                rel="noopener"
                href={
                  scm === 'gitlab'
                    ? normalizedGitlabBaseUrl
                      ? TOKEN_LINKS.setupGitlab(normalizedGitlabBaseUrl)
                      : undefined
                    : TOKEN_LINKS.setup
                }
              >
                Create token on {scm === 'gitlab' ? 'GitLab' : 'GitHub'} ↗
              </a>
            </div>
            <input
              id="f-token"
              type="password"
              autoComplete="off"
              spellCheck={false}
              value={token}
              onChange={(e) => {
                const value = e.target.value.trim();
                setToken(value);
                if (scm === 'gitlab') {
                  setSecretVals((secrets) => ({ ...secrets, GITLAB_TOKEN: value }));
                }
              }}
              placeholder={scm === 'gitlab' ? 'glpat-…' : 'ghp_… or github_pat_…'}
            />
            {tokenErr && <small className="is-error">{tokenErr}</small>}
            {repoLoadError && <small className="is-error">{repoLoadError}</small>}
            {tokenInfo && (
              <ul className="factory__tokencheck">
                <li className="is-ok">
                  Signed in as <strong>{tokenInfo.login}</strong> ({tokenInfo.kind} token)
                </li>
                {scm === 'gitlab' ? (
                  <>
                    <li className="is-warn">
                      Use a GitLab personal access token with the <code>api</code> and <code>write_repository</code>{' '}
                      scopes. Its user needs Developer or higher access to push branches and create merge requests.
                    </li>
                    <li className="is-warn">
                      This setup also needs Maintainer access to manage project webhooks and trigger tokens.
                    </li>
                  </>
                ) : tokenInfo.kind === 'classic' &&
                  (tokenInfo.missingScopes.length ? (
                    <li className="is-bad">
                      Missing scopes: {tokenInfo.missingScopes.join(', ')} — regenerate with the button above
                    </li>
                  ) : (
                    <li className="is-ok">Scopes: repo, workflow</li>
                  ))}
                {tokenInfo.kind === 'fine-grained' && (
                  <li className="is-warn">
                    Fine-grained token: ensure Contents, Pull requests, Workflows: write and Secrets: write on the
                    repository — scopes cannot be verified, use a classic token if unsure
                  </li>
                )}
                {!cfg.createRepo && tokenInfo.push === false && (
                  <li className="is-bad">No write access to {cfg.repo}</li>
                )}
                {!cfg.createRepo &&
                  tokenInfo.push &&
                  (tokenInfo.admin ? (
                    <li className="is-ok">Admin on {cfg.repo} — can create the PR and write secrets</li>
                  ) : (
                    <li className="is-warn">
                      Write but not admin on {cfg.repo} — the PR works, secrets must be added by an admin
                    </li>
                  ))}
              </ul>
            )}
            <small>
              {scm === 'gitlab' ? (
                <>
                  Your token stays in this browser tab and talks directly to the configured GitLab instance — never
                  stored on any server.
                </>
              ) : (
                <>
                  Opens GitHub with <code>repo</code> + <code>workflow</code> pre-selected. Your token stays in this
                  browser tab and talks directly to <code>api.github.com</code> — never stored on any server.
                </>
              )}
            </small>
          </div>

          <div className="factory__field">
            <label htmlFor="f-repo">Repository</label>
            {scm === 'github' && (
              <div className="factory__seg" role="radiogroup" aria-label="Repository mode">
                {(
                  [
                    [false, 'Existing repository'],
                    [true, 'Create new (Greenfield)'],
                  ] as const
                ).map(([isNew, label]) => (
                  <button
                    key={String(isNew)}
                    type="button"
                    role="radio"
                    aria-checked={Boolean(cfg.createRepo) === isNew}
                    className={Boolean(cfg.createRepo) === isNew ? 'is-on' : ''}
                    onClick={() => {
                      setCfg((c) => ({
                        ...c,
                        createRepo: isNew,
                        repo: isNew && !c.repo && tokenInfo?.login ? `${tokenInfo.login}/` : c.repo,
                      }));
                    }}
                  >
                    {label}
                  </button>
                ))}
              </div>
            )}
            <div className="factory__combo">
              <input
                id="f-repo"
                role="combobox"
                aria-expanded={!cfg.createRepo && comboOpen && filtered.length > 0}
                aria-controls="f-repo-list"
                aria-autocomplete="list"
                autoComplete="off"
                value={cfg.repo}
                aria-invalid={!!show('repo')}
                placeholder={
                  cfg.createRepo
                    ? `${tokenInfo?.login || 'owner'}/new-repo-name`
                    : repos.length
                      ? 'Start typing to search your repositories…'
                      : scm === 'gitlab'
                        ? 'group/project'
                        : 'owner/name'
                }
                onFocus={() => setComboOpen(true)}
                onBlur={() => {
                  touch('repo');
                  setTimeout(() => setComboOpen(false), 120);
                }}
                onChange={(e) => {
                  set('repo', e.target.value.trim());
                  setComboOpen(true);
                  setActive(0);
                }}
                onKeyDown={(e) => {
                  if (cfg.createRepo || !comboOpen || !filtered.length) return;
                  if (e.key === 'ArrowDown') {
                    e.preventDefault();
                    setActive((a) => Math.min(a + 1, filtered.length - 1));
                  } else if (e.key === 'ArrowUp') {
                    e.preventDefault();
                    setActive((a) => Math.max(a - 1, 0));
                  } else if (e.key === 'Enter') {
                    e.preventDefault();
                    chooseRepo(filtered[active].full_name);
                  } else if (e.key === 'Escape') setComboOpen(false);
                }}
              />
              {!cfg.createRepo && comboOpen && filtered.length > 0 && (
                <ul id="f-repo-list" role="listbox" className="factory__options">
                  {filtered.map((r, i) => (
                    <li
                      key={r.full_name}
                      role="option"
                      aria-selected={i === active}
                      className={i === active ? 'is-active' : ''}
                      onMouseEnter={() => setActive(i)}
                      onMouseDown={(e) => {
                        e.preventDefault();
                        chooseRepo(r.full_name);
                      }}
                    >
                      {r.full_name}
                      {r.private ? ' 🔒' : ''}
                    </li>
                  ))}
                </ul>
              )}
            </div>
            {scm === 'github' && cfg.createRepo && (
              <div className="factory__seg" role="radiogroup" aria-label="New repository visibility">
                {(
                  [
                    [true, 'Private 🔒'],
                    [false, 'Public'],
                  ] as const
                ).map(([priv, label]) => (
                  <button
                    key={String(priv)}
                    type="button"
                    role="radio"
                    aria-checked={(cfg.repoPrivate !== false) === priv}
                    className={(cfg.repoPrivate !== false) === priv ? 'is-on' : ''}
                    onClick={() => set('repoPrivate', priv)}
                  >
                    {label}
                  </button>
                ))}
              </div>
            )}
            <small className={show('repo') ? 'is-error' : ''}>
              {show('repo') ??
                (scm === 'gitlab'
                  ? repos.length
                    ? `${repos.length} GitLab projects you can push to`
                    : 'Enter a GitLab personal access token with api scope to pick from your projects'
                  : cfg.createRepo
                    ? 'Creates the repository on GitHub (with an initial commit on main) under your account or organization before opening the factory PR'
                    : repos.length
                      ? `${repos.length} repositories you can push to`
                      : 'Enter a token to pick from your repositories, or type owner/name')}
            </small>
            {scm === 'gitlab' && (
              <small className="is-warn">
                GitLab setup opens a merge request with a manual DMTools pipeline. The GitHub event-driven factory
                state machine, automatic quality workflows, and auto-merge are not generated for GitLab.
              </small>
            )}
          </div>

          <div className="factory__field">
            <label htmlFor="f-base">Target branch</label>
            {branches.length > 0 && !cfg.createRepo ? (
              <select id="f-base" value={cfg.baseBranch} onChange={(e) => set('baseBranch', e.target.value)}>
                {branches.map((b) => (
                  <option key={b} value={b}>
                    {b}
                  </option>
                ))}
              </select>
            ) : (
              <input
                id="f-base"
                value={cfg.baseBranch}
                aria-invalid={!!errors.baseBranch}
                onChange={(e) => set('baseBranch', e.target.value.trim())}
              />
            )}
            <small className={errors.baseBranch ? 'is-error' : ''}>
              {errors.baseBranch ?? 'Agents branch from it and open their PRs against it; the setup PR targets it too'}
            </small>
          </div>
        </section>

        {/* STEP 2: FA Universal Agent & Project Memory */}
        <section id="step-fa" className="factory__card factory__card--accent">
          <header className="factory__cardHead">
            <span className="factory__stepBadge">Step 2</span>
            <div>
              <h2 className="factory__cardTitle">FA Universal Agent &amp; Project Memory</h2>
              <p className="factory__cardSub">
                {scm === 'gitlab'
                  ? 'Project memory is supported in GitLab. The @fa conversational workflow currently depends on GitHub Actions and is not generated for GitLab.'
                  : (
                      <>
                        Install the ultra-fast <code>@fa</code> conversational agent (~2s cold start) and repository-backed
                        durable memory alongside your DMTools factory. Both are optional and enabled by default.
                      </>
                    )}
              </p>
            </div>
          </header>

          <div className="factory__featureGrid">
            <label className={`factory__featureCard ${faEnabled ? 'is-on' : ''}`}>
              <div className="factory__featureTop">
                <input
                  type="checkbox"
                  checked={faEnabled}
                  disabled={scm === 'gitlab'}
                  onChange={(e) => set('faUniversal', e.target.checked)}
                />
                <span className="factory__featureTitle">
                  FA Universal Agent {scm === 'gitlab' ? '(GitHub Actions only)' : <>(<code>@fa</code> mentions)</>}
                </span>
                {scm === 'github' && <em className="factory__badge">Recommended</em>}
              </div>
              <p className="factory__featureDesc">
                {scm === 'gitlab'
                  ? 'This GitHub Actions conversation workflow is not part of the generated GitLab CI pipeline.'
                  : (
                      <>
                        Generates <code>.github/workflows/fa-universal.yml</code>. Mention <code>@fa</code> in any Issue
                        body, Issue comment, or PR review comment to trigger an interactive coding agent with full
                        DMTools CLI access, single-queue concurrency, and automatic session persistence on branch{' '}
                        <code>fa-sess/&lt;session_key&gt;</code>.
                      </>
                    )}
              </p>
            </label>

            <label className={`factory__featureCard ${memEnabled ? 'is-on' : ''}`}>
              <div className="factory__featureTop">
                <input
                  type="checkbox"
                  checked={memEnabled}
                  onChange={(e) => set('faMemory', e.target.checked)}
                />
                <span className="factory__featureTitle">
                  Durable Project Memory (<code>.fah/memory</code>)
                </span>
                <em className="factory__badge">Recommended</em>
              </div>
              <p className="factory__featureDesc">
                Generates <code>.fah/config.yaml</code> pointing project memory to <code>.fah/memory</code>. Architectural
                decisions, conventions, and learned repo facts are committed to the repository so every future DMTools
                agent run starts with shared context.
              </p>
            </label>
          </div>

          {faEnabled && (
            <div className="factory__howto">
              <div className="factory__howtoHead">
                <strong>How <code>@fa</code> works in your repository</strong>
                <button
                  type="button"
                  className="btn btn--ghost factory__mini"
                  onClick={() => setPreviewTab('guide')}
                >
                  Open full usage guide →
                </button>
              </div>
              <ol className="factory__howtoList">
                <li>
                  <strong>Mention anywhere:</strong> Write <code>@fa implement this</code> or{' '}
                  <code>@fa fix the failing test</code> in any Issue or PR comment.
                </li>
                <li>
                  <strong>Stateful single-queue sessions:</strong> Each Issue/PR has its own persistent session branch (
                  <code>fa-sess/gh-owner-repo-123</code>). If multiple <code>@fa</code> comments arrive while a run is
                  active, the agent drains the inbox sequentially before exiting.
                </li>
                <li>
                  <strong>Zero env-file boilerplate:</strong> Both <code>fa</code> and <code>dmtools</code> read your
                  configured repository secrets directly from environment variables — no <code>dmtools.env</code> file
                  needed.
                </li>
                <li>
                  <strong>Live DAP Hub Steering (Optional):</strong> Add secret <code>FA_DAP_INVITE</code> (or{' '}
                  <code>DAP_HUB_URL</code> + <code>DAP_CLIENT_SECRET</code>) to steer a running agent mid-turn in real
                  time via <code>agent_message</code> to <code>gh-&lt;owner&gt;-&lt;repo&gt;-&lt;issue&gt;</code>.
                </li>
              </ol>
            </div>
          )}
        </section>

        {/* STEP 3: AI Provider & Failover Queue */}
        <section id="step-providers" className="factory__card">
          <header className="factory__cardHead">
            <span className="factory__stepBadge">Step 3</span>
            <div>
              <h2 className="factory__cardTitle">AI Provider &amp; Failover Queue</h2>
              <p className="factory__cardSub">
                Configure the primary LLM (and optional failover queue) shared by the DMTools Teammate legs and FA
                Universal Agent.
              </p>
            </div>
          </header>

          <fieldset className="factory__group">
            <legend>AI provider</legend>
            <div className="factory__seg" role="radiogroup" aria-label="Provider mode">
              {(['default', 'queue'] as const).map((m) => (
                <button
                  key={m}
                  type="button"
                  role="radio"
                  aria-checked={cfg.providers.mode === m}
                  className={cfg.providers.mode === m ? 'is-on' : ''}
                  onClick={() =>
                    set('providers', {
                      ...cfg.providers,
                      mode: m,
                      entries: m === 'default' ? cfg.providers.entries.slice(0, 1) : cfg.providers.entries,
                    })
                  }
                >
                  {m === 'default' ? 'Default' : 'Queue (failover)'}
                </button>
              ))}
            </div>
            <small>
              {cfg.providers.mode === 'default'
                ? 'One provider and model for all agent legs.'
                : 'Ordered list: the next provider is used when the previous one is rate-limited or down (FA_PROVIDERS_QUEUE).'}
            </small>
            <ProviderList
              list={cfg.providers.entries}
              mode={cfg.providers.mode}
              onChange={(entries) => set('providers', { ...cfg.providers, entries })}
            />
            <label className="factory__check">
              <input
                type="checkbox"
                checked={cfg.providers.reviewSeparate}
                onChange={(e) => set('providers', { ...cfg.providers, reviewSeparate: e.target.checked })}
              />{' '}
              Different provider for PR review
            </label>
            {cfg.providers.reviewSeparate && (
              <ProviderList
                list={cfg.providers.reviewEntries}
                mode={cfg.providers.mode}
                onChange={(reviewEntries) => set('providers', { ...cfg.providers, reviewEntries })}
              />
            )}
            <small className={errors.providers ? 'is-error' : ''}>
              {errors.providers ?? `Add repository secrets: ${keySlots(cfg).join(', ')} — keys are never entered here.`}
            </small>
          </fieldset>
        </section>

        {/* STEP 4: Runtime, Toolchains & Merge Identity */}
        <section id="step-runtime" className="factory__card">
          <header className="factory__cardHead">
            <span className="factory__stepBadge">Step 4</span>
            <div>
              <h2 className="factory__cardTitle">Runner, Toolchains &amp; Merge Identity</h2>
              <p className="factory__cardSub">
                Select the GitHub Actions runner label, cached SDK toolchains, and the identity that merges approved
                PRs.
              </p>
            </div>
          </header>

          <div className="factory__field">
            <label htmlFor="f-runner">Runner</label>
            <select
              id="f-runner"
              value={RUNNERS.includes(runnerMode) ? runnerMode : CUSTOM}
              onChange={(e) => {
                setRunnerMode(e.target.value);
                if (e.target.value !== CUSTOM) set('runner', e.target.value);
              }}
            >
              {RUNNERS.map((r) => (
                <option key={r} value={r}>
                  {r}
                </option>
              ))}
              <option value={CUSTOM}>Custom label…</option>
            </select>
            {runnerMode === CUSTOM && (
              <input
                value={cfg.runner}
                aria-label="Custom runner label"
                aria-invalid={!!errors.runner}
                placeholder="self-hosted-label"
                onChange={(e) => set('runner', e.target.value.trim())}
              />
            )}
            <small className={errors.runner ? 'is-error' : ''}>
              {errors.runner ?? 'Where the factory legs and @fa universal agent run'}
            </small>
          </div>

          <fieldset className="factory__group">
            <legend>Frameworks &amp; tools</legend>
            <small>Installed for the agent legs. Nothing is selected by default.</small>
            <div className="factory__tools">
              {TOOLCHAINS.map((t) => {
                const on = cfg.toolchains[t.id] !== undefined;
                return (
                  <div key={t.id} className="factory__tool">
                    <label>
                      <input type="checkbox" checked={on} onChange={(e) => toggleTool(t.id, e.target.checked)} />{' '}
                      {t.label}
                    </label>
                    {on && (
                      <select
                        aria-label={`${t.label} version`}
                        value={cfg.toolchains[t.id]}
                        onChange={(e) => set('toolchains', { ...cfg.toolchains, [t.id]: e.target.value })}
                      >
                        {t.versions.map((v) => (
                          <option key={v} value={v}>
                            {v}
                          </option>
                        ))}
                      </select>
                    )}
                  </div>
                );
              })}
            </div>
          </fieldset>

          <fieldset className="factory__group">
            <legend>Merge identity</legend>
            <small>
              Who merges the machine's PRs once the checks are green. A separate identity keeps merges auditable and
              lets branch rules allow only it to merge.
            </small>
            {(
              [
                ['factory', 'Factory token', 'SOURCE_GITHUB_TOKEN — the same PAT the agents use'],
                ['token', 'Separate token', 'A PAT of another account, stored in its own secret'],
                [
                  'app',
                  'GitHub App',
                  'Short-lived installation token minted per run; merges appear as the App and trigger downstream workflows',
                ],
              ] as const
            ).map(([m, label, hint]) => (
              <label key={m} className="factory__check factory__radio">
                <input
                  type="radio"
                  name="merge-mode"
                  checked={cfg.merge.mode === m}
                  onChange={() => set('merge', { ...cfg.merge, mode: m })}
                />
                <span>
                  <strong>{label}</strong>
                  <br />
                  <small>{hint}</small>
                </span>
              </label>
            ))}
            {cfg.merge.mode === 'token' && (
              <div className="factory__field">
                <label htmlFor="f-merge-secret">Merge token secret name</label>
                <input
                  id="f-merge-secret"
                  value={cfg.merge.tokenSecret}
                  aria-invalid={!!errors.merge}
                  onChange={(e) => set('merge', { ...cfg.merge, tokenSecret: e.target.value.trim().toUpperCase() })}
                />
                <small className={errors.merge ? 'is-error' : ''}>
                  {errors.merge ??
                    'Create a classic PAT with the repo scope for the merge account, then add it as this repository secret.'}
                </small>
                <a className="btn btn--ghost factory__mini" target="_blank" rel="noopener" href={TOKEN_LINKS.merge}>
                  Create merge token ↗
                </a>
              </div>
            )}
            {cfg.merge.mode === 'app' && (
              <p className="factory__keyhelp">
                <strong>
                  Secrets <code>MERGE_APP_ID</code> + <code>MERGE_APP_PRIVATE_KEY</code>.
                </strong>{' '}
                Create an App (Settings → Developer settings → GitHub Apps → New) with Repository permissions Contents,
                Pull requests, Issues: <em>Read and write</em>, Checks: <em>Read</em>; no webhook. Install it on the
                repository, copy the App ID, generate a private key and paste the whole PEM into the secret.{' '}
                <a href={TOKEN_LINKS.app(cfg.repo)} target="_blank" rel="noopener">
                  Create App ↗
                </a>
              </p>
            )}
          </fieldset>
        </section>

        {/* STEP 5: Repository Secrets & Labels */}
        <section id="step-secrets" className="factory__card">
          <header className="factory__cardHead">
            <span className="factory__stepBadge">Step 5</span>
            <div>
              <h2 className="factory__cardTitle">
                {scm === 'gitlab' ? 'CI/CD Variables & Setup' : 'Repository Secrets & State Labels'}
              </h2>
              <p className="factory__cardSub">
                {scm === 'gitlab'
                  ? 'Create GitLab CI automation for Issue-event webhook processing, manual runs, merge-request review/rework, and approved merges.'
                  : 'Seal repository secrets in your browser (libsodium) and ensure the 12 factory state-machine labels exist.'}
              </p>
            </div>
          </header>

          <fieldset className="factory__group">
            <legend>Repository secrets</legend>
            <small>
              {scm === 'gitlab'
                ? 'Optional — supplied secret values are stored as GitLab project CI/CD variables with masked and hidden enabled before the merge request opens. GitLab requires version 17.4 or newer and single-line values that meet its masking rules; existing non-hidden secrets are deleted and recreated with hidden enabled. After merging the setup request, a project webhook starts the poller on every Issue event.'
                : 'Optional — fill a value and it is stored as an encrypted Actions secret before the PR opens. Values are sealed in this browser with the repository public key (libsodium), sent only to api.github.com and never readable afterwards. Leave a field empty to add it yourself later.'}
            </small>
            {secretsList.map((x) => (
              <div className="factory__field" key={x.name}>
                <label htmlFor={`s-${x.name}`}>
                  <code>{x.name}</code>
                  {existing.includes(x.name) && <em className="factory__badge">already set</em>}
                </label>
                {x.multiline ? (
                  <textarea
                    id={`s-${x.name}`}
                    rows={4}
                    spellCheck={false}
                    autoComplete="off"
                    value={secretVals[x.name] ?? ''}
                    placeholder={
                      existing.includes(x.name)
                        ? 'leave empty to keep the current value'
                        : '-----BEGIN RSA PRIVATE KEY-----…'
                    }
                    onChange={(e) => setSecretVals((s) => ({ ...s, [x.name]: e.target.value }))}
                  />
                ) : (
                  <input
                    id={`s-${x.name}`}
                    type="password"
                    autoComplete="off"
                    spellCheck={false}
                    value={secretVals[x.name] ?? ''}
                    placeholder={
                      existing.includes(x.name)
                        ? 'leave empty to keep the current value'
                        : 'paste value to set secret, or leave empty'
                    }
                    onChange={(e) => setSecretVals((s) => ({ ...s, [x.name]: e.target.value.trim() }))}
                  />
                )}
                <small>
                  {x.hint}{' '}
                  {x.url && (
                    <a href={x.url} target="_blank" rel="noopener">
                      Create ↗
                    </a>
                  )}
                </small>
              </div>
            ))}
            <small>
              {scm === 'gitlab'
                ? 'Creating or updating project variables requires a GitLab token with API access and sufficient project permissions (typically Maintainer).'
                : (
                    <>
                      Writing secrets needs a token with admin access to the repository (classic <code>repo</code>{' '}
                      scope, or the fine-grained “Secrets: write” permission).
                    </>
                  )}
            </small>
          </fieldset>

          <fieldset className="factory__group">
            <legend>{scm === 'gitlab' ? 'GitLab labels' : 'Repository labels'}</legend>
            <small>
              {scm === 'gitlab'
                ? 'Required project labels used to organize DMTools issues and merge requests.'
                : 'Required state-machine and trigger labels used by the DMTools factory workflows.'}
            </small>
            {(
              <>
                <div className="factory__labelGrid">
                  {FACTORY_LABELS.map((l) => {
                    const has = existingLabels.some((x) => same(x, l.name));
                    return (
                      <span key={l.name} className="factory__labelChip" title={l.description}>
                        <span className="factory__labelDot" style={{ background: `#${l.color}` }} />
                        <code>{l.name}</code>
                        {!cfg.createRepo &&
                          existingLabels.length > 0 &&
                          (has ? (
                            <em className="factory__badge">exists</em>
                          ) : (
                            <em className="factory__badge is-warn">missing</em>
                          ))}
                      </span>
                    );
                  })}
                </div>
                <label className="factory__check">
                  <input
                    type="checkbox"
                    checked={cfg.createLabels !== false}
                    onChange={(e) => set('createLabels', e.target.checked)}
                  />
                  Create required labels automatically during setup
                </label>
                <button
                  type="button"
                  className="btn btn--ghost factory__mini"
                  disabled={
                    Boolean(cfg.createRepo) ||
                    Boolean(errors.repo) ||
                    (scm === 'gitlab' && !normalizedGitlabBaseUrl) ||
                    token.length <= 20 ||
                    tokenInfo?.push === false ||
                    labelsBusy
                  }
                  onClick={async () => {
                    setLabelsBusy(true);
                    setError('');
                    const log = (m: string) => setLines((l) => [...l, m]);
                    try {
                      if (scm === 'gitlab') {
                        await ensureGitlabLabels(token, cfg.repo, normalizedGitlabBaseUrl, log);
                      } else {
                        await ensureLabels(token, cfg.repo, log);
                      }
                      const updated =
                        scm === 'gitlab'
                          ? await listGitlabLabels(token, cfg.repo, normalizedGitlabBaseUrl)
                          : await listLabels(token, cfg.repo);
                      setExistingLabels(updated);
                    } catch (e) {
                      setError((e as Error).message);
                    } finally {
                      setLabelsBusy(false);
                    }
                  }}
                >
                  {labelsBusy ? 'Creating labels…' : 'Create missing labels now'}
                </button>
              </>
            )}
          </fieldset>

          <div className="factory__submitBar">
            <button type="button" className="btn btn--solid factory__submitBtn" disabled={!canSubmit} onClick={submit}>
              {busy
                ? 'Working…'
                : scm === 'gitlab'
                  ? 'Set variables & create merge request'
                  : cfg.createRepo
                    ? Object.values(secretVals).some(Boolean)
                      ? 'Create repository, set secrets & open PR'
                      : 'Create repository & open pull request'
                    : Object.values(secretVals).some(Boolean)
                      ? 'Set secrets & create pull request'
                      : 'Create pull request'}
            </button>
            {!canSubmit && !busy && (
              <small className="factory__submitHint">
                {scm === 'gitlab'
                  ? templateError
                    ? templateError
                    : !gitlabTemplates
                      ? 'Loading GitLab parent workflow templates…'
                      : !normalizedGitlabBaseUrl
                        ? 'Enter a valid GitLab instance URL.'
                        : token.length <= 20
                          ? 'Enter a GitLab personal access token in Step 1.'
                          : !tokenInfo
                            ? 'Wait for GitLab to validate the token.'
                            : Object.keys(errors).length > 0
                              ? `Complete required fields (${Object.keys(errors).map((k) => LABELS[k] ?? k).join(', ')}).`
                              : 'Verify GitLab project permissions and try again.'
                  : token.length <= 20
                    ? 'Enter your GitHub setup token in Step 1 to enable PR creation.'
                    : Object.keys(errors).length > 0
                      ? `Complete required fields (${Object.keys(errors)
                          .map((k) => LABELS[k] ?? k)
                          .join(', ')}) to open the pull request.`
                      : 'Verify token permissions in Step 1.'}
              </small>
            )}
          </div>
          {error && (
            <p className="factory__error" role="alert">
              {error}
            </p>
          )}
          {lines.length > 0 && <pre className="factory__log">{lines.join('\n')}</pre>}
          {prUrl && (
            <p className="factory__done">
              {scm === 'gitlab' ? 'Merge request created: ' : 'Pull request created: '}
              <a href={prUrl} target="_blank" rel="noopener">
                {prUrl}
              </a>
            </p>
          )}
        </section>
      </div>

      {/* Sticky Right Column: Tabbed Live Preview, Usage Guide & PR Checklist */}
      <aside className="factory__preview" aria-label="Generated configuration and instructions">
        <div className="factory__previewHeader">
          <p className="eyebrow" style={{ marginBottom: 0 }}>
            Preview — {files.length || '…'} files
          </p>
          <div className="factory__seg" role="tablist" aria-label="Preview mode">
            <button
              type="button"
              role="tab"
              aria-selected={previewTab === 'files'}
              className={previewTab === 'files' ? 'is-on' : ''}
              onClick={() => setPreviewTab('files')}
            >
              Files ({files.length || 0})
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={previewTab === 'guide'}
              className={previewTab === 'guide' ? 'is-on' : ''}
              onClick={() => setPreviewTab('guide')}
            >
              @fa &amp; Factory Guide
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={previewTab === 'checklist'}
              className={previewTab === 'checklist' ? 'is-on' : ''}
              onClick={() => setPreviewTab('checklist')}
            >
              PR Checklist
            </button>
          </div>
        </div>

        {previewTab === 'files' && (
          <>
            {files.length === 0 && (
              <div className="factory__empty">
                {scm === 'gitlab' ? (
                  <>
                    <p role={templateError ? 'alert' : undefined}>
                      {templateError || (!gitlabTemplates
                        ? `Loading templates from ${GITLAB_WORKFLOW_SOURCE.repository}@${GITLAB_WORKFLOW_SOURCE.ref}…`
                        : 'Complete the required fields to preview the GitLab setup.')}
                    </p>
                    {templateError && (
                      <button type="button" className="btn btn--ghost factory__mini" onClick={() => setGitlabTemplateAttempt((attempt) => attempt + 1)}>
                        Retry loading templates
                      </button>
                    )}
                  </>
                ) : (
                  <>
                    <p>The preview appears once these are set:</p>
                    <ul>
                      {Object.entries(errors).map(([k, v]) => (
                        <li key={k}>
                          <strong>{LABELS[k] ?? k}</strong> — {v}
                        </li>
                      ))}
                    </ul>
                  </>
                )}
              </div>
            )}
            {files.map((f, i) => (
              <div key={f.path} className={`factory__file ${open === i ? 'is-open' : ''}`}>
                <div className="factory__fileHeader">
                  <button
                    type="button"
                    className="factory__fileToggle"
                    onClick={() => setOpen(open === i ? -1 : i)}
                    aria-expanded={open === i}
                  >
                    <span>{f.path}</span>
                    <span className="factory__fileChevron">{open === i ? '▾' : '▸'}</span>
                  </button>
                  <button
                    type="button"
                    className="factory__copyBtn"
                    onClick={() => copyContent(f.path, f.content)}
                    title={`Copy ${f.path}`}
                  >
                    {copiedPath === f.path ? 'Copied ✓' : 'Copy'}
                  </button>
                </div>
                {open === i && (
                  <pre>
                    <code>{f.content}</code>
                  </pre>
                )}
              </div>
            ))}
          </>
        )}

        {previewTab === 'guide' && (
          <div className="factory__guide">
            <div className="factory__guideBlock">
              <h3>{scm === 'gitlab' ? '1. GitLab CI Factory Jobs' : '1. Interactive @fa Universal Agent'}</h3>
              <p>
                {scm === 'gitlab' ? (
                  <>GitLab does not generate the GitHub Actions-only <code>@fa</code> conversational workflow.</>
                ) : faEnabled ? (
                  <>
                    <code>.github/workflows/fa-universal.yml</code> is <strong>enabled</strong> in your setup.
                  </>
                ) : (
                  <>
                    <code>.github/workflows/fa-universal.yml</code> is currently disabled (toggle it on in Step 2).
                  </>
                )}
              </p>
              {scm === 'gitlab' ? (
                <ul>
                  <li>
                    The GitLab Issue-event webhook starts the poller for any issue event; the poller filters for assigned
                    or labeled work.
                  </li>
                  <li>Merge-request pipelines can run the review or rework runner based on labels.</li>
                  <li>Approved merge requests by {cfg.machine || 'ai-teammate'} are queued to merge after pipeline success.</li>
                  <li>Manual agent runs are available from GitLab Build → Pipelines using ticket and runner variables.</li>
                </ul>
              ) : (
                <ul>
                  <li>
                    <strong>Trigger in Issues &amp; PRs:</strong> Comment <code>@fa &lt;your instruction&gt;</code> on any
                    Issue or Pull Request, or open a new Issue containing <code>@fa</code>.
                  </li>
                  <li>
                    <strong>~2s Cold Start:</strong> The workflow downloads precompiled native binaries (<code>fa</code> +{' '}
                    <code>dmtools-dart</code>), prefetches the Issue/PR thread in parallel, and launches immediately
                    without checkout action overhead.
                  </li>
                  <li>
                    <strong>Single-Queue Session Continuation:</strong> Conversation history is stored on orphan branch{' '}
                    <code>fa-sess/gh-&lt;owner&gt;-&lt;repo&gt;-&lt;issue&gt;</code>. Follow-up <code>@fa</code> comments
                    resume the exact same session and drain in order.
                  </li>
                  <li>
                    <strong>Durable Project Memory:</strong>{' '}
                    {memEnabled ? (
                      <>
                        With <code>.fah/config.yaml</code> enabled, durable facts are saved under{' '}
                        <code>.fah/memory/</code> and committed back to your branch automatically.
                      </>
                    ) : (
                      <>Enable Project Memory in Step 2 to persist learned conventions in .fah/memory/.</>
                    )}
                  </li>
                  <li>
                    <strong>Real-Time Mid-Turn Steering (DAP Hub):</strong> Each running agent registers on the DAP Hub as{' '}
                    <code>DAP_AGENT_NAME=gh-&lt;owner&gt;-&lt;repo&gt;-&lt;issue&gt;</code> when secret{' '}
                    <code>FA_DAP_INVITE</code> (or <code>DAP_HUB_URL</code> + <code>DAP_CLIENT_SECRET</code>) is set,
                    allowing you or other agents to steer it live mid-turn via <code>agent_message</code>.
                  </li>
                </ul>
              )}
            </div>

            <div className="factory__guideBlock">
              <h3>2. {scm === 'gitlab' ? 'GitLab Issue-to-MR Teammate' : 'Autonomous Issue-to-PR Teammate'}</h3>
              {scm === 'gitlab' ? (
                <ul>
                  <li>
                    Assign an open issue to <code>{cfg.machine || 'ai-teammate'}</code> or add <code>agent:dev</code>,
                    <code>agent:review</code>, or <code>agent:rework</code>; the poller selects a generated runner.
                  </li>
                  <li>
                    Merge requests labeled <code>agent:review</code> or <code>agent:rework</code> are processed by their
                    corresponding runner. A <code>pr_approved</code> label queues eligible machine-authored MRs to merge
                    after pipeline success.
                  </li>
                </ul>
              ) : (
                <ul>
                  <li>
                    <strong>Assign to {cfg.machine || 'ai-teammate'}:</strong> Assign any GitHub Issue to{' '}
                    <code>{cfg.machine || 'ai-teammate'}</code> (or add label <code>agent:dev</code>) to trigger the
                    autonomous development leg.
                  </li>
                  <li>
                    <strong>State Machine &amp; Quality Gate:</strong> Once a PR is opened (<code>ai_developed</code>),
                    the state machine dispatches your quality gate (
                    <code>{isCiEnabled(cfg) ? cfg.ciFile || 'quality.yml' : 'none'}</code>), runs AI code review (
                    <code>agent:review</code>), and auto-merges when approved (<code>pr_approved</code>).
                  </li>
                </ul>
              )}
            </div>

            <div className="factory__guideBlock">
              <h3>3. Local CLI Installation (Optional)</h3>
              <p>Run the same native Dart binaries locally on macOS, Linux, or Windows:</p>
              <pre>
                <code>{`# Install FA harness
curl -fsSL https://raw.githubusercontent.com/IstiN/flutter_agent_harness/main/install.sh | bash

# Install DMTools native CLI (dmtools-dart)
curl -fsSL https://github.com/epam/dmtools-dart/releases/latest/download/install.sh | bash`}</code>
              </pre>
            </div>
          </div>
        )}

        {previewTab === 'checklist' && (
          <div className="factory__guide">
            <div className="factory__guideHeadRow">
              <h3>Generated PR Description &amp; Owner Checklist</h3>
              <button
                type="button"
                className="factory__copyBtn"
                onClick={() => copyContent('pr-body', prBody({ ...cfg, repo: cfg.repo || 'owner/repo' }))}
              >
                {copiedPath === 'pr-body' ? 'Copied ✓' : 'Copy Markdown'}
              </button>
            </div>
            <pre className="factory__checklistPre">
              <code>{prBody({ ...cfg, repo: cfg.repo || 'owner/repo' })}</code>
            </pre>
          </div>
        )}
      </aside>
    </div>
  );
}
