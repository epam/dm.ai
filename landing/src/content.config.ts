import { defineCollection } from 'astro:content';
import { glob } from 'astro/loaders';

/**
 * The reference docs, read from the sibling directory contributors already edit.
 *
 * The four exclusions are editorial: the first three are thin near-duplicates of
 * SKILL.md (a retro, a compression, a prompt kernel) and the changelog is served
 * by the repository. Remove an entry to publish it.
 */
const EXCLUDED = ['IMPORTANT_LESSONS.md', 'SKILL.scl.md', 'SKILL.ultra.md', 'CHANGELOG.md'];

const docs = defineCollection({
  loader: glob({
    base: '../dmtools-ai-docs',
    pattern: ['**/*.md', ...EXCLUDED.map((file) => `!${file}`)],
    // A directory's README folds into the directory. The fold anchors on `/`,
    // which leaves the root README as the literal id `index` — the content store
    // rejects the empty string it logically is. ROOT_ID in lib/docs.ts owns that
    // name and hrefOf maps it back to /docs/.
    generateId: ({ entry }) =>
      entry
        .replace(/\.md$/i, '')
        .replace(/(^|\/)README$/i, '$1index')
        .replace(/\/index$/i, '')
        .toLowerCase(),
  }),
});

export const collections = { docs };
