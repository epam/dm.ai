import { REPO } from './site';

const RELEASE = `${REPO}/releases/latest/download`;

/**
 * The install tablist. `id` is the suffix behind every paired id in the markup
 * — `tab-<id>`, `panel-<id>`, `cmd-<id>` — which is what lets the tab script
 * find a panel from its button without a lookup table.
 */
export interface InstallTab {
  id: string;
  label: string;
  command: string;
  hint: string;
}

export const installTabs: InstallTab[] = [
  {
    id: 'unix',
    label: 'Linux / macOS',
    command: `curl -fsSL ${RELEASE}/install.sh | bash`,
    hint: 'Installs the CLI to <code>~/.dmtools</code>. Requires Java 17 or newer.',
  },
  {
    id: 'win',
    label: 'Windows PowerShell',
    command: `irm ${RELEASE}/install.ps1 | iex`,
    hint: 'Run in PowerShell 5.1 or newer. Requires Java 17 or newer.',
  },
  {
    id: 'skill',
    label: 'Agent skill',
    command: `curl -fsSL ${RELEASE}/skill-install.sh | bash`,
    hint: 'Adds the DMTools skill to your project for Cursor, Claude Code and Codex.',
  },
];

/**
 * The section used to end on a copied command, which leaves the reader with no
 * way to tell whether it worked or what to do next.
 */
export const verifyCommand = 'dmtools list';
