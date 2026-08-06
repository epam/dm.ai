import { dirname, resolve, relative, sep } from 'node:path';
import { existsSync } from 'node:fs';

/**
 * Rewrites Markdown links so they still resolve once the docs are served as HTML.
 *
 * Each link is resolved against the file it appears in: inside dmtools-ai-docs
 * it becomes /docs/<slug>/, anywhere else in the repository it becomes a GitHub
 * blob URL. A target that does not exist on disk is already broken in the repo,
 * so it is unlinked rather than published as a dead link.
 *
 * Done here rather than by editing the Markdown, which stays readable in the
 * repository where the agent skill reads it.
 */
export function remarkDocLinks({ docsRoot, repoRoot, repoUrl, base, branch = 'main' }) {
  const toSlug = (absolute) =>
    relative(docsRoot, absolute)
      .split(sep).join('/')
      .replace(/\.md$/i, '')
      .replace(/(^|\/)README$/i, '$1index')
      .replace(/(^|\/)index$/i, '')
      .replace(/\/$/, '')
      .toLowerCase();

  const isInside = (parent, child) => {
    const rel = relative(parent, child);
    return rel !== '' && !rel.startsWith('..') && !rel.startsWith(sep + '..');
  };

  const missing = [];

  return () => (tree, file) => {
    const from = file?.history?.[0] ?? file?.path;
    if (!from) return;
    const here = dirname(from);

    // emphasis, not a block node: a link is inline, and the text keeps a mark
    // showing it was meant to point somewhere.
    const unlink = (node) => {
      node.type = 'emphasis';
      delete node.url;
      delete node.title;
    };

    /** Returns null when the target does not exist, so the caller can unlink. */
    const rewrite = (url) => {
      if (/^([a-z][a-z0-9+.-]*:|\/\/|#)/i.test(url)) return url;

      const [path, hash = ''] = url.split('#');
      const fragment = hash ? `#${hash}` : '';
      if (!path) return url;

      const target = resolve(here, path);
      const toGitHub = () =>
        `${repoUrl}/blob/${branch}/${relative(repoRoot, target).split(sep).join('/')}${fragment}`;

      if (!/\.md$/i.test(path)) return isInside(repoRoot, target) ? toGitHub() : url;

      if (isInside(docsRoot, target) || target === docsRoot) {
        if (!existsSync(target)) return null;
        const slug = toSlug(target);
        return `${base}docs/${slug ? `${slug}/` : ''}${fragment}`;
      }

      return isInside(repoRoot, target) ? toGitHub() : url;
    };

    const walk = (node) => {
      if (node.type === 'link' && typeof node.url === 'string') {
        const next = rewrite(node.url);
        if (next === null) {
          missing.push(`${relative(docsRoot, from).split(sep).join('/')} → ${node.url}`);
          unlink(node);
        } else {
          node.url = next;
        }
      }
      if (Array.isArray(node.children)) node.children.forEach(walk);
    };

    walk(tree);

    // Best effort only — Astro's content loader swallows console and stderr, so
    // this is usually invisible during `astro build`. The check that holds is
    // auditing the built HTML for internal links with no matching page.
    if (missing.length) {
      process.stderr.write(
        `\n[docs] ${missing.length} link(s) point at Markdown that does not exist, `
        + 'rendered as plain text:\n'
        + missing.map((m) => `  ${m}\n`).join('') + '\n',
      );
      missing.length = 0;
    }
  };
}
