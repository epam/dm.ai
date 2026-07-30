const SNIPPET = `const ticket = jira_get_ticket('PROJ-123');
const dod = confluence_content_by_title('Definition of Done');
const openPrs = github_list_prs('epam/dm.ai', 'open');

file_write('context.md', \`\${ticket.description}\\n\\n\${dod}\`);

const suggestion = cli_execute_command(
  'copilot suggest --file context.md'
);`;

export function CodeSample() {
  return (
    <section className="section section--bordered" id="code">
      <div className="container code-section">
        <div>
          <p className="eyebrow">Jobs &amp; agents</p>
          <h2>
            Automation in plain <span className="gradient-word">JavaScript</span>.
          </h2>
          <p className="section__lede">
            CLI tools are callable as plain JS functions inside a job — no SDK, no boilerplate, no
            proprietary DSL to learn.
          </p>
        </div>

        <div className="code-window">
          <div className="code-window__bar">
            <span className="code-window__dot" />
            <span className="code-window__dot" />
            <span className="code-window__dot" />
            <span className="code-window__name">story_development.js</span>
          </div>
          <pre className="code-block code-block--tall">
            <code>{SNIPPET}</code>
          </pre>
        </div>
      </div>
    </section>
  );
}
