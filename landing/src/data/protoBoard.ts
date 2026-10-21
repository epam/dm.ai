// Board data for the circuit-hero prototype — mirrors dmtools-factory/config.js.
// Each factory's SM tick publishes a schema-2 snapshot to its repo's
// factory-data branch; the hero fetches the same CDN links the board uses.

export type Factory = {
  id: string; name: string; accent: string; stateUrl: string;
  /** Tokens stream asset on the factory-data branch (data/<asset>). */
  tokensAsset: string;
  /** GitHub logins the machine's PRs/issues come from (search ORs them). */
  machineAuthors: string[];
};

export const factories = [
  {
    id: 'fa',
    name: 'flutter_agent_harness',
    accent: '#22d3ee',
    stateUrl: 'https://raw.githubusercontent.com/IstiN/flutter_agent_harness/factory-data/data/fa-state.json',
    tokensAsset: 'fa-tokens.json',
    machineAuthors: ['ai-teammate', 'vabhzw17eg2qu4m9-bit'],
  },
  {
    id: 'dart',
    name: 'dmtools-dart',
    accent: '#34d399',
    stateUrl: 'https://raw.githubusercontent.com/epam/dmtools-dart/factory-data/data/dart-state.json',
    tokensAsset: 'dart-tokens.json',
    machineAuthors: ['ai-teammate', 'vabhzw17eg2qu4m9-bit'],
  },
  {
    id: 'agents',
    name: 'dmtools-agents',
    accent: '#a78bfa',
    stateUrl: 'https://raw.githubusercontent.com/IstiN/dmtools-agents/factory-data/data/agents-state.json',
    tokensAsset: 'agents-tokens.json',
    machineAuthors: ['ai-teammate', 'vabhzw17eg2qu4m9-bit'],
  },
] as const;

export const laneOrder = [
  { id: 'development', title: 'Development' },
  { id: 'pr_created', title: 'PR created' },
  { id: 'pr_validation', title: 'PR validation' },
  { id: 'review', title: 'Review' },
  { id: 'approved_queue', title: 'Approved queue · FIFO' },
  { id: 'validating', title: 'Validating · mutex' },
  { id: 'merged_recent', title: 'Merged · 24h' },
];

export const backlogColumns = [
  { id: 'in_dev', title: 'In dev · assigned' },
  { id: 'queued', title: 'agent:dev queued' },
  { id: 'blocked', title: 'Blocked · owner hold' },
  { id: 'inbox', title: 'Inbox' },
];

export const badgeLabels = [
  'pr_approved', 'ai_validated', 'ai_pr_reviewed', 'ai_validating',
  'dependencies', 'agent:dev', 'agent:review', 'agent:rework', 'github_actions',
];

export type Terminal = {
  side: 'left' | 'right' | 'top' | 'bottom';
  x: number;
  y: number;
  laneId: string;
  labelSide?: 'left' | 'right';
};

// 11 terminals: 7 pipeline lanes (left 4, right 3) + 4 backlog lanes fanning
// up from the cube top — blocked/inbox exit up-left, in_dev/queued up-center.
export const terminals: Terminal[] = [
  { side: 'left', x: 2.5, y: 16, laneId: 'development' },
  { side: 'left', x: 2.5, y: 29, laneId: 'pr_created' },
  { side: 'left', x: 2.5, y: 42, laneId: 'pr_validation' },
  { side: 'left', x: 2.5, y: 55, laneId: 'review' },
  { side: 'right', x: 97.5, y: 24, laneId: 'approved_queue' },
  { side: 'right', x: 97.5, y: 44, laneId: 'validating' },
  { side: 'right', x: 97.5, y: 64, laneId: 'merged_recent' },
  { side: 'top', x: 21, y: 7, labelSide: 'left', laneId: 'blocked' },
  { side: 'top', x: 38, y: 10.5, labelSide: 'left', laneId: 'inbox' },
  { side: 'top', x: 45, y: 14, labelSide: 'right', laneId: 'in_dev' },
  { side: 'top', x: 58, y: 17.5, labelSide: 'right', laneId: 'queued' },
];

export const SEA = [0x00, 0xf6, 0xff];
export const LILAC = [0xb8, 0x96, 0xff];
export const mixColor = (t: number) => SEA.map((c, i) => Math.round(c + (LILAC[i] - c) * t));
