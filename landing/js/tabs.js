/** Install-command tablist, including the arrow-key behaviour a tablist owes. */
export function initTabs() {
  const tabs = [...document.querySelectorAll('.tab')];
  if (!tabs.length) return;

  function select(tab) {
    tabs.forEach((other) => {
      const isTarget = other === tab;
      other.classList.toggle('is-active', isTarget);
      other.setAttribute('aria-selected', isTarget ? 'true' : 'false');

      const panel = document.getElementById(`panel-${other.dataset.panel}`);
      if (!panel) return;
      panel.classList.toggle('is-active', isTarget);
      panel.hidden = !isTarget;
    });
  }

  tabs.forEach((tab) => {
    tab.addEventListener('click', () => select(tab));

    tab.addEventListener('keydown', (event) => {
      if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
      event.preventDefault();
      const step = event.key === 'ArrowRight' ? 1 : -1;
      const next = tabs[(tabs.indexOf(tab) + step + tabs.length) % tabs.length];
      select(next);
      next.focus();
    });
  });
}
