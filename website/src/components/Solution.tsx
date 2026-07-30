const HARNESSES = ['Cursor', 'Claude', 'Copilot', 'Codex'];
const SPOKES = ['Jira', 'Azure DevOps', 'GitHub', 'Confluence', 'Figma', 'Teams', 'SharePoint', 'TestRail'];

export function Solution() {
  return (
    <section className="section" id="solution">
      <div className="container">
        <p className="eyebrow">The solution</p>
        <h2>
          One <span className="gradient-word">orchestration layer</span>.
        </h2>
        <p className="section__lede">
          DMTools sits between your AI harnesses and every delivery system your organization already
          runs — one CLI, one job engine, one set of agent skills.
        </p>

        <div className="hub" aria-hidden="true">
          <div className="hub__ring hub__ring--inner">
            {HARNESSES.map((h) => (
              <span className="hub__chip hub__chip--harness" key={h}>
                {h}
              </span>
            ))}
          </div>
          <div className="hub__core">DMTools</div>
          <div className="hub__ring hub__ring--outer">
            {SPOKES.map((s) => (
              <span className="hub__chip" key={s}>
                {s}
              </span>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
