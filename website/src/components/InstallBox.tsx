import { useState } from 'react';

const COMMANDS: Record<'unix' | 'win', string> = {
  unix: 'curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash',
  win: 'irm https://github.com/epam/dm.ai/releases/latest/download/install.ps1 | iex',
};

export function InstallBox() {
  const [os, setOs] = useState<'unix' | 'win'>('unix');
  const [copied, setCopied] = useState(false);

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(COMMANDS[os]);
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    } catch {
      // Clipboard API unavailable (e.g. insecure context) — fail silently.
    }
  }

  return (
    <div className="install-box">
      <div className="install-box__tabs" role="tablist" aria-label="Install command platform">
        <button
          role="tab"
          aria-selected={os === 'unix'}
          className={os === 'unix' ? 'is-active' : ''}
          onClick={() => setOs('unix')}
        >
          macOS / Linux
        </button>
        <button
          role="tab"
          aria-selected={os === 'win'}
          className={os === 'win' ? 'is-active' : ''}
          onClick={() => setOs('win')}
        >
          Windows (PowerShell)
        </button>
      </div>
      <div className="install-box__body">
        <pre className="code-block">
          <code>{COMMANDS[os]}</code>
        </pre>
        <button className={`copy-btn${copied ? ' is-copied' : ''}`} type="button" onClick={handleCopy}>
          {copied ? 'Copied!' : 'Copy'}
        </button>
      </div>
    </div>
  );
}
