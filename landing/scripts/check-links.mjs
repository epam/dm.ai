#!/usr/bin/env node
/**
 * Internal link checker for the built site.
 *
 * Crawls dist/**.html, collects href/src attributes, and fails the build when
 * a site-internal link does not resolve to a file in dist/. External links
 * (http/mailto/…) and fragment-only links are skipped — this catches the
 * "repo-relative path leaked onto the site" class of bug, not the open web.
 *
 * Usage: node scripts/check-links.mjs [distDir] [basePath]
 *   distDir  defaults to dist
 *   basePath defaults to / (must match the build base, e.g. /repo/ on GitHub
 *            Pages project sites)
 */

import { readdirSync, readFileSync, existsSync, statSync } from 'node:fs';
import { join, resolve, sep } from 'node:path';

const distDir = resolve(process.argv[2] || 'dist');
const base = (process.argv[3] || '/').replace(/\/?$/, '/');

function* walk(dir) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) yield* walk(full);
    else if (entry.endsWith('.html')) yield full;
  }
}

const ATTR_RE = /(?:href|src)="([^"]+)"/g;
const missing = new Map(); // url -> [source files]
let checked = 0;

for (const file of walk(distDir)) {
  const html = readFileSync(file, 'utf8');
  for (const match of html.matchAll(ATTR_RE)) {
    const url = match[1];
    if (/^([a-z][a-z0-9+.-]*:|\/\/|#|mailto:)/i.test(url)) continue;
    if (!url.startsWith(base)) continue; // not a site-internal link

    const path = url.slice(base.length).split('#')[0].split('?')[0];
    if (!path) continue; // the root itself

    checked++;
    const asFile = join(distDir, path);
    const asDirIndex = join(distDir, path.replace(/\/?$/, '/'), 'index.html');
    if (!existsSync(asFile) && !existsSync(asDirIndex)) {
      if (!missing.has(url)) missing.set(url, []);
      missing.get(url).push(file.split(sep).slice(-3).join('/'));
    }
  }
}

if (missing.size > 0) {
  console.error(`❌ ${missing.size} broken internal link(s):`);
  for (const [url, sources] of missing) {
    console.error(`  ${url}`);
    console.error(`    referenced from: ${sources.slice(0, 5).join(', ')}${sources.length > 5 ? '…' : ''}`);
  }
  process.exit(1);
}

console.log(`✅ ${checked} internal links OK across dist/`);
