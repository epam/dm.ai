import { InstallBox } from './InstallBox';

export function Hero() {
  return (
    <section className="hero" id="top">
      <div className="hero__glow hero__glow--sea" aria-hidden="true" />
      <div className="hero__glow hero__glow--lilac" aria-hidden="true" />
      <div className="container hero__inner">
        <p className="eyebrow">EPAM open source · Apache 2.0</p>
        <h1 className="hero__title">
          The enterprise <span className="gradient-word">AI-factory</span> orchestrator.
        </h1>
        <p className="hero__lede">
          DMTools automates delivery workflows across trackers, source control, documentation, design
          systems, AI providers, and CI/CD — built for self-hosted, enterprise environments that need
          repeatable AI-assisted workflows instead of one-off scripts or server-first demos.
        </p>

        <div className="hero__cta">
          <a className="btn btn--primary btn--lg" href="https://github.com/epam/dm.ai" target="_blank" rel="noopener">
            View on GitHub
          </a>
          <a
            className="btn btn--ghost btn--lg"
            href="https://github.com/epam/dm.ai/tree/main/dmtools-ai-docs"
            target="_blank"
            rel="noopener"
          >
            Read the docs
          </a>
        </div>

        <InstallBox />

        <div className="badges">
          <img
            src="https://img.shields.io/github/v/release/epam/dm.ai?label=latest%20version&color=00F6FF&labelColor=060606"
            alt="Latest release"
          />
          <img src="https://img.shields.io/badge/license-Apache%202.0-B896FF?labelColor=060606" alt="License Apache 2.0" />
          <img src="https://img.shields.io/badge/Java-17%2B-00FFF0?labelColor=060606" alt="Java 17+" />
          <img
            src="https://img.shields.io/github/stars/epam/dm.ai?labelColor=060606&color=7BA8FF"
            alt="GitHub stars"
          />
        </div>
      </div>
    </section>
  );
}
