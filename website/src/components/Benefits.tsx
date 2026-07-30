const BENEFITS = [
  {
    title: 'Less manual ticket busywork',
    detail:
      'Platform and delivery teams stop copy-pasting context between Jira, Confluence, and GitHub by hand — DMTools assembles it automatically.',
  },
  {
    title: 'A repeatable process, not a one-off prompt',
    detail:
      'The same reviewed workflow runs every time a ticket moves — not a script one engineer wrote once and nobody else can maintain.',
  },
  {
    title: 'Fits the pipeline you already have',
    detail:
      'Runs inside GitHub Actions, Jenkins, GitLab CI, or Bitrise. No new infrastructure to stand up or maintain.',
  },
  {
    title: 'Works with the AI assistant your team already uses',
    detail:
      'Cursor, Claude, Copilot, or Codex — nothing new to learn on top of the editor people already have open.',
  },
];

export function Benefits() {
  return (
    <section className="section section--bordered" id="benefits">
      <div className="container">
        <p className="eyebrow">Who it's for</p>
        <h2>What your team gets — not just what it runs.</h2>
        <p className="section__lede">
          You don't need to read code to benefit from DMTools. One technical teammate sets it up per
          project; platform, delivery, QA, and engineering leadership all feel the difference in how
          much manual coordination disappears.
        </p>

        <div className="grid grid--4">
          {BENEFITS.map((b) => (
            <div className="benefit-card" key={b.title}>
              <h3>{b.title}</h3>
              <p>{b.detail}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
