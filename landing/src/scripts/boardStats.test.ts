import { describe, it, expect } from 'vitest';
import {
  tokenTotals, laneActive, queueWait, cardLastStamp,
  WAITING_LANES,
  type StatCard,
} from './boardStats';

const TICK = Date.parse('2026-10-19T17:00:00Z');
const min = (n: number) => new Date(TICK - n * 60_000).toISOString();

describe('tokenTotals', () => {
  it('splits read (prompt + cacheRead) from write (completion)', () => {
    const cards: StatCard[] = [
      { tokens: [{ leg: 'review', prompt: 100, completion: 30, cacheRead: 3700, total: 130 }] },
      { tokens: [{ leg: 'dev', prompt: 10, completion: 5, cacheRead: 200, total: 15 }] },
    ];
    expect(tokenTotals(cards)).toEqual({ read: 4010, write: 35, unsplit: 0, legs: 2 });
  });

  it('treats absent cacheRead as 0 but never negative', () => {
    expect(tokenTotals([{ tokens: [{ prompt: 5, completion: 2 }] }]))
      .toEqual({ read: 5, write: 2, unsplit: 0, legs: 1 });
  });

  it('routes split-less rows (only total) to unsplit, not read', () => {
    const t = tokenTotals([{ tokens: [{ total: 999 }] }, { tokens: [{ prompt: 1, completion: 1 }] }]);
    expect(t.unsplit).toBe(999);
    expect(t.read).toBe(1);
    expect(t.legs).toBe(2);
  });

  it('is empty-safe and token-safe', () => {
    expect(tokenTotals([])).toEqual({ read: 0, write: 0, unsplit: 0, legs: 0 });
    expect(tokenTotals([{ pr: 1 }, { issue: 2 }])).toEqual({ read: 0, write: 0, unsplit: 0, legs: 0 });
  });
});

describe('laneActive', () => {
  const fresh = (at: string): StatCard => ({ history: [{ state: 'review', at }] });

  it('never pulses storage lanes even when full', () => {
    for (const lane of WAITING_LANES) {
      expect(laneActive(lane, [fresh(min(1))], TICK)).toBe(false);
    }
  });

  it('validating is active while non-empty (mutex run in flight)', () => {
    const old: StatCard = { history: [{ state: 'validating', at: '2026-10-01T00:00:00Z' }] };
    expect(laneActive('validating', [old], TICK)).toBe(true);
    expect(laneActive('validating', [], TICK)).toBe(false);
  });

  it('work lanes pulse only on recent entry (within the window)', () => {
    expect(laneActive('review', [fresh(min(5))], TICK)).toBe(true);
    expect(laneActive('development', [fresh(min(29))], TICK)).toBe(true);
    expect(laneActive('pr_validation', [fresh(min(31))], TICK)).toBe(false);
    const stale: StatCard = { history: [{ state: 'review', at: '2026-10-18T00:00:00Z' }] };
    expect(laneActive('review', [stale], TICK)).toBe(false);
  });

  it('a card with no timestamps is never treated as active', () => {
    expect(laneActive('pr_created', [{ pr: 7 }], TICK)).toBe(false);
  });

  it('empty lane is never active', () => {
    expect(laneActive('review', [], TICK)).toBe(false);
  });
});

describe('queueWait', () => {
  it('measures the approved_queue FIFO head via queuePos 1 and approvedAt', () => {
    const cards: StatCard[] = [
      { pr: 30, queuePos: 2, history: [{ state: 'approved_queue', at: min(10) }] },
      { pr: 28, queuePos: 1, approvedAt: min(45), history: [{ state: 'approved_queue', at: min(45) }] },
    ];
    expect(queueWait('approved_queue', cards, TICK)).toEqual({ depth: 2, headAgeMs: 45 * 60_000 });
  });

  it('falls back to the latest history stamp when approvedAt is absent', () => {
    const cards: StatCard[] = [{ pr: 3, queuePos: 1, history: [{ state: 'approved_queue', at: min(7) }] }];
    expect(queueWait('approved_queue', cards, TICK)).toEqual({ depth: 1, headAgeMs: 7 * 60_000 });
  });

  it('measures the backlog queued bucket by its first card', () => {
    const cards: StatCard[] = [
      { issue: 11, history: [{ state: 'queued', at: min(120) }] },
      { issue: 12, history: [{ state: 'queued', at: min(5) }] },
    ];
    expect(queueWait('queued', cards, TICK)).toEqual({ depth: 2, headAgeMs: 120 * 60_000 });
  });

  it('returns null for non-waiting lanes, empty lanes and stamp-less heads', () => {
    expect(queueWait('review', [{ pr: 1 }], TICK)).toBeNull();
    expect(queueWait('approved_queue', [], TICK)).toBeNull();
    expect(queueWait('approved_queue', [{ pr: 1, queuePos: 1 }], TICK)).toBeNull();
  });
});

describe('cardLastStamp', () => {
  it('picks the newest of devStartedAt / approvedAt / history / prCreated', () => {
    const c: StatCard = {
      prCreated: '2026-10-10T00:00:00Z',
      devStartedAt: '2026-10-15T00:00:00Z',
      history: [{ state: 'a', at: '2026-10-12T00:00:00Z' }, { state: 'b', at: '2026-10-17T00:00:00Z' }],
    };
    expect(cardLastStamp(c)).toBe('2026-10-17T00:00:00Z');
  });

  it('ignores garbage and returns null when nothing parses', () => {
    expect(cardLastStamp({ history: [{ state: 'x', at: 'not-a-date' }] })).toBeNull();
    expect(cardLastStamp({})).toBeNull();
  });
});

import { sumTokenRows, sumTokensMap, searchCount } from './boardStats';

describe('sumTokenRows', () => {
  it('sums raw rows with read/write split and snake_case cache fallback', () => {
    const rows = [
      { leg: 'dev', prompt: 100, completion: 10, cacheRead: 500, total: 110 },
      { leg: 'review', prompt: 20, completion: 5, cache_read: '700', total: 25 },
      { leg: 'x', total: 999 },
    ];
    const t = sumTokenRows(rows);
    expect(t.read).toBe(100 + 500 + 20 + 700);
    expect(t.write).toBe(15);
    expect(t.unsplit).toBe(999);
    expect(t.legs).toBe(3);
  });

  it('empty rows → zeroed totals', () => {
    expect(sumTokenRows([])).toEqual({ read: 0, write: 0, unsplit: 0, legs: 0 });
  });
});

describe('sumTokensMap', () => {
  it('sums every leg of every pr-N/issue-N bucket', () => {
    const map = {
      'pr-1': [{ prompt: 1, completion: 1 }, { prompt: 2, completion: 2 }],
      'issue-9': [{ prompt: 10, completion: 0, cacheRead: 100 }],
    };
    expect(sumTokensMap(map)).toEqual({ read: 113, write: 3, unsplit: 0, legs: 3 });
  });

  it('null map (no stream published) → null, never zero pretending success', () => {
    expect(sumTokensMap(null)).toBeNull();
  });
});

describe('searchCount', () => {
  it('reads total_count from a GitHub search response', () => {
    expect(searchCount({ total_count: 263, items: [] })).toBe(263);
  });

  it('returns null on rate-limit / error / garbage payloads', () => {
    expect(searchCount({ message: 'API rate limit exceeded' })).toBeNull();
    expect(searchCount(null)).toBeNull();
    expect(searchCount('nope')).toBeNull();
    expect(searchCount({})).toBeNull();
  });
});

import { filterFactories, mergedArchiveFromSearch } from './boardStats';

describe('filterFactories', () => {
  const fs = [
    { id: 'fa', name: 'flutter_agent_harness' },
    { id: 'dart', name: 'dmtools-dart' },
    { id: 'agents', name: 'dmtools-agents' },
  ];

  it('empty query returns all', () => {
    expect(filterFactories(fs, '')).toHaveLength(3);
    expect(filterFactories(fs, '   ')).toHaveLength(3);
  });

  it('matches by name or id substring, case-insensitive', () => {
    expect(filterFactories(fs, 'DART').map((f) => f.id)).toEqual(['dart']);
    expect(filterFactories(fs, 'dmtools').map((f) => f.id)).toEqual(['dart', 'agents']);
    expect(filterFactories(fs, 'harness').map((f) => f.id)).toEqual(['fa']);
    expect(filterFactories(fs, 'zzz')).toEqual([]);
  });
});

describe('mergedArchiveFromSearch', () => {
  it('maps search items to archive rows (newest first as returned)', () => {
    const json = {
      total_count: 263,
      items: [
        { number: 1701, title: 'add ledger', closed_at: '2026-10-19T10:00:00Z' },
        { number: 1699, title: 'fix goldens', closed_at: '2026-10-18T09:00:00Z' },
      ],
    };
    expect(mergedArchiveFromSearch(json)).toEqual({
      total: 263,
      items: [
        { num: 1701, title: 'add ledger', at: '2026-10-19T10:00:00Z', leadMs: null },
        { num: 1699, title: 'fix goldens', at: '2026-10-18T09:00:00Z', leadMs: null },
      ],
    });
  });

  it('caps items and skips broken rows', () => {
    const items = Array.from({ length: 60 }, (_, i) => ({ number: 100 - i, title: 'r' + i, closed_at: null }));
    items.push({ title: 'no number' } as unknown as { number: number; title: string; closed_at: null });
    const out = mergedArchiveFromSearch({ total_count: 99, items });
    expect(out).not.toBeNull();
    expect(out!.items).toHaveLength(50);
    expect(out!.items[0]).toEqual({ num: 100, title: 'r0', at: null, leadMs: null });
  });

  it('returns null on rate-limit / garbage payloads', () => {
    expect(mergedArchiveFromSearch({ message: 'API rate limit exceeded' })).toBeNull();
    expect(mergedArchiveFromSearch(null)).toBeNull();
    expect(mergedArchiveFromSearch({ total_count: 1 })).toBeNull();
  });
});

import { qualifyAll } from './boardStats';

describe('qualifyAll', () => {
  it('repeats the qualifier — GitHub search ORs repeated same-kind qualifiers', () => {
    expect(qualifyAll('author', ['ai-teammate', 'vabhzw'])).toBe('author:ai-teammate author:vabhzw');
    expect(qualifyAll('author', ['ai-teammate'])).toBe('author:ai-teammate');
  });

  it('empty authors → empty string (unqualified query)', () => {
    expect(qualifyAll('author', [])).toBe('');
  });
});

import { histSpans, phaseAggregate, medianMs, laneDwell, type HistoryEntry } from './boardStats';

const T0 = Date.parse('2026-10-21T10:00:00Z');
const iso = (ms: number) => new Date(ms).toISOString();

describe('histSpans', () => {
  it('splits history into sequential spans; last span runs to now', () => {
    const h: HistoryEntry[] = [
      { state: 'pr_created', at: iso(T0) },
      { state: 'validating', at: iso(T0 + 10 * 60000) },
      { state: 'approved_queue', at: iso(T0 + 13 * 60000) },
    ];
    const now = T0 + 43 * 60000;
    const spans = histSpans(h, now);
    expect(spans.map((s) => [s.state, s.durMs])).toEqual([
      ['pr_created', 10 * 60000],
      ['validating', 3 * 60000],
      ['approved_queue', 30 * 60000],
    ]);
  });

  it('endAt caps the last span (merged cards stop at mergedAt)', () => {
    const h: HistoryEntry[] = [
      { state: 'validating', at: iso(T0) },
      { state: 'merged', at: iso(T0 + 60 * 60000) },
    ];
    const spans = histSpans(h, T0 + 120 * 60000, iso(T0 + 90 * 60000));
    expect(spans[0].durMs).toBe(60 * 60000);
    expect(spans[1].durMs).toBe(30 * 60000);
  });

  it('empty / at-less history → no spans', () => {
    expect(histSpans([], T0)).toEqual([]);
    expect(histSpans([{ state: 'dev' }], T0)).toEqual([]);
  });
});

describe('phaseAggregate', () => {
  it('groups repeated states with visits, total, avg, max — sorted by total desc', () => {
    const h: HistoryEntry[] = [
      { state: 'approved_queue', at: iso(T0) },              // 30m
      { state: 'validating', at: iso(T0 + 30 * 60000) },     // 1m
      { state: 'approved_queue', at: iso(T0 + 31 * 60000) }, // 60m
      { state: 'validating', at: iso(T0 + 91 * 60000) },     // 2m
      { state: 'approved_queue', at: iso(T0 + 93 * 60000) }, // 10m
    ];
    const now = T0 + 103 * 60000;
    const ag = phaseAggregate(histSpans(h, now));
    expect(ag[0]).toEqual({ state: 'approved_queue', visits: 3, totalMs: 100 * 60000, avgMs: 100 * 60000 / 3, maxMs: 60 * 60000 });
    expect(ag.find((p) => p.state === 'validating')).toEqual({
      state: 'validating', visits: 2, totalMs: 3 * 60000, avgMs: 1.5 * 60000, maxMs: 2 * 60000,
    });
  });
});

describe('medianMs', () => {
  it('even count → mean of middle two; odd → middle', () => {
    expect(medianMs([1, 2, 3, 4])).toBe(2.5);
    expect(medianMs([5, 1, 3])).toBe(3);
    expect(medianMs([])).toBe(0);
  });
});

describe('laneDwell', () => {
  it('per-card dwell in one lane → n / avg / median / max + merged leads', () => {
    const now = T0 + 120 * 60000;
    // card A: 30m + 60m in approved_queue = 90m; merged at +100m → lead 100m
    const a = { history: [
      { state: 'approved_queue', at: iso(T0) },
      { state: 'validating', at: iso(T0 + 30 * 60000) },
      { state: 'approved_queue', at: iso(T0 + 31 * 60000) },
      { state: 'merged', at: iso(T0 + 91 * 60000) },
    ], mergedAt: iso(T0 + 100 * 60000) };
    // card B: 10m in approved_queue, still open
    const b = { history: [
      { state: 'approved_queue', at: iso(T0 + 110 * 60000) },
    ] };
    const d = laneDwell([a, b] as never[], 'approved_queue', now);
    expect(d.n).toBe(2);
    expect(d.avgMs).toBe(50 * 60000);        // (90m + 10m)/2
    expect(d.medianMs).toBe(50 * 60000);
    expect(d.maxMs).toBe(90 * 60000);
    expect(d.lead).toBeDefined();
    expect(d.lead!.n).toBe(1);
    expect(d.lead!.avgMs).toBe(100 * 60000); // first.at → mergedAt
  });

  it('cards without the lane are not counted', () => {
    const now = T0;
    const c = { history: [{ state: 'dev', at: iso(T0) }] };
    expect(laneDwell([c] as never[], 'approved_queue', now).n).toBe(0);
  });
});

import { tokensKey, tokensFor } from './boardStats';

describe('tokensKey / tokensFor', () => {
  const stream = {
    'pr-1753': [{ leg: 'dev', total: 100 }],
    'issue-42': [{ leg: 'spec', total: 7 }],
  } as never;

  it('card key prefers pr, then issue', () => {
    expect(tokensKey({ pr: 1753 })).toBe('pr-1753');
    expect(tokensKey({ issue: 42 })).toBe('issue-42');
    expect(tokensKey({})).toBeNull();
  });

  it('tokensFor reads the stream by card key, falls back to embedded', () => {
    expect(tokensFor({ pr: 1753 }, stream).length).toBe(1);
    expect(tokensFor({ issue: 42 }, stream)[0].total).toBe(7);
    expect(tokensFor({ pr: 999, tokens: [{ leg: 'x', total: 5 }] }, stream)[0].total).toBe(5);
    expect(tokensFor({ pr: 999 }, null)).toEqual([]);
    expect(tokensFor({ pr: 999 }, stream)).toEqual([]);
  });
});

import { mergesPerDay, mergeDayStats } from './boardStats';

describe('mergedArchiveFromSearch: leadMs', () => {
  it('derives lead = closed_at − created_at per PR (ms), missing → null', async () => {
    const { mergedArchiveFromSearch } = await import('./boardStats');
    const json = {
      total_count: 2,
      items: [
        { number: 1, title: 'a', created_at: '2026-10-20T10:00:00Z', closed_at: '2026-10-20T11:30:00Z' },
        { number: 2, title: 'b', created_at: '2026-10-20T10:00:00Z', closed_at: null },
      ],
    };
    const m = mergedArchiveFromSearch(json);
    expect(m!.items[0].leadMs).toBe(90 * 60000);
    expect(m!.items[1].leadMs).toBeNull();
  });
});

describe('mergesPerDay', () => {
  it('zero-fills 30 UTC days and counts merges per day, newest last', () => {
    const now = Date.parse('2026-10-21T20:00:00Z');
    const items = [
      { at: '2026-10-21T09:00:00Z' },
      { at: '2026-10-21T15:00:00Z' },
      { at: '2026-10-19T12:00:00Z' },
      { at: null },
    ] as { at: string | null }[];
    const days = mergesPerDay(items as never, 30, now);
    expect(days).toHaveLength(30);
    expect(days[29]).toEqual({ date: '10-21', n: 2 });
    expect(days[27]).toEqual({ date: '10-19', n: 1 });
    expect(days[0].n).toBe(0);
  });
});

describe('mergeDayStats', () => {
  it('total / perDay / best day', () => {
    const now = Date.parse('2026-10-21T20:00:00Z');
    const items = [
      { at: '2026-10-21T09:00:00Z' }, { at: '2026-10-21T15:00:00Z' },
      { at: '2026-10-21T16:00:00Z' }, { at: '2026-10-20T10:00:00Z' },
      { at: null },
    ] as { at: string | null }[];
    const s = mergeDayStats(items as never, 30, now);
    expect(s.total).toBe(4);
    expect(s.perDay).toBeCloseTo(4 / 30);
    expect(s.best).toEqual({ date: '10-21', n: 3 });
  });
});
