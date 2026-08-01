/** The sticky header grows a bottom hairline once the page has moved. */
export function initNav() {
  const nav = document.getElementById('nav');
  if (!nav) return;

  const setStuck = () => nav.classList.toggle('is-stuck', window.scrollY > 8);
  setStuck();
  window.addEventListener('scroll', setStuck, { passive: true });
}
