const TOOLS = ['Tracker', 'Repo', 'Docs', 'CI/CD', 'AI'];

export function Problem() {
  return (
    <section className="section section--bordered" id="problem">
      <div className="container">
        <p className="eyebrow">The problem</p>
        <h2>
          Delivery runs on <span className="gradient-word">duct tape</span>.
        </h2>
        <p className="section__lede">
          Trackers, repos, docs, CI, and AI tools don't talk to each other. Every team glues them
          together with one-off scripts that don't survive the next hire, the next tool swap, or the
          next audit.
        </p>

        <div className="duct-tape" aria-hidden="true">
          {TOOLS.map((tool, i) => (
            <div className="duct-tape__group" key={tool}>
              <div className="duct-tape__card">{tool}</div>
              {i < TOOLS.length - 1 && <span className="duct-tape__break" />}
            </div>
          ))}
        </div>
        <p className="duct-tape__caption">Disconnected tools. Broken handoffs. One-off scripts that don't scale.</p>
      </div>
    </section>
  );
}
