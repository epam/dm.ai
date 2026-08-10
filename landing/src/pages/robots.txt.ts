import type { APIRoute } from 'astro';
import { basePath } from '../data/content';

/**
 * Crawlers read robots.txt from the ORIGIN root only, so on a github.io project
 * path this file documents intent rather than enforcing it — GitHub's own file
 * governs *.github.io. It goes live with a custom domain; until then the
 * sitemap must be submitted through Search Console to be found.
 */
export const GET: APIRoute = ({ site }) => {
  const root = new URL(basePath, site).href;

  // `User-agent: *` already allows these; naming them states the intent, so a
  // later blanket Disallow does not quietly take a platform out. Both groups are
  // allowed — the split is where that decision would be made if it changes.
  const answering = [
    'GPTBot', 'OAI-SearchBot', 'ChatGPT-User',
    'ClaudeBot', 'Claude-User', 'Claude-SearchBot',
    'PerplexityBot', 'Perplexity-User',
    'Google-Extended', 'Applebot-Extended', 'Bingbot', 'Amazonbot', 'MistralAI-User',
  ];

  const training = ['anthropic-ai', 'cohere-ai', 'Meta-ExternalAgent', 'CCBot'];

  const block = (agents: string[]) =>
    agents.map((agent) => `User-agent: ${agent}\nAllow: /`).join('\n\n');

  const body = `# Served at ${root}robots.txt

User-agent: *
Allow: /

# Assistants that answer a question and can cite this page back.
${block(answering)}

# Crawlers that only collect for training.
${block(training)}

Sitemap: ${root}sitemap.xml
`;

  return new Response(body, { headers: { 'Content-Type': 'text/plain; charset=utf-8' } });
};
