const FAQS = [
  {
    q: 'Do I need to be a developer to benefit from DMTools?',
    a: 'No. A technical teammate installs and configures it once per project (Java 17+ plus a few environment variables). After that, the benefit — tickets processed, pull requests drafted, reports generated — reaches delivery managers, QA, and platform teams too, not only engineers.',
  },
  {
    q: 'Does this only work with GitHub?',
    a: 'No. Integrations span Jira, Azure DevOps, GitHub, GitLab, Confluence, TestRail, Figma, Teams, SharePoint, Bitrise, and Jenkins — see the full list above.',
  },
  {
    q: 'What happens if the AI gets something wrong?',
    a: 'Nothing merges automatically. Every generated branch and pull request stops for a human review before it ships. DMTools drafts the work; your team still approves and merges it.',
  },
  {
    q: 'Is our data sent to EPAM or a DMTools-hosted service?',
    a: "No. DMTools is a CLI you run in your own CI/CD pipeline or on your own machine, using your own credentials and your own choice of AI provider. There's no DMTools-operated backend in between.",
  },
  {
    q: 'Which AI providers can we use?',
    a: 'Gemini, OpenAI, Anthropic, AWS Bedrock, an enterprise LLM gateway, or fully local models via Ollama if code and tickets can never leave your network.',
  },
  {
    q: 'What does DMTools cost?',
    a: 'DMTools itself is free and open source under Apache 2.0. You only pay for the AI provider and delivery tools your organization already uses.',
  },
];

export function Faq() {
  return (
    <section className="section section--bordered" id="faq">
      <div className="container">
        <p className="eyebrow">Common questions</p>
        <h2>Plain answers, before you install anything.</h2>

        <div className="faq-list">
          {FAQS.map((item) => (
            <details className="faq-item" key={item.q}>
              <summary>{item.q}</summary>
              <p>{item.a}</p>
            </details>
          ))}
        </div>
      </div>
    </section>
  );
}
