import type { CollectionEntry } from 'astro:content';

export type AgentDoc = CollectionEntry<'agentDocs'>;

/** One page per agent config: /agents/<name>/. */
export const agentHrefOf = (id: string, base: string): string => `${base}agents/${id}/`;

/**
 * The generated page's H1 is the job class plus the config file name
 * ("Teammate (`story_solution.json`)") — the agent name is the useful title.
 */
export function agentTitleOf(doc: AgentDoc): string {
  return doc.id.replace(/[-_]/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
}

/**
 * First real paragraph after the H1 — the human description embedded from
 * docs/agents/<name>.md. Used for index cards and the meta description.
 */
export function agentSummaryOf(doc: AgentDoc): string {
  const body = (doc.body ?? '')
    .replace(/^#\s+.+$/m, '')
    .replace(/```[\s\S]*?```/g, '')
    .replace(/^\s*[|>#-].*$/gm, '');

  const paragraph = body.split(/\n\s*\n/).map((p) => p.trim()).find((p) => p.length > 40);
  if (!paragraph) return '';

  const flat = paragraph
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/[*_`]/g, '')
    .replace(/\s+/g, ' ')
    .trim();

  return flat.length > 200 ? `${flat.slice(0, 197).trimEnd()}…` : flat;
}

/** Group agents by their name prefix (story_*, bug_*, pr_*, …) for the index. */
export interface AgentGroup {
  label: string;
  items: AgentDoc[];
}

const GROUP_LABELS: Record<string, string> = {
  story: 'Story workflows',
  bug: 'Bug workflows',
  pr: 'Pull request workflows',
  test: 'Test automation',
  recover: 'Recovery',
  retry: 'Recovery',
};

function groupLabel(id: string): string {
  const prefix = id.split('_')[0];
  return GROUP_LABELS[prefix] ?? 'Other agents';
}

export function groupAgents(docs: AgentDoc[]): AgentGroup[] {
  const groups = new Map<string, AgentDoc[]>();
  const sorted = [...docs].sort((a, b) => a.id.localeCompare(b.id));
  for (const doc of sorted) {
    const label = groupLabel(doc.id);
    if (!groups.has(label)) groups.set(label, []);
    groups.get(label)!.push(doc);
  }
  const order = ['Story workflows', 'Bug workflows', 'Pull request workflows', 'Test automation', 'Recovery'];
  return [...groups.entries()]
    .sort(([a], [b]) => {
      const ia = order.indexOf(a);
      const ib = order.indexOf(b);
      return (ia === -1 ? order.length : ia) - (ib === -1 ? order.length : ib) || a.localeCompare(b);
    })
    .map(([label, items]) => ({ label, items }));
}
