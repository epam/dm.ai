import { getCollection, type CollectionEntry } from 'astro:content';

export type Doc = CollectionEntry<'docs'>;

/**
 * The root README's id. It is the docs index and belongs at /docs/, but the
 * content store will not accept the empty string that would say so. Everything
 * that turns an id into a URL goes through hrefOf so the name never leaks.
 */
export const ROOT_ID = 'index';

export const hrefOf = (id: string, base: string): string =>
  id === ROOT_ID || !id ? `${base}docs/` : `${base}docs/${id}/`;

/** These files carry no front matter, so the H1 is the only title there is. */
export function titleOf(doc: Doc): string {
  const heading = doc.body?.match(/^#\s+(.+?)\s*$/m);
  if (heading) return heading[1].replace(/[*_`]/g, '').trim();

  const last = doc.id.split('/').pop() ?? doc.id;
  return last.replace(/[-_]/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
}

/** First real paragraph, for the index cards and the meta description. */
export function summaryOf(doc: Doc): string {
  const body = (doc.body ?? '')
    .replace(/^---[\s\S]*?---/, '')
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

/** Reading order, not alphabetical. Anything unlisted falls to the end. */
const ORDER: { prefix: string; label: string }[] = [
  { prefix: 'references/installation', label: 'Installation' },
  { prefix: 'references/configuration', label: 'Configuration' },
  { prefix: 'references/mcp-tools', label: 'MCP tools' },
  { prefix: 'references/jobs', label: 'Jobs' },
  { prefix: 'references/agents', label: 'Agents' },
  { prefix: 'references/workflows', label: 'Workflows' },
  { prefix: 'references/test-generation', label: 'Test generation' },
  { prefix: 'references/reporting', label: 'Reporting' },
  { prefix: 'per-skill-packages', label: 'Skill packages' },
];

export interface NavSection {
  label: string;
  items: { id: string; title: string; href: string }[];
}

export async function buildNav(base: string): Promise<NavSection[]> {
  const docs = await getCollection('docs');

  const entries = docs.map((doc) => ({
    id: doc.id,
    title: doc.id === ROOT_ID ? 'Overview' : titleOf(doc),
    href: hrefOf(doc.id, base),
    file: (doc.filePath ?? '').split('/').pop()?.replace(/\.md$/i, '') ?? '',
  }));

  // mcp-tools/README.md and TOOLS-REFERENCE.md share an H1, which listed the
  // same row twice. The headings belong to the repository, so disambiguate here.
  const counts = new Map<string, number>();
  entries.forEach((e) => counts.set(e.title, (counts.get(e.title) ?? 0) + 1));
  entries.forEach((e) => {
    if ((counts.get(e.title) ?? 0) < 2) return;
    e.title += /^(README|index)$/i.test(e.file)
      ? ' — Overview'
      : ` — ${e.file.replace(/[-_]/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase())}`;
  });

  entries.sort((a, b) => a.title.localeCompare(b.title));

  const used = new Set<string>();
  const sections: NavSection[] = [];

  const overview = entries.filter((e) => !e.id.includes('/'));
  overview.forEach((e) => used.add(e.id));
  if (overview.length) sections.push({ label: 'Overview', items: overview });

  for (const { prefix, label } of ORDER) {
    const items = entries.filter((e) => e.id === prefix || e.id.startsWith(`${prefix}/`));
    if (!items.length) continue;
    items.forEach((e) => used.add(e.id));
    sections.push({ label, items });
  }

  const rest = entries.filter((e) => !used.has(e.id));
  if (rest.length) sections.push({ label: 'Reference', items: rest });

  return sections;
}
