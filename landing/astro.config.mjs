// @ts-check
import { defineConfig } from 'astro/config';
import react from '@astrojs/react';
import { unified } from '@astrojs/markdown-remark';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { remarkDocLinks } from './src/lib/remark-doc-links.mjs';
import { rehypeDocIcons } from './src/lib/rehype-doc-icons.mjs';

const here = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT = resolve(here, '..');
const DOCS_ROOT = resolve(REPO_ROOT, 'dmtools-ai-docs');
const REPO_URL = 'https://github.com/epam/dm.ai';

/**
 * The published address differs per repository — a fork publishes to its own
 * github.io path — so it arrives as an environment variable rather than being
 * hardcoded. The deploy workflow passes what actions/configure-pages reports.
 *
 * Splitting it into origin and path matters for a project page: the site lives
 * under /<repo>/, and every generated asset link has to carry that prefix or
 * it resolves against the user page root and 404s.
 */
const SITE_URL = process.env.SITE_URL || 'http://localhost:4321';
const { origin, pathname } = new URL(SITE_URL.endsWith('/') ? SITE_URL : `${SITE_URL}/`);

export default defineConfig({
  site: origin,
  base: pathname,
  trailingSlash: 'always',
  integrations: [react()],
  build: {
    // The landing markup is the product — keep it readable in view-source.
    inlineStylesheets: 'never',
  },
  markdown: {
    // The reference pages carry a lot of shell and JSON; a highlighter that
    // matches the two panels the landing page already draws keeps them looking
    // like the same site rather than a bolted-on docs host.
    shikiConfig: { theme: 'github-dark-default', wrap: false },
    processor: unified({
      remarkPlugins: [
        remarkDocLinks({
          docsRoot: DOCS_ROOT,
          repoRoot: REPO_ROOT,
          repoUrl: REPO_URL,
          base: pathname,
        }),
      ],
      rehypePlugins: [rehypeDocIcons],
    }),
  },
});
