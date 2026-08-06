import type { APIRoute } from 'astro';
import { basePath, lastModified, software, REPO } from '../data/content';

/**
 * Pricing as a file an agent can parse without rendering the page.
 *
 * For a free tool, "no pricing page" reads the same as "pricing hidden", and the
 * second gets filtered out of a shortlist — so state zero explicitly. Generated,
 * so the version and date cannot drift from the rest of the site.
 */
export const GET: APIRoute = ({ site }) => {
  const root = new URL(basePath, site).href;

  const body = `# Pricing — DMTools

Last updated: ${lastModified}

DMTools is free and open source. There is no paid tier, no licence key, no
account, and no usage limit — the project is released by EPAM Systems under the
Apache 2.0 licence.

## Free — the only tier

- Price: 0 USD, permanently
- Licence: Apache 2.0 (${REPO}/blob/main/LICENSE)
- Seats: unlimited
- Tool calls: unlimited, and not metered — the CLI runs on your machine or in
  your CI, and reports nothing back
- Integrations: all of them; none are held back for a paid plan
- Requirements: ${software.requirements}
- Support: GitHub issues (${REPO}/issues)

## What you do pay for

DMTools calls your AI provider and your delivery systems with your own
credentials, so the running cost is whatever those already charge you:

- Your AI provider's API usage — Anthropic, OpenAI, Google, AWS Bedrock or a
  local model through Ollama, at their rates
- The delivery tools you already licence — Jira, Azure DevOps, GitHub, and so on
- The CI minutes a run consumes on your own runners

EPAM does not resell, meter or mark up any of these, and there is no
DMTools-operated service in between.

## Source

- Landing page: ${root}
- Repository: ${REPO}
- Machine-readable summary: ${root}llms.txt
`;

  return new Response(body, { headers: { 'Content-Type': 'text/markdown; charset=utf-8' } });
};
