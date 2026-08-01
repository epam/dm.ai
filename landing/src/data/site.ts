/**
 * Everything the <head> needs, and the few facts more than one section states.
 *
 * The structured-data blocks in Layout.astro are built from the values here
 * rather than hand-written beside them: markup that contradicts the visible
 * page is a manual-action risk, and the only reliable way to keep the two in
 * step is to give them one source.
 */

const rawBase = import.meta.env.BASE_URL;
const base = rawBase.endsWith('/') ? rawBase : `${rawBase}/`;

/** Prefix a file in public/ with the base path this repository publishes under. */
export const asset = (path: string): string => base + path.replace(/^\//, '');

/** The site's own root, base path included. Absolute URLs are built from it. */
export const basePath = base;

export const REPO = 'https://github.com/epam/dm.ai';
export const DOCS = `${REPO}/tree/main/dmtools-ai-docs`;

export const meta = {
  lang: 'en',
  title: 'DMTools — Enterprise AI-Factory Orchestrator | EPAM Open Source',
  description:
    "DMTools is EPAM's open-source AI-factory orchestrator. 320+ CLI tools across 20+ integrations — Jira, Azure DevOps, GitHub, Figma, Teams and every major AI provider — behind one CLI. Apache 2.0.",
  keywords:
    'DMTools, EPAM, AI orchestrator, AI factory, CLI tools, MCP, Jira, GitHub, Azure DevOps, Figma, Confluence, delivery automation, open source',
  siteName: 'DMTools by EPAM',
  socialTitle: 'DMTools — Enterprise AI-Factory Orchestrator',
  ogDescription:
    '320+ CLI tools. 20+ integrations. One orchestration layer for your AI factory. Open source from EPAM.',
  twitterDescription:
    '320+ CLI tools. 20+ integrations. One orchestration layer for your AI factory.',
  // Night in both cases: the browser chrome should match the canvas the page
  // actually opens on, not the OS preference.
  themeColor: '#060606',
  ogImage: 'assets/og-cover.png',
  favicon: 'assets/favicon.svg',
} as const;

/**
 * The film. One YouTube id, stated once — it used to appear in the section's
 * data attribute and twice more in the VideoObject block.
 */
export const film = {
  youtubeId: 'EQp_leZgVtk',
  name: 'DMTools - Introduction',
  uploadDate: '2026-07-31',
  description:
    "A nine-scene introduction to DMTools, EPAM's open-source AI-factory orchestrator, in the EPAM 2023 brand system.",
  poster: 'assets/film-poster.jpg',
  posterAlt:
    'Still from the DMTools film: a ticket state change in Azure DevOps or Jira passes through a bridge that fires workflow_dispatch into a CI/CD container runner.',
  publisher: { name: 'AI Native', url: 'https://www.youtube.com/@AI-Native-Pro' },
  sourceUrl: 'https://github.com/IstiN/yoclip-examples/tree/main/branded/epam/dmtools',
} as const;

export const filmEmbedUrl = `https://www.youtube-nocookie.com/embed/${film.youtubeId}`;
export const filmWatchUrl = `https://www.youtube.com/watch?v=${film.youtubeId}`;

/**
 * softwareVersion tracks gradle.properties. It is the one field here that goes
 * stale silently, so bump it with the release.
 */
export const software = {
  version: '1.7.238',
  description:
    'Enterprise AI-factory orchestrator for automating delivery workflows across trackers, source control, documentation, design systems, AI providers, and CI/CD.',
  requirements: 'Java 17 or newer',
  operatingSystem: 'Linux, macOS, Windows',
  features: [
    '320+ CLI tools across 20+ enterprise integrations',
    'Model Context Protocol server for Claude, Cursor and Copilot',
    'JavaScript agents with direct access to every tool',
    'Runs in GitHub Actions, GitLab CI, Jenkins and Bitrise',
    'Runtime AI provider selection across Claude, OpenAI, Gemini, Vertex AI, Bedrock, DIAL and Ollama',
  ],
} as const;

/** The date sitemap.xml reports. Bump it when the page's content changes. */
export const lastModified = '2026-08-01';
