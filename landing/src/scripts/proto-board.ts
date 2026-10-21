// Circuit-hero client: fetches the three factories' live state snapshots,
// routes lanes as circuit traces from the cube, and renders real tickets
// in the detail modal (lane view → ticket drawer view).
import { factories, laneOrder, backlogColumns, badgeLabels, terminals, mixColor, type Terminal } from '../data/protoBoard';
import {
  tokenTotals, laneActive, queueWait, sumTokensMap, searchCount,
  filterFactories, mergedArchiveFromSearch, qualifyAll,
  histSpans, phaseAggregate, laneDwell, tokensFor, mergesPerDay, mergeDayStats, medianMs,
  type StatCard, type TokenRow, type TokenTotals, type ArchiveRow, type MergedArchive,
} from './boardStats';

type Card = StatCard & {
  author?: string; assignee?: string; labels?: string[];
  url?: string; branch?: string;
  bucket?: string;
  mergedAt?: string;
};
type Snapshot = {
  factory: string; repo: string; tick: { at: string };
  lanes: Record<string, Card[]>; backlog?: Record<string, Card[]>;
};

const ALL_LANES = [...laneOrder, ...backlogColumns];
const TITLES: Record<string, string> = Object.fromEntries(ALL_LANES.map((l) => [l.id, l.title]));

const board = document.getElementById('board') as HTMLElement;
const svg = document.getElementById('wires') as unknown as SVGSVGElement;
const cube = document.getElementById('cube') as HTMLElement;
const modal = document.getElementById('phase-modal') as HTMLElement;
const statusEl = document.getElementById('tick-status') as HTMLElement;
const loadLine = document.getElementById('load-status') as HTMLElement;
const tabsBox = document.getElementById('factory-select') as HTMLElement;
const selectBtn = document.getElementById('factory-btn') as HTMLButtonElement;
const selectBtnName = document.getElementById('factory-btn-name') as HTMLElement;
const selectBtnDot = document.getElementById('factory-btn-dot') as HTMLElement;
const selectMenu = document.getElementById('factory-menu') as HTMLElement;
const selectSearch = document.getElementById('factory-search') as HTMLInputElement;
const selectList = document.getElementById('factory-list') as HTMLElement;
let selectHighlight = 0;
const mobileLanes = document.getElementById('mobile-lanes') as HTMLElement;

const STALE_AFTER_MS = 30 * 60 * 1000;
const compact = window.matchMedia('(max-width: 900px)');
const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

let activeIdx = 0;
let st: Snapshot | null = null;
const cache: Record<string, Snapshot> = {};
let wires: { laneId: string; path: SVGPathElement; color: number[] }[] = [];
let pulseFrame = 0;

/* ---------------- data ---------------- */

async function loadFactory(i: number): Promise<Snapshot> {
  const f = factories[i];
  if (cache[f.id]) return cache[f.id];
  const res = await fetch(f.stateUrl, { cache: 'no-store' });
  if (!res.ok) throw new Error('HTTP ' + res.status);
  const json = (await res.json()) as Snapshot;
  cache[f.id] = json;
  return json;
}

function cardsIn(laneId: string): Card[] {
  if (!st) return [];
  return st.lanes?.[laneId] ?? st.backlog?.[laneId] ?? [];
}

function allCards(): Card[] {
  return ALL_LANES.flatMap((l) => cardsIn(l.id));
}

function tickMs(): number {
  const t = st?.tick?.at ? Date.parse(st.tick.at) : NaN;
  return Number.isNaN(t) ? Date.now() : t;
}

function cardKey(c: Card): string { return String(c.pr ?? c.issue ?? '—'); }
function cardKind(c: Card): 'pull' | 'issues' { return c.pr != null ? 'pull' : 'issues'; }
function cardUrl(c: Card): string {
  if (typeof c.url === 'string' && c.url) return c.url;
  const repo = st?.repo ?? '';
  return c.pr != null ? `https://github.com/${repo}/pull/${c.pr}` : `https://github.com/${repo}/issues/${c.issue}`;
}
function cardLastAt(c: Card): string | null {
  const h = c.history ?? [];
  return (h.length ? h[h.length - 1].at : undefined) ?? c.prCreated ?? c.devStartedAt ?? null;
}
function cardTokens(c: Card): number {
  return tokensFor(c, tokensStream()).reduce((n, t) => n + (t.total ?? 0), 0);
}
/** Raw per-card token stream for the active factory (pr-<N>/issue-<N> → rows). */
const tokensStreams: Record<string, Record<string, TokenRow[]> | null> = {};
function tokensStream(): Record<string, TokenRow[]> | null {
  return st ? tokensStreams[st.factory] ?? null : null;
}
function fmtTokens(n: number): string {
  if (n >= 1e6) return (n / 1e6).toFixed(1) + 'M';
  if (n >= 1e3) return Math.round(n / 1e3) + 'k';
  return String(n);
}

const tokenTotalsEl = document.getElementById('token-totals') as HTMLElement;
function renderTokenTotals() {
  if (!st) return;
  // Prefer the published tokens stream (all-time, survives snapshot windows);
  // fall back to tokens embedded in snapshot cards while the stream loads.
  const fromFile = ledgerCache[ledgerKey()]?.tokens;
  const t = fromFile ?? tokenTotals(allCards());
  if (!t.legs) {
    tokenTotalsEl.textContent = 'TOKENS —';
    tokenTotalsEl.title = 'This factory does not publish per-leg token usage yet (read = prompt + cache, write = completion)';
    return;
  }
  const sum = t.read + t.write + t.unsplit;
  tokenTotalsEl.textContent =
    'TOKENS · R ' + fmtTokens(t.read) + ' · W ' + fmtTokens(t.write);
  tokenTotalsEl.title =
    (fromFile ? 'All-time token stream (data/' + factories[activeIdx].tokensAsset + ' on the factory-data branch)'
              : 'Token spend across snapshot cards') +
    ' · read = prompt + cacheRead, write = completion' +
    ' · ' + fmtTokens(sum) + ' total across ' + t.legs + ' legs' +
    (t.unsplit ? ' · +' + fmtTokens(t.unsplit) + ' rows carry no prompt/completion split' : '');
}

/* ---------------- ledger: all-time factory stats ---------------- */
// The snapshot is a live window (open work + 24h merged), so card-level
// sums miss months of factory history. The ledger restores it from two
// durable sources: the factory's published tokens stream (data/<asset>
// on the factory-data branch — accumulates every leg) and the GitHub
// search API (machine-authored merged PRs / machine-assigned closed
// issues). Sources are cached 10 min; a miss renders an honest dash.

const ledgerEl = document.getElementById('factory-ledger') as HTMLElement;
function ledgerKey(): string { return factories[activeIdx].id; }
const LEDGER_TTL_MS = 10 * 60 * 1000;
type Ledger = { tokens: TokenTotals | null; merged: number | null; issues: number | null; at: number };
const ledgerCache: Record<string, Ledger> = {};

async function fetchJson(url: string): Promise<unknown> {
  const res = await fetch(url, { cache: 'no-store' });
  if (!res.ok) throw new Error('HTTP ' + res.status);
  return res.json();
}

async function loadLedger(): Promise<void> {
  if (!st) return;
  const f = factories[activeIdx];
  const repo = st.repo;
  const cached = ledgerCache[f.id];
  if (cached && Date.now() - cached.at < LEDGER_TTL_MS) { renderLedger(f.id); return; }
  const q = (s: string) => 'https://api.github.com/search/issues?q=' + encodeURIComponent(s);
  const [tokens, merged, issues] = await Promise.all([
    fetchJson(`https://raw.githubusercontent.com/${repo}/factory-data/data/${f.tokensAsset}`)
      .then((d) => {
        const map = d as Record<string, TokenRow[]>;
        tokensStreams[f.id] = map;
        return sumTokensMap(map);
      })
      .catch(() => { tokensStreams[f.id] = null; return null; }),
    fetchJson(q(`repo:${repo} is:pr is:merged ${qualifyAll('author', f.machineAuthors)}`)).then(searchCount).catch(() => null),
    fetchJson(q(`repo:${repo} is:issue is:closed ${qualifyAll('assignee', f.machineAuthors)}`)).then(searchCount).catch(() => null),
  ]);
  ledgerCache[f.id] = { tokens, merged, issues, at: Date.now() };
  renderLedger(f.id);
}

function renderLedger(id: string): void {
  const f = factories.find((x) => x.id === id);
  const l = ledgerCache[id];
  if (!f || !l) return;
  const parts: string[] = [];
  if (l.merged != null) parts.push('merged ' + l.merged + ' PRs');
  if (l.issues != null) parts.push(l.issues + ' issues done');
  if (l.tokens) {
    parts.push('R ' + fmtTokens(l.tokens.read) + ' · W ' + fmtTokens(l.tokens.write));
    parts.push(l.tokens.legs + ' legs');
  }
  ledgerEl.textContent =
    'LEDGER ▸ ' + (parts.length ? parts.join(' · ') + ' · ALL TIME' : 'no all-time sources yet');
  ledgerEl.title =
    'All-time factory ledger (the snapshot above is a live window: open work + 24h merges)' +
    ' · merged = PRs authored by ' + f.machineAuthors.join(' / ') + ' · issues done = closed issues assigned to them' +
    ' · tokens = every leg in data/' + f.tokensAsset + ' on the factory-data branch' +
    (l.tokens ? '' : ' · this factory does not publish a tokens stream yet (dmtools-agents#1108)');
  updateMergedLabel(l.merged);
  renderTokenTotals();
}

/** Patch the merged_recent terminal label with the Σ all-time count in place. */
function updateMergedLabel(allTime: number | null): void {
  if (allTime == null) return;
  const label = svg.querySelector<SVGTextElement>('.board__label[data-lane="merged_recent"]');
  if (!label) return;
  const txt = label.textContent ?? '';
  if (txt.includes('Σ')) label.textContent = txt.replace(/ · Σ\d+.*$/, '') + ' · Σ' + allTime;
  else label.textContent = txt + ' · Σ' + allTime;
}
function humanize(ms: number): string {
  if (ms < 0) ms = 0;
  const m = Math.floor(ms / 60000);
  if (m < 1) return Math.max(1, Math.round(ms / 1000)) + 's';
  if (m < 60) return m + 'm';
  const h = Math.floor(m / 60);
  if (h < 24) return h + 'h ' + (m % 60) + 'm';
  return Math.floor(h / 24) + 'd ' + (h % 24) + 'h';
}
function since(iso: string): number { return Date.now() - new Date(iso).getTime(); }

/* ---------------- wires ---------------- */

const SVG_NS = 'http://www.w3.org/2000/svg';
function el(name: string, attrs: Record<string, string | number>, parent?: Element): SVGElement {
  const node = document.createElementNS(SVG_NS, name);
  for (const k in attrs) node.setAttribute(k, String(attrs[k]));
  if (parent) parent.appendChild(node);
  return node;
}
function rgb(c: number[], a?: number): string { return `rgba(${c[0]},${c[1]},${c[2]},${a ?? 1})`; }
function pad2(n: number): string { return String(n).padStart(2, '0'); }

function route(t: Terminal, padX: number, padY: number, tx: number, ty: number): string {
  if (t.side === 'left' || t.side === 'right') {
    const dir = t.side === 'left' ? -1 : 1;
    const sx = padX + dir * 40;
    let ex = tx - dir * (Math.abs(ty - padY) + 18);
    if ((dir > 0 && ex < sx) || (dir < 0 && ex > sx)) ex = sx;
    return `M ${padX} ${padY} L ${sx} ${padY} L ${ex} ${ty} L ${tx} ${ty}`;
  }
  if (Math.abs(tx - padX) < 2) return `M ${padX} ${padY} L ${tx} ${ty}`;
  // Top/bottom: straight diagonal from the cube edge, short riser into the dot.
  return `M ${padX} ${padY} L ${tx} ${ty + (t.side === 'top' ? 14 : -14)} L ${tx} ${ty}`;
}

function draw() {
  if (pulseFrame) cancelAnimationFrame(pulseFrame);
  while (svg.firstChild) svg.removeChild(svg.firstChild);
  wires = [];
  if (compact.matches || !st) return;

  const b = board.getBoundingClientRect();
  const c = cube.getBoundingClientRect();
  svg.setAttribute('viewBox', `0 0 ${b.width} ${b.height}`);
  svg.setAttribute('width', String(b.width));
  svg.setAttribute('height', String(b.height));

  const defs = el('defs', {}, svg);
  const filter = el('filter', { id: 'wireGlow', x: '-60%', y: '-60%', width: '220%', height: '220%' }, defs);
  el('feGaussianBlur', { stdDeviation: '3.4', result: 'b' }, filter);
  const merge = el('feMerge', {}, filter);
  el('feMergeNode', { in: 'b' }, merge);
  el('feMergeNode', { in: 'SourceGraphic' }, merge);

  terminals.forEach((t, i) => {
    const cards = cardsIn(t.laneId);
    const tx = (t.x / 100) * b.width;
    const ty = (t.y / 100) * b.height;
    let padX: number, padY: number;
    if (t.side === 'left') { padX = c.left - b.left; padY = c.top - b.top + c.height / 2 + (-66 + i * 30); }
    else if (t.side === 'right') { padX = c.right - b.left; padY = c.top - b.top + c.height / 2 + (-60 + (i - 4) * 30); }
    else {
      // Spread the pads along the cube's top edge so the up-fan doesn't merge.
      const topIdx = terminals.filter((x) => x.side === 'top').indexOf(t);
      const topCount = terminals.filter((x) => x.side === 'top').length;
      const frac = topCount > 1 ? topIdx / (topCount - 1) : 0.5;
      padX = c.left - b.left + c.width * (0.08 + 0.84 * frac);
      padY = c.top - b.top;
    }

    const color = mixColor(t.x / 100);
    const active = laneActive(t.laneId, cards, tickMs());
    const path = el('path', {
      d: route(t, padX, padY, tx, ty),
      class: 'board__wire',
      stroke: rgb(color, cards.length ? 0.45 : 0.2),
      'data-lane': t.laneId,
    }, svg);
    el('rect', { x: padX - 4, y: padY - 4, width: 8, height: 8, class: 'board__pad', stroke: rgb(color, 0.9) }, svg);

    const g = el('g', {
      class: 'board__term', 'data-lane': t.laneId, tabindex: '0', role: 'button',
      'aria-label': `${TITLES[t.laneId]} — ${cards.length} tickets`,
    }, svg);
    // Activity language: filled + glow = work in flight; hollow dashed ring =
    // cards parked/waiting (queue or stalled lane); stroke-only = empty.
    if (cards.length && active) {
      el('circle', { cx: tx, cy: ty, r: 5, fill: rgb(color, 0.35), filter: 'url(#wireGlow)' }, g);
      el('circle', { cx: tx, cy: ty, r: 3.2, fill: rgb(color, 1) }, g);
    } else if (cards.length) {
      el('circle', {
        cx: tx, cy: ty, r: 4.4, fill: '#060606', stroke: rgb(color, 0.9),
        'stroke-width': 1.4, 'stroke-dasharray': '2.6 2.2', class: 'is-waiting',
      }, g);
    } else {
      el('circle', { cx: tx, cy: ty, r: 4, fill: '#060606', stroke: rgb(color, 0.9), 'stroke-width': 1.4 }, g);
    }
    el('circle', { cx: tx, cy: ty, r: 14, fill: 'transparent', class: 'board__hit' }, g);

    // Waiting queues show how long their head has been queued.
    const wait = queueWait(t.laneId, cards, tickMs());
    // Merged lane also shows the all-time machine merge count (Σ) once the
    // ledger has loaded — the snapshot count above is a 24h window by design.
    const allTime = t.laneId === 'merged_recent' ? ledgerCache[st?.factory ?? '']?.merged : undefined;
    const baseText = `${pad2(i + 1)} · ${TITLES[t.laneId]} · ${cards.length}` +
      (allTime != null ? ' · Σ' + allTime : '');

    const label = el('text', { class: 'board__label', fill: rgb(color, cards.length ? 0.85 : 0.4), 'data-lane': t.laneId }, g);
    if (t.side === 'left') { label.setAttribute('x', String(tx + 4)); label.setAttribute('text-anchor', 'start'); label.setAttribute('y', String(ty + 15)); }
    else if (t.side === 'right') { label.setAttribute('x', String(tx - 4)); label.setAttribute('text-anchor', 'end'); label.setAttribute('y', String(ty + 15)); }
    else if (t.labelSide === 'left') { label.setAttribute('x', String(tx - 11)); label.setAttribute('text-anchor', 'end'); label.setAttribute('y', String(ty + 4)); }
    else { label.setAttribute('x', String(tx + 11)); label.setAttribute('text-anchor', 'start'); label.setAttribute('y', String(ty + 4)); }
    if (wait) {
      label.textContent = '';
      const main = document.createElementNS(SVG_NS, 'tspan');
      main.textContent = baseText;
      const clock = document.createElementNS(SVG_NS, 'tspan');
      clock.setAttribute('class', 'board__wait');
      clock.textContent = ' · ⌛' + humanize(wait.headAgeMs);
      label.appendChild(main);
      label.appendChild(clock);
    } else {
      label.textContent = baseText;
    }

    wires.push({ laneId: t.laneId, path: path as SVGPathElement, color });

    g.addEventListener('mouseenter', () => hot(t.laneId, true));
    g.addEventListener('mouseleave', () => hot(t.laneId, false));
    g.addEventListener('click', () => openLane(t.laneId));
    g.addEventListener('keydown', (ev) => {
      if (ev.key === 'Enter' || ev.key === ' ') { ev.preventDefault(); openLane(t.laneId); }
    });
  });

  animateIn();
  renderTokenTotals();
  startPulses();
}

function hot(laneId: string, on: boolean) {
  const w = wires.find((x) => x.laneId === laneId);
  if (!w) return;
  w.path.classList.toggle('is-hot', on);
  const label = svg.querySelector(`.board__label[data-lane="${laneId}"]`);
  if (label) label.classList.toggle('is-hot', on);
}

function animateIn() {
  wires.forEach((w, i) => {
    if (reduceMotion) return;
    const len = w.path.getTotalLength();
    w.path.style.transition = 'none';
    w.path.style.strokeDasharray = String(len);
    w.path.style.strokeDashoffset = String(len);
    void w.path.getBoundingClientRect();
    w.path.style.transition = `stroke-dashoffset 850ms cubic-bezier(0.22,0.61,0.36,1) ${150 + i * 50}ms`;
    w.path.style.strokeDashoffset = '0';
  });
}

function startPulses() {
  if (reduceMotion) return;
  // A traveling pulse means work is genuinely in flight on that leg:
  // non-empty AND active per boardStats (storage lanes and parked cards
  // never pulse — waiting is drawn as a hollow ring instead).
  const now = tickMs();
  const travelers = wires
    .filter((w) => laneActive(w.laneId, cardsIn(w.laneId), now))
    .map((w, i) => {
    const dot = el('circle', { r: 2.4, fill: rgb(w.color, 0.95), filter: 'url(#wireGlow)', class: 'board__pulse' }, svg);
    return { dot, path: w.path, offset: i * 0.29, speed: 2400 + (i % 5) * 380 };
  });
  const t0 = performance.now();
  const tick = (now: number) => {
    travelers.forEach((t) => {
      const len = t.path.getTotalLength();
      const pos = (((now - t0) / t.speed + t.offset) % 1) * len;
      const pt = t.path.getPointAtLength(pos);
      t.dot.setAttribute('cx', String(pt.x));
      t.dot.setAttribute('cy', String(pt.y));
    });
    pulseFrame = requestAnimationFrame(tick);
  };
  pulseFrame = requestAnimationFrame(tick);
}

function updateStatus() {
  if (!st) { statusEl.textContent = '—'; return; }
  const age = since(st.tick.at);
  const stale = age > STALE_AFTER_MS;
  statusEl.textContent = (stale ? 'STALE · ' : 'LIVE · ') + humanize(age) + ' ago';
  statusEl.classList.toggle('is-stale', stale);
}

/* ---------------- modal: lane view ---------------- */

/** Every card in the snapshot: all lanes + backlog columns. */
function snapshotCards(): Card[] {
  if (!st) return [];
  const out: Card[] = [];
  for (const lane of ALL_LANES) out.push(...(st.lanes[lane.id] ?? []));
  return out;
}

/** AVG dwell strip in the lane header: how long cards spend in this lane. */
function renderLaneAvg(laneId: string) {
  const strip = document.getElementById('lane-avg') as HTMLElement;
  strip.innerHTML = '';
  if (!st) return;
  const now = Date.now();
  const d = laneDwell(snapshotCards(), laneId, now);
  if (!d.n) { strip.hidden = true; return; }
  strip.hidden = false;
  strip.title = 'Across every snapshot card that passed this lane (open work + 24h merged).' +
    ' Dwell = total time a card spent in this lane.';
  const chips: [string, string][] = [
    ['through lane', d.n + ' card' + (d.n === 1 ? '' : 's')],
    ['avg dwell', humanize(d.avgMs)],
    ['median dwell', humanize(d.medianMs)],
    ['max dwell', humanize(d.maxMs)],
  ];
  if (d.lead) {
    chips.push(['avg lead → merge', humanize(d.lead.avgMs)]);
    chips.push(['median lead', humanize(d.lead.medianMs)]);
  }
  chips.forEach(([k, v]) => {
    const chip = document.createElement('div');
    chip.className = 'laneAvg__chip';
    const kk = document.createElement('span');
    kk.className = 'laneAvg__k';
    kk.textContent = k;
    const vv = document.createElement('span');
    vv.className = 'laneAvg__v';
    vv.textContent = v;
    chip.appendChild(kk);
    chip.appendChild(vv);
    strip.appendChild(chip);
  });
}

function openLane(laneId: string) {
  if (!st) return;
  showLaneView();
  const cards = cardsIn(laneId);
  (document.getElementById('lane-eyebrow') as HTMLElement).textContent =
    'LANE · ' + st.factory.toUpperCase();
  (document.getElementById('lane-title') as HTMLElement).textContent =
    TITLES[laneId] ?? laneId;
  const allTime = ledgerCache[ledgerKey()]?.merged;
  (document.getElementById('lane-summary') as HTMLElement).textContent =
    cards.length + ' ticket' + (cards.length === 1 ? '' : 's') + ' · snapshot ' + humanize(since(st.tick.at)) + ' ago' +
    (laneId === 'merged_recent' && allTime != null ? ' · ' + allTime + ' merged all-time' : '');
  renderLaneAvg(laneId);
  const box = document.getElementById('lane-tickets') as HTMLElement;
  box.innerHTML = '';
  if (!cards.length) {
    const p = document.createElement('p');
    p.className = 'phaseModal__empty';
    p.textContent = 'Empty lane — nothing in flight here right now.';
    box.appendChild(p);
  }
  cards.forEach((c) => box.appendChild(ticketCard(c, laneId)));
  if (laneId === 'merged_recent') appendMergedArchive(box);
  modal.hidden = false;
  document.body.style.overflow = 'hidden';
  (modal.querySelector('.phaseModal__close') as HTMLElement | null)?.focus();
}

/* ---------------- merged archive (beyond the 24h snapshot window) ---------------- */

type ArchiveState = { total: number | null; items: ArchiveRow[] | null; at: number };
const archiveCache: Record<string, ArchiveState> = {};
const ARCHIVE_TTL_MS = 10 * 60 * 1000;
let archiveSeq = 0;

async function fetchMergedArchive(): Promise<ArchiveState> {
  const f = factories[activeIdx];
  const repo = st?.repo ?? '';
  const cached = archiveCache[f.id];
  if (cached && Date.now() - cached.at < ARCHIVE_TTL_MS) return cached;
  const since = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
  const q = 'https://api.github.com/search/issues?q=' + encodeURIComponent(
    `repo:${repo} is:pr is:merged ${qualifyAll('author', f.machineAuthors)} merged:>${since}`) + '&per_page=100';
  let parsed: MergedArchive | null = null;
  try {
    parsed = mergedArchiveFromSearch(await fetchJson(q));
  } catch { parsed = null; }
  const next: ArchiveState = parsed
    ? { total: parsed.total, items: parsed.items, at: Date.now() }
    : { total: null, items: null, at: Date.now() };
  archiveCache[f.id] = next;
  return next;
}

async function appendMergedArchive(box: HTMLElement) {
  const seq = ++archiveSeq;
  const section = document.createElement('div');
  section.className = 'arcBox';
  const head = document.createElement('div');
  head.className = 'arcBox__head';
  head.textContent = 'ARCHIVE · BEYOND 24H';
  const body = document.createElement('div');
  body.className = 'arcBox__body';
  body.textContent = 'Loading merged history…';
  section.appendChild(head);
  section.appendChild(body);
  box.appendChild(section);
  const a = await fetchMergedArchive();
  if (seq !== archiveSeq || modal.hidden) return; // lane changed / closed meanwhile
  body.innerHTML = '';
  if (!a.items) {
    body.textContent = 'Merged history unavailable right now (GitHub rate limit).';
    return;
  }
  if (!a.items.length) {
    body.textContent = 'No merges by the machine in the last 30 days.';
    return;
  }
  const allTime = ledgerCache[factories[activeIdx].id]?.merged;
  head.textContent = 'ARCHIVE · BEYOND 24H — ' + a.items.length + ' merges in 30d' + (allTime != null ? ' · ' + allTime + ' all-time' : '');
  const list = document.createElement('div');
  list.className = 'arcList';

  // Throughput dashboard: per-day sparkline + derived metrics from the same payload.
  const nowMs = Date.now();
  const perDay = mergesPerDay(a.items, 30, nowMs);
  const dayStats = mergeDayStats(a.items, 30, nowMs);
  const leads = a.items.map((r) => r.leadMs).filter((v): v is number => typeof v === 'number' && v > 0);
  const maxN = Math.max(1, ...perDay.map((d) => d.n));
  const dash = document.createElement('div');
  dash.className = 'arcDash';
  const sparkWrap = document.createElement('div');
  const spark = document.createElement('div');
  spark.className = 'arcSpark';
  perDay.forEach((d) => {
    const bar = document.createElement('span');
    bar.className = 'arcSpark__bar' + (dayStats.best && d.date === dayStats.best.date ? ' is-best' : '');
    bar.style.height = d.n ? Math.max(8, Math.round((d.n / maxN) * 100)) + '%' : '2px';
    bar.title = d.date + ' · ' + d.n + ' merge' + (d.n === 1 ? '' : 's');
    spark.appendChild(bar);
  });
  const axis = document.createElement('div');
  axis.className = 'arcSpark__axis';
  axis.innerHTML = '<span>' + perDay[0].date + '</span><span>merges / day · 30d</span><span>' + perDay[29].date + '</span>';
  sparkWrap.appendChild(spark);
  sparkWrap.appendChild(axis);
  const chips = document.createElement('div');
  chips.className = 'arcChips';
  const chipPairs: [string, string, string][] = [
    ['merges 30d', String(dayStats.total), ''],
    ['avg / day', dayStats.perDay.toFixed(1), ''],
    ['best day', dayStats.best ? dayStats.best.date + ' · ' + dayStats.best.n : '—', dayStats.best ? dayStats.best.n + ' merges on ' + dayStats.best.date : ''],
    ['median lead', leads.length ? humanize(medianMs(leads)) : '—', 'created → merged, across archived PRs that report both stamps'],
    ['avg lead', leads.length ? humanize(leads.reduce((x, y) => x + y, 0) / leads.length) : '—', 'created → merged, 30d archive'],
  ];
  chipPairs.forEach(([k, v, tip]) => {
    const chip = document.createElement('div');
    chip.className = 'arcChip';
    if (tip) chip.title = tip;
    const kk = document.createElement('span');
    kk.className = 'arcChip__k';
    kk.textContent = k;
    const vv = document.createElement('span');
    vv.className = 'arcChip__v';
    vv.textContent = v;
    chip.appendChild(kk);
    chip.appendChild(vv);
    chips.appendChild(chip);
  });
  dash.appendChild(sparkWrap);
  dash.appendChild(chips);
  body.appendChild(dash);
  a.items.forEach((r) => {
    const row = document.createElement('a');
    row.className = 'arcRow';
    row.href = cardUrl({ pr: r.num });
    row.target = '_blank';
    row.rel = 'noopener';
    const id = document.createElement('span');
    id.className = 'arcRow__id';
    id.textContent = '#' + r.num;
    const title = document.createElement('span');
    title.className = 'arcRow__title';
    title.textContent = r.title;
    const when = document.createElement('span');
    when.className = 'arcRow__when';
    when.textContent = r.at ? new Date(r.at).toISOString().slice(5, 10) : '—';
    row.appendChild(id);
    row.appendChild(title);
    row.appendChild(when);
    list.appendChild(row);
  });
  body.appendChild(list);
}

function showLaneView() {
  (document.getElementById('lane-view') as HTMLElement).hidden = false;
  (document.getElementById('ticket-view') as HTMLElement).hidden = true;
}

function badgeChip(label: string): HTMLElement {
  const chip = document.createElement('span');
  chip.className = 'phaseModal__chip' + (badgeLabels.includes(label) ? ' phaseModal__chip--route' : '');
  chip.textContent = label;
  return chip;
}

function ticketCard(c: Card, laneId: string): HTMLElement {
  const card = document.createElement('button');
  card.type = 'button';
  card.className = 'tkCard';

  const head = document.createElement('div');
  head.className = 'tkCard__head';
  const id = document.createElement('span');
  id.className = 'tkCard__id';
  id.textContent = '#' + cardKey(c);
  head.appendChild(id);
  (c.labels ?? []).slice(0, 3).forEach((l) => head.appendChild(badgeChip(l)));
  const dur = document.createElement('span');
  dur.className = 'tkCard__dur';
  const lastAt = cardLastAt(c);
  dur.textContent = lastAt ? humanize(since(lastAt)) : '—';
  head.appendChild(dur);

  const title = document.createElement('p');
  title.className = 'tkCard__title';
  title.textContent = c.title ?? '(untitled)';

  const foot = document.createElement('div');
  foot.className = 'tkCard__foot';
  const status = document.createElement('span');
  status.className = 'tkCard__status';
  const tok = cardTokens(c);
  status.textContent = '▸ ' + (c.assignee ?? c.author ?? '—') + (tok ? ' · ' + fmtTokens(tok) + ' tok' : '');
  foot.appendChild(status);
  foot.appendChild(historyRail(c));

  card.appendChild(head);
  card.appendChild(title);
  card.appendChild(foot);
  card.addEventListener('click', () => openTicket(c, laneId));
  return card;
}

function historyRail(c: Card): HTMLElement {
  const wrap = document.createElement('div');
  wrap.className = 'tkRail';
  const h = c.history ?? [];
  h.forEach((_entry, i) => {
    if (i > 0) {
      const line = document.createElement('span');
      line.className = 'tkRail__line';
      wrap.appendChild(line);
    }
    const dot = document.createElement('span');
    const isLast = i === h.length - 1;
    dot.className = 'tkRail__dot' + (isLast ? ' is-here' : '');
    dot.title = h[i].state + (h[i].at ? ' · ' + h[i].at.slice(0, 16).replace('T', ' ') : '');
    wrap.appendChild(dot);
  });
  return wrap;
}

/* ---------------- modal: ticket drawer view ---------------- */

/** Phase family → timeline color class (dev / validate / review / queue / merged). */
function timelineHue(state: string): string {
  if (['development', 'pr_created', 'assigned', 'agent:dev queued'].includes(state)) return 'hue--dev';
  if (['pr_validation', 'validating'].includes(state)) return 'hue--validate';
  if (state === 'review' || state.includes('review')) return 'hue--review';
  if (['merged_recent', 'merged'].includes(state)) return 'hue--merged';
  if (['approved_queue', 'queued'].includes(state)) return 'hue--queue';
  return 'hue--hold'; // inbox, blocked, anything else
}

function openTicket(c: Card, laneId: string) {
  (document.getElementById('lane-view') as HTMLElement).hidden = true;
  (document.getElementById('ticket-view') as HTMLElement).hidden = false;

  (document.getElementById('ticket-eyebrow') as HTMLElement).textContent =
    'TICKET · ' + (st?.factory ?? '').toUpperCase() + ' · ' + cardKind(c).toUpperCase() + ' #' + cardKey(c);
  (document.getElementById('ticket-title') as HTMLElement).textContent = '#' + cardKey(c) + ' — ' + (c.title ?? '');
  (document.getElementById('ticket-gh') as HTMLElement).setAttribute('href', cardUrl(c));

  const chips = document.getElementById('ticket-chips') as HTMLElement;
  chips.innerHTML = '';
  (c.labels ?? []).forEach((l) => chips.appendChild(badgeChip(l)));
  if (c.author) chips.appendChild(badgeChip('author: ' + c.author));
  if (c.assignee) chips.appendChild(badgeChip('assignee: ' + c.assignee));
  if (c.branch) chips.appendChild(badgeChip(c.branch));

  const history = document.getElementById('ticket-history') as HTMLElement;
  history.innerHTML = '';
  const h = c.history ?? [];
  const nowMs = Date.now();
  const spans = histSpans(h, nowMs, c.mergedAt ?? null);
  const phases = phaseAggregate(spans);
  const maxPhase = Math.max(1, ...phases.map((p) => p.totalMs));

  // Aggregated flow: one row per phase with visits / avg / total — rework visible at a glance.
  const flow = document.getElementById('ticket-flow') as HTMLElement;
  flow.innerHTML = '';
  if (!phases.length) {
    const p = document.createElement('p');
    p.className = 'phaseModal__empty';
    p.textContent = 'No phase history recorded yet.';
    flow.appendChild(p);
  }
  phases.forEach((p) => {
    const row = document.createElement('div');
    row.className = 'tkFlow' + (p.state === h[h.length - 1]?.state ? ' is-here' : '');
    const rowTop = document.createElement('div');
    rowTop.className = 'tkFlow__row';
    const label = document.createElement('span');
    label.className = 'tkFlow__label';
    label.textContent = p.state;
    const visits = document.createElement('span');
    visits.className = 'tkFlow__visits';
    visits.textContent = p.visits > 1 ? '×' + p.visits : '';
    const avg = document.createElement('span');
    avg.className = 'tkFlow__avg';
    avg.title = 'avg per visit';
    avg.textContent = 'avg ' + humanize(p.avgMs);
    const total = document.createElement('span');
    total.className = 'tkFlow__total';
    total.textContent = humanize(p.totalMs);
    rowTop.appendChild(label); rowTop.appendChild(visits); rowTop.appendChild(avg); rowTop.appendChild(total);
    const bar = document.createElement('div');
    bar.className = 'tkFlow__bar';
    const fill = document.createElement('span');
    fill.style.width = Math.max(4, Math.round((p.totalMs / maxPhase) * 100)) + '%';
    bar.appendChild(fill);
    row.appendChild(rowTop); row.appendChild(bar);
    flow.appendChild(row);
  });

  // Proportional timeline: one stacked bar, every visit to scale.
  const tl = document.getElementById('ticket-timeline') as HTMLElement;
  tl.innerHTML = '';
  if (spans.length) {
    const bar = document.createElement('div');
    bar.className = 'tkTimeline';
    spans.forEach((s) => {
      const seg = document.createElement('span');
      seg.className = 'tkTimeline__seg ' + timelineHue(s.state);
      seg.style.flexGrow = String(Math.max(1, s.durMs / 1000));
      const hh = s.startMs ? new Date(s.startMs).toISOString().slice(11, 16) : '—';
      seg.title = s.state + ' · ' + humanize(s.durMs) + ' · from ' + hh + ' UTC';
      bar.appendChild(seg);
    });
    tl.appendChild(bar);
    const legend = document.createElement('p');
    legend.className = 'tkTimeline__legend';
    legend.textContent = spans.length + ' phase' + (spans.length === 1 ? '' : 's') +
      (c.mergedAt ? ' · ends at merge' : ' · ends now') + ' · hover for detail';
    tl.appendChild(legend);
  } else {
    const p = document.createElement('p');
    p.className = 'phaseModal__empty';
    p.textContent = 'Timeline unavailable (no timestamps).';
    tl.appendChild(p);
  }

  const maxDur = Math.max(1, ...spans.map((s) => s.durMs));
  h.forEach((entry, i) => {
    const next = h[i + 1]?.at;
    const dur = entry.at ? (next ? new Date(next).getTime() - new Date(entry.at).getTime() : since(entry.at)) : null;
    const li = document.createElement('li');
    li.className = 'tkHist' + (i === h.length - 1 ? ' is-here' : '');
    const row = document.createElement('div');
    row.className = 'tkHist__row';
    const label = document.createElement('span');
    label.className = 'tkHist__label';
    label.textContent = entry.state;
    const when = document.createElement('code');
    when.textContent = entry.at ? entry.at.slice(5, 16).replace('T', ' ') : '—';
    const durEl = document.createElement('span');
    durEl.className = 'tkHist__dur';
    durEl.textContent = dur != null ? humanize(dur) : '—';
    row.appendChild(label); row.appendChild(when); row.appendChild(durEl);
    const bar = document.createElement('div');
    bar.className = 'tkHist__bar';
    const fill = document.createElement('span');
    if (dur != null) fill.style.width = Math.round((dur / maxDur) * 100) + '%';
    bar.appendChild(fill);
    li.appendChild(row); li.appendChild(bar);
    history.appendChild(li);
  });
  const raw = document.getElementById('ticket-raw') as HTMLDetailsElement;
  raw.open = false;
  (raw.querySelector('summary') as HTMLElement).textContent =
    'Raw history · ' + h.length + ' transition' + (h.length === 1 ? '' : 's');

  const tokensBox = document.getElementById('ticket-tokens') as HTMLElement;
  tokensBox.innerHTML = '';
  const tokens = tokensFor(c, tokensStream());
  if (!tokens.length) {
    const p = document.createElement('p');
    p.className = 'phaseModal__empty';
    p.textContent = 'No token spend recorded for this card yet.';
    tokensBox.appendChild(p);
  } else {
    const table = document.createElement('table');
    table.className = 'tkTokens';
    const thead = document.createElement('thead');
    thead.innerHTML = '<tr><th>Leg</th><th>Model</th><th>Read</th><th>Write</th><th>Tokens</th><th>Cache read</th></tr>';
    table.appendChild(thead);
    const tbody = document.createElement('tbody');
    tokens.forEach((t) => {
      const tr = document.createElement('tr');
      const splitless = t.prompt == null && t.completion == null;
      const read = (t.prompt ?? 0) + (t.cacheRead ?? 0);
      const cells = [
        t.leg, t.model ?? '—',
        splitless ? '—' : fmtTokens(read),
        splitless ? '—' : fmtTokens(t.completion ?? 0),
        fmtTokens(t.total ?? 0), t.cacheRead ? fmtTokens(t.cacheRead) : '—',
      ];
      cells.forEach((v) => {
        const td = document.createElement('td');
        td.textContent = String(v);
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    tokensBox.appendChild(table);
    const agg = tokenTotals([{ tokens } as StatCard]);
    const total = document.createElement('p');
    total.className = 'phaseModal__empty';
    total.textContent = 'Total: ' + fmtTokens(cardTokens(c)) + ' tokens across ' + tokens.length + ' legs · R ' + fmtTokens(agg.read) + ' / W ' + fmtTokens(agg.write) + (agg.unsplit ? ' / +' + fmtTokens(agg.unsplit) + ' unsplit' : '') + ' · model ' + (tokens[tokens.length - 1]?.model ?? '—');
    tokensBox.appendChild(total);
  }

  let rework = 0;
  const seenStates: string[] = [];
  (c.history ?? []).forEach((e) => {
    if (seenStates.includes(e.state)) rework += 1;
    else seenStates.push(e.state);
  });
  const stats = document.getElementById('ticket-stats') as HTMLElement;
  stats.innerHTML = '';
  const totalMs = spans.length ? spans[spans.length - 1].endMs - spans[0].startMs : null;
  const pairs: [string, string][] = [
    [c.mergedAt ? 'Lead → merge' : 'Lead time', totalMs != null ? humanize(totalMs) : '—'],
    ['Phase visits', String(h.length)],
    ['Rework loops', '×' + rework],
  ];
  pairs.forEach(([k, v]) => {
    const dt = document.createElement('dt');
    dt.textContent = k;
    const dd = document.createElement('dd');
    dd.textContent = v;
    stats.appendChild(dt);
    stats.appendChild(dd);
  });

  (document.getElementById('ticket-gh') as HTMLElement).setAttribute('href', cardUrl(c));
  (document.getElementById('ticket-back') as HTMLElement).onclick = () => openLane(laneId);
}

/* ---------------- boot ---------------- */

function setActive(i: number) {
  activeIdx = i;
  const f = factories[i];
  selectBtnName.textContent = f.name;
  selectBtnDot.style.background = f.accent;
  selectBtnDot.style.boxShadow = '0 0 6px ' + f.accent;
  closeFactoryMenu();
  document.documentElement.style.setProperty('--factory-accent', f.accent);
  loadLine.textContent = 'Loading ' + f.name + ' state…';
  loadFactory(i)
    .then((json) => {
      st = json;
      loadLine.hidden = true;
      updateStatus();
      draw();
      renderMobileLanes();
      loadLedger();
    })
    .catch((err) => {
      loadLine.textContent = 'Failed to load ' + f.name + ' state (' + (err as Error).message + ')';
    });
}

/* ---------------- repository picker (dropdown + quick search) ---------------- */

function renderFactoryList() {
  const matches = filterFactories(factories as unknown as { id: string; name: string }[], selectSearch.value);
  if (selectHighlight >= matches.length) selectHighlight = Math.max(0, matches.length - 1);
  selectList.innerHTML = '';
  matches.forEach((f, pos) => {
    const realIdx = (factories as unknown as { id: string }[]).indexOf(f as unknown as { id: string });
    const li = document.createElement('li');
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'board__selectOpt' + (realIdx === activeIdx ? ' is-active' : '') + (pos === selectHighlight ? ' is-hl' : '');
    btn.dataset.factory = String(realIdx);
    const dot = document.createElement('span');
    dot.className = 'board__selectDot';
    dot.style.background = factories[realIdx].accent;
    btn.appendChild(dot);
    const name = document.createElement('span');
    name.className = 'board__selectName';
    name.textContent = f.name;
    btn.appendChild(name);
    if (realIdx === activeIdx) {
      const mark = document.createElement('span');
      mark.className = 'board__selectMark';
      mark.textContent = '● live';
      btn.appendChild(mark);
    }
    btn.addEventListener('click', () => setActive(realIdx));
    btn.addEventListener('mouseenter', () => {
      selectHighlight = pos;
      selectList.querySelectorAll('.board__selectOpt').forEach((o) => o.classList.remove('is-hl'));
      btn.classList.add('is-hl');
    });
    li.appendChild(btn);
    selectList.appendChild(li);
  });
  if (!matches.length) {
    const li = document.createElement('li');
    li.className = 'board__selectEmpty';
    li.textContent = 'No repository matches “' + selectSearch.value.trim() + '”';
    selectList.appendChild(li);
  }
}

function openFactoryMenu() {
  selectMenu.hidden = false;
  selectBtn.setAttribute('aria-expanded', 'true');
  selectSearch.value = '';
  selectHighlight = activeIdx;
  renderFactoryList();
  selectSearch.focus();
}

function closeFactoryMenu() {
  selectMenu.hidden = true;
  selectBtn.setAttribute('aria-expanded', 'false');
}

function moveHighlight(delta: number) {
  const opts = selectList.querySelectorAll('.board__selectOpt');
  if (!opts.length) return;
  selectHighlight = (selectHighlight + delta + opts.length) % opts.length;
  opts.forEach((o) => o.classList.remove('is-hl'));
  (opts[selectHighlight] as HTMLElement).classList.add('is-hl');
  (opts[selectHighlight] as HTMLElement).scrollIntoView({ block: 'nearest' });
}

selectBtn.addEventListener('click', () => (selectMenu.hidden ? openFactoryMenu() : closeFactoryMenu()));
selectSearch.addEventListener('input', () => { selectHighlight = 0; renderFactoryList(); });
selectSearch.addEventListener('keydown', (ev) => {
  if (ev.key === 'ArrowDown') { ev.preventDefault(); moveHighlight(1); }
  else if (ev.key === 'ArrowUp') { ev.preventDefault(); moveHighlight(-1); }
  else if (ev.key === 'Enter') {
    ev.preventDefault();
    const idx = Number((selectList.querySelector('.board__selectOpt.is-hl') as HTMLElement | null)?.dataset.factory ?? NaN);
    if (!Number.isNaN(idx)) setActive(idx);
  } else if (ev.key === 'Escape') { ev.preventDefault(); closeFactoryMenu(); selectBtn.focus(); }
});
document.addEventListener('click', (ev) => {
  if (!selectMenu.hidden && !tabsBox.contains(ev.target as Node)) closeFactoryMenu();
});

function renderMobileLanes() {
  if (!compact.matches || !st) return;
  mobileLanes.innerHTML = '';
  ALL_LANES.forEach((lane) => {
    const cards = cardsIn(lane.id);
    const li = document.createElement('li');
    const btn = document.createElement('button');
    btn.className = 'board__mobileLane';
    btn.textContent = lane.title + ' · ' + cards.length;
    btn.addEventListener('click', () => openLane(lane.id));
    li.appendChild(btn);
    mobileLanes.appendChild(li);
  });
}

function closeModal() {
  modal.hidden = true;
  document.body.style.overflow = '';
}

modal.addEventListener('click', (e) => {
  if ((e.target as HTMLElement).closest('[data-close]')) closeModal();
});
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape' && !modal.hidden) closeModal();
});

let resizeTimer = 0;
window.addEventListener('resize', () => {
  clearTimeout(resizeTimer);
  resizeTimer = window.setTimeout(draw, 120);
});
compact.addEventListener?.('change', () => { draw(); renderMobileLanes(); });
if (document.fonts?.ready) document.fonts.ready.then(draw);
setInterval(() => { loadFactory(activeIdx).then(() => { updateStatus(); draw(); loadLedger(); }).catch(() => {}); }, 60000);

setActive(0);
