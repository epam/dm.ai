import type { APIRoute } from 'astro';
import { getCollection } from 'astro:content';
import { basePath, lastModified } from '../data/content';
import { hrefOf } from '../lib/docs';
import { agentHrefOf } from '../lib/agentDocs';
import releases from '../data/releases.json';

/**
 * One page today. This grows a URL per reference document once the docs in
 * dmtools-ai-docs/ are rendered to HTML — that is the point of having it now.
 */
export const GET: APIRoute = async ({ site }) => {
  const root = new URL(basePath, site).href;

  // From the collection, so adding a Markdown file is the whole job.
  // Deduplicated: hrefOf folds the root README onto /docs/, which the index
  // route also claims.
  const docs = await getCollection('docs');
  const docUrls = [...new Set(docs.map((doc) => new URL(hrefOf(doc.id, basePath), site).href))]
    .sort();

  const agents = await getCollection('agentDocs');
  const agentUrls = [
    new URL(`${basePath}agents/`, site).href,
    ...agents.map((doc) => new URL(agentHrefOf(doc.id, basePath), site).href),
  ].sort();

  const releaseUrls = [
    new URL(`${basePath}releases/`, site).href,
    ...releases.map((r) => new URL(`${basePath}releases/${r.version}/`, site).href),
  ].sort();

  // pricing.md is listed because nothing on the site links to it.
  const body = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url>
    <loc>${root}</loc>
    <lastmod>${lastModified}</lastmod>
    <changefreq>weekly</changefreq>
    <priority>1.0</priority>
  </url>
  <url>
    <loc>${root}pricing.md</loc>
    <lastmod>${lastModified}</lastmod>
    <changefreq>monthly</changefreq>
    <priority>0.5</priority>
  </url>
${docUrls.map((loc) => `  <url>
    <loc>${loc}</loc>
    <lastmod>${lastModified}</lastmod>
    <changefreq>monthly</changefreq>
    <priority>${loc.endsWith('/docs/') ? '0.9' : '0.7'}</priority>
  </url>`).join('\n')}
${agentUrls.map((loc) => `  <url>
    <loc>${loc}</loc>
    <lastmod>${lastModified}</lastmod>
    <changefreq>monthly</changefreq>
    <priority>${loc.endsWith('/agents/') ? '0.8' : '0.6'}</priority>
  </url>`).join('\n')}
${releaseUrls.map((loc) => `  <url>
    <loc>${loc}</loc>
    <lastmod>${lastModified}</lastmod>
    <changefreq>weekly</changefreq>
    <priority>${loc.endsWith('/releases/') ? '0.8' : '0.6'}</priority>
  </url>`).join('\n')}
</urlset>
`;

  return new Response(body, { headers: { 'Content-Type': 'application/xml; charset=utf-8' } });
};
