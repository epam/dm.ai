const POINTS = [
  {
    title: 'No DMTools-operated backend',
    detail:
      'DMTools is a CLI you run yourself — inside your own CI/CD pipeline or on your own workstation. There is no hosted service in the middle, and no telemetry phones home.',
  },
  {
    title: 'Your credentials, your environment',
    detail:
      'Jira, Azure DevOps, GitHub, and every other integration token lives in your own env files or CI secrets. Nothing is transmitted to or stored by a third party.',
  },
  {
    title: 'Bring your own AI provider',
    detail:
      'Gemini, OpenAI, Anthropic, AWS Bedrock, an enterprise LLM gateway, or Vertex AI — or run fully offline with Ollama if code and tickets can never leave your network.',
  },
  {
    title: 'Human approval, always',
    detail:
      'Every generated branch and pull request stops for a human review before anything merges. DMTools drafts the work; your team still ships it.',
  },
];

export function Trust() {
  return (
    <section className="section section--bordered" id="trust">
      <div className="container">
        <p className="eyebrow">Built for enterprise trust</p>
        <h2>Your data never leaves your infrastructure.</h2>
        <p className="section__lede">
          DMTools is Apache-2.0-licensed source code, not a SaaS product. Everything it touches — your
          tickets, your repos, your LLM calls — runs under credentials and infrastructure you already
          control.
        </p>

        <div className="trust-grid">
          {POINTS.map((p) => (
            <div className="trust-card" key={p.title}>
              <h3>{p.title}</h3>
              <p>{p.detail}</p>
            </div>
          ))}
        </div>

        <a
          className="proof-strip"
          href="https://github.com/epam/dm.ai/pull/405"
          target="_blank"
          rel="noopener"
        >
          <span className="proof-strip__tag">Dogfooded in production</span>
          <span className="proof-strip__text">
            DMTools' own CI already merges automation-authored pull requests in this repository —
            see PR&nbsp;#405 for a real example.
          </span>
          <span className="proof-strip__arrow">→</span>
        </a>
      </div>
    </section>
  );
}
