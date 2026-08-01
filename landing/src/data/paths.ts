import { REPO, DOCS } from './site';

/**
 * The four adoption paths. Each stands on its own — the numbering is reading
 * order, not a sequence anyone has to complete.
 */
export interface AdoptionPath {
  title: string;
  desc: string;
  link: { label: string; href: string };
}

export const paths: AdoptionPath[] = [
  {
    title: 'CLI and MCP tools',
    desc: 'Run direct tool calls against every connected system from your terminal. 320+ tools, available the moment you install.',
    link: { label: 'MCP tools reference →', href: `${DOCS}/references/mcp-tools` },
  },
  {
    title: 'Jobs and agents',
    desc: 'Orchestrate real workflows — AI teammate, reporting, test generation, story development — in plain JavaScript.',
    link: { label: 'Jobs reference →', href: `${DOCS}/references/jobs` },
  },
  {
    title: 'CI/CD pipelines',
    desc: 'Run DMTools in GitHub Actions, GitLab CI, Jenkins or Bitrise for ticket processing and teammate flows at scale.',
    // A file, so /blob/ — DOCS points at /tree/, which is for directories.
    link: {
      label: 'GitHub Actions guide →',
      href: `${REPO}/blob/main/dmtools-ai-docs/references/workflows/github-actions-teammate.md`,
    },
  },
  {
    title: 'Agent skills',
    desc: 'Install project-level DMTools skills for Cursor, Claude, Codex and Copilot. The agents your teams already use gain full delivery reach.',
    link: { label: 'Skill guide →', href: `${REPO}/blob/main/dmtools-ai-docs/SKILL.md` },
  },
];
