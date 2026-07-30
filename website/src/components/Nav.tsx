import { useState } from 'react';

const LINKS = [
  { href: '#problem', label: 'Why DMTools' },
  { href: '#architecture', label: 'Architecture' },
  { href: '#integrations', label: 'Integrations' },
  { href: '#trust', label: 'Trust & security' },
  { href: '#quick-start', label: 'Quick start' },
];

export function Nav() {
  const [open, setOpen] = useState(false);

  return (
    <header className={`nav${open ? ' nav--open' : ''}`}>
      <div className="container nav__inner">
        <a className="nav__brand" href="#top">
          <svg viewBox="0 0 32 32" width="26" height="26" aria-hidden="true">
            <rect width="32" height="32" rx="8" fill="#060606" />
            <path
              d="M16 5 6 10v12l10 5 10-5V10L16 5Z"
              stroke="url(#navGrad)"
              strokeWidth="1.7"
              strokeLinejoin="round"
              fill="none"
            />
            <path
              d="M16 5v10m0 0-10-5m10 5 10-5m-10 5v12"
              stroke="url(#navGrad)"
              strokeWidth="1.7"
              strokeLinejoin="round"
            />
            <defs>
              <linearGradient id="navGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stopColor="#00FFF0" />
                <stop offset="50%" stopColor="#00F6FF" />
                <stop offset="100%" stopColor="#B896FF" />
              </linearGradient>
            </defs>
          </svg>
          <span>DMTools</span>
        </a>

        <nav className="nav__links">
          {LINKS.map((link) => (
            <a key={link.href} href={link.href}>
              {link.label}
            </a>
          ))}
          <a className="btn btn--ghost" href="https://github.com/epam/dm.ai/releases" target="_blank" rel="noopener">
            Releases
          </a>
          <a className="btn btn--primary" href="https://github.com/epam/dm.ai" target="_blank" rel="noopener">
            View on GitHub
          </a>
        </nav>

        <button
          className="nav__toggle"
          aria-label="Toggle navigation"
          aria-expanded={open}
          onClick={() => setOpen((v) => !v)}
        >
          <span />
          <span />
          <span />
        </button>
      </div>
    </header>
  );
}
