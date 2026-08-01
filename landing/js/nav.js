/** The sticky header grows a bottom hairline once the page has moved. */
function initStuck(nav) {
  const setStuck = () => nav.classList.toggle('is-stuck', window.scrollY > 8);
  setStuck();
  window.addEventListener('scroll', setStuck, { passive: true });
}

/** Burger menu. Below 720px the links collapse into a panel under the header. */
function initMenu(nav) {
  const burger = document.getElementById('nav-burger');
  const panel = document.getElementById('nav-links');
  if (!burger || !panel) return;

  const setOpen = (open) => {
    nav.classList.toggle('is-open', open);
    burger.setAttribute('aria-expanded', String(open));
    burger.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
  };

  burger.addEventListener('click', () => setOpen(!nav.classList.contains('is-open')));

  // Every item in the panel navigates, so a click always ends the menu's job.
  panel.addEventListener('click', (event) => {
    if (event.target.closest('a')) setOpen(false);
  });

  document.addEventListener('keydown', (event) => {
    if (event.key !== 'Escape' || !nav.classList.contains('is-open')) return;
    setOpen(false);
    burger.focus();
  });

  // Crossing back to the desktop layout would otherwise strand `is-open` on a
  // header whose links are laid out inline again. Keep in step with the burger
  // breakpoint in nav.css.
  const wide = window.matchMedia('(min-width: 861px)');
  wide.addEventListener('change', (event) => {
    if (event.matches) setOpen(false);
  });
}

/**
 * Scroll spy — the header says which section the reader is standing in.
 *
 * The margins crop the viewport to a band across its middle, so the mark
 * changes when a section reaches the reader rather than when it first appears
 * at the bottom edge.
 */
function initSpy() {
  if (!('IntersectionObserver' in window)) return;

  const links = new Map();
  document.querySelectorAll('.nav__link[href^="#"]').forEach((link) => {
    const section = document.querySelector(link.getAttribute('href'));
    if (section) links.set(section, link);
  });
  if (!links.size) return;

  const visible = new Set();

  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) visible.add(entry.target);
        else visible.delete(entry.target);
      });

      // With two sections in the band the reader is leaving the upper one, so
      // the topmost wins and the header never runs ahead of them.
      const current = [...visible].sort((a, b) => a.offsetTop - b.offsetTop)[0];

      links.forEach((link, section) => {
        const on = section === current;
        link.classList.toggle('is-current', on);
        if (on) link.setAttribute('aria-current', 'location');
        else link.removeAttribute('aria-current');
      });
    },
    { rootMargin: '-30% 0px -55% 0px' },
  );

  links.forEach((_, section) => observer.observe(section));
}

export function initNav() {
  const nav = document.getElementById('nav');
  if (!nav) return;

  initStuck(nav);
  initMenu(nav);
  initSpy();
}
