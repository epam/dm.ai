export function Footer() {
  return (
    <footer className="footer">
      <div className="container footer__inner">
        <div className="footer__brand">
          <span>DMTools</span>
          <span className="footer__license">Apache 2.0 License</span>
        </div>

        <nav className="footer__links">
          <a href="https://github.com/epam/dm.ai" target="_blank" rel="noopener">
            Repository
          </a>
          <a href="https://github.com/epam/dm.ai/issues" target="_blank" rel="noopener">
            Issues
          </a>
          <a href="https://github.com/epam/dm.ai/releases" target="_blank" rel="noopener">
            Releases
          </a>
          <a href="https://github.com/epam/dm.ai/tree/main/dmtools-ai-docs" target="_blank" rel="noopener">
            Documentation
          </a>
        </nav>

        <p className="footer__note">Community-contributed landing page for DMTools · EPAM AI/Run</p>
      </div>
    </footer>
  );
}
