const PATHS = [
  {
    index: '01',
    title: 'CLI + MCP tools',
    detail: 'Execute direct tool calls and integration operations from the terminal.',
    href: 'https://github.com/epam/dm.ai/tree/main/dmtools-ai-docs/references/mcp-tools',
    link: 'MCP tools reference',
  },
  {
    index: '02',
    title: 'Jobs + agents',
    detail: 'Run orchestrated workflows such as Teammate, reporting, and test generation.',
    href: 'https://github.com/epam/dm.ai/tree/main/dmtools-ai-docs/references/jobs',
    link: 'Jobs reference',
  },
  {
    index: '03',
    title: 'CI/CD pipelines',
    detail: 'Run DMTools in GitHub Actions, Jenkins, Bitrise, GitLab CI, or Bitbucket for ticket processing and teammate flows.',
    href: 'https://github.com/epam/dm.ai/blob/main/dmtools-ai-docs/references/workflows/github-actions-teammate.md',
    link: 'GitHub Actions teammate workflow',
  },
  {
    index: '04',
    title: 'AI assistant skills',
    detail: 'Install project-level DMTools skills for agent-driven usage in Cursor, Claude, Codex, and Copilot.',
    href: 'https://github.com/epam/dm.ai/blob/main/dmtools-ai-docs/SKILL.md',
    link: 'Skill guide',
  },
];

export function UsagePaths() {
  return (
    <section className="section" id="usage-paths">
      <div className="container">
        <p className="eyebrow">Run it your way</p>
        <h2>Pick the entry point that fits your workflow.</h2>

        <div className="grid grid--4">
          {PATHS.map((p) => (
            <a className="path-card" href={p.href} target="_blank" rel="noopener" key={p.index}>
              <span className="path-card__index">{p.index}</span>
              <h3>{p.title}</h3>
              <p>{p.detail}</p>
              <span className="path-card__link">{p.link} →</span>
            </a>
          ))}
        </div>
      </div>
    </section>
  );
}
