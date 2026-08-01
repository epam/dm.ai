// @ts-check
import { defineConfig } from 'astro/config';

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
  build: {
    // One page, and the markup is the product — keep it readable in view-source.
    inlineStylesheets: 'never',
  },
});
