/**
 * Snow / Night switch.
 *
 * The initial value is applied by an inline script in <head> — it has to run
 * before first paint, and a module is deferred by definition. This file only
 * handles changes after load.
 */
const KEY = 'dmtools-theme';
const root = document.documentElement;

function apply(theme) {
  root.dataset.theme = theme;
  try {
    localStorage.setItem(KEY, theme);
  } catch {
    /* private mode — the theme still applies for this session */
  }
}

function stored() {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

export function initTheme() {
  const toggle = document.getElementById('theme-toggle');
  if (toggle) {
    toggle.addEventListener('click', () => {
      apply(root.dataset.theme === 'night' ? 'snow' : 'night');
    });
  }

  // Follow the OS only while the visitor has not made an explicit choice.
  const dark = window.matchMedia('(prefers-color-scheme: dark)');
  dark.addEventListener?.('change', (event) => {
    if (!stored()) root.dataset.theme = event.matches ? 'night' : 'snow';
  });
}
