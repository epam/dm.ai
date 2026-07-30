const STEPS = [
  { title: 'Ticket state change', detail: 'A ticket moves to "Ready for dev" in Jira or Azure DevOps.' },
  { title: 'Service hook', detail: 'A webhook fires an Azure Function bridge.' },
  { title: 'workflow_dispatch', detail: 'The bridge triggers a GitHub Actions run.' },
  { title: 'Runner installs DMTools', detail: 'A fresh CLI install, per run — nothing persists between jobs.' },
  { title: 'preJSAction builds context', detail: 'CLI tools pull the ticket, wiki, repo, and knowledge base.' },
  { title: 'LLM reasoning', detail: 'Copilot CLI, Codex, or Claude reasons over the assembled context.' },
  { title: 'postJSAction publishes', detail: 'A branch + pull request go up, the ticket updates — a human approves before anything merges.' },
];

export function Architecture() {
  return (
    <section className="section" id="architecture">
      <div className="container">
        <p className="eyebrow">AI teammate architecture</p>
        <h2>
          From ticket to <span className="gradient-word">pull request</span>.
        </h2>
        <p className="section__lede">
          A ticket state change fires the whole pipeline. Nothing is deployed automatically — every run
          ends with a human approving the result.
        </p>

        <ol className="flow">
          {STEPS.map((step, i) => (
            <li className="flow__step" key={step.title}>
              <span className="flow__index">{String(i + 1).padStart(2, '0')}</span>
              <div>
                <h3 className="flow__title">{step.title}</h3>
                <p className="flow__detail">{step.detail}</p>
              </div>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}
