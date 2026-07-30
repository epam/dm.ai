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
          runs — one CLI, one job engine, one set of agent skills. In practice: whichever AI assistant
          your team already uses can now read and update Jira, GitHub, Confluence, and the rest, safely
          and consistently.
        </p>

        <div className="hub">
          <p className="hub__row-label">Your AI tools</p>
          <div className="hub__ring">
            {HARNESSES.map((h) => (
              <span className="hub__chip hub__chip--harness" key={h}>
                {h}
              </span>
            ))}
          </div>

          <div className="hub__connector" aria-hidden="true">
            <span />
          </div>

          <div className="hub__core">DMTools</div>

          <div className="hub__connector" aria-hidden="true">
            <span />
          </div>

          <p className="hub__row-label">Your delivery systems</p>
          <div className="hub__ring">
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
