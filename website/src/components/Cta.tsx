import { InstallBox } from './InstallBox';

export function Cta() {
  return (
    <section className="section cta" id="quick-start">
      <div className="container cta__inner">
        <p className="eyebrow">Get started</p>
        <h2>
          Build your <span className="gradient-word">AI factory</span>.
        </h2>
        <p className="section__lede">
          Java 17+ is the only baseline requirement. Everything else — Jira, Azure DevOps, GitHub,
          Confluence, TestRail, Figma, and your AI provider of choice — is configured per project.
        </p>

        <InstallBox />

        <div className="cta__links">
          <a className="btn btn--primary" href="https://github.com/epam/dm.ai" target="_blank" rel="noopener">
            Star on GitHub
          </a>
          <a
            className="btn btn--ghost"
            href="https://github.com/epam/dm.ai/tree/main/dmtools-ai-docs/references/configuration"
            target="_blank"
            rel="noopener"
          >
            Configuration guide
          </a>
        </div>
      </div>
    </section>
  );
}
