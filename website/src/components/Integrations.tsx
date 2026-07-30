const TOOLS = [
  'Jira',
  'Azure DevOps',
  'GitHub',
  'GitLab',
  'Confluence',
  'TestRail',
  'Figma',
  'Teams',
  'SharePoint',
  'Bitrise',
  'Jenkins',
];

const AI_PROVIDERS = ['Gemini', 'OpenAI', 'Anthropic', 'AWS Bedrock', 'Enterprise gateway (DIAL)', 'Ollama', 'Vertex AI'];

export function Integrations() {
  return (
    <section className="section section--bordered" id="integrations">
      <div className="container">
        <p className="eyebrow">Integrations</p>
        <h2>Connects to the tools your delivery already runs on.</h2>

        <div className="chip-grid">
          {TOOLS.map((t) => (
            <span className="chip" key={t}>
              {t}
            </span>
          ))}
        </div>

        <p className="section__sublabel">AI providers</p>
        <div className="chip-grid chip-grid--accent">
          {AI_PROVIDERS.map((p) => (
            <span className="chip chip--accent" key={p}>
              {p}
            </span>
          ))}
        </div>
      </div>
    </section>
  );
}
