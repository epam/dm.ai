import { prefersReducedMotion } from './shared';

/**
 * Scroll reveal, and the two things a revealed block can own: the count-up
 * figures and the bar widths. The counter used to be its own module with
 * exactly one caller — this one — so it lives here now.
 */

/** Count up to data-to once, the first time the element is revealed. */
function runCounter(el: HTMLElement): void {
  if (el.dataset.done) return;
  el.dataset.done = '1';

  const target = parseInt(el.dataset.to ?? '', 10) || 0;

  if (prefersReducedMotion) {
    el.textContent = String(target);
    return;
  }

  const duration = 1500;
  const start = performance.now();

  function frame(now: number): void {
    const progress = Math.min((now - start) / duration, 1);
    const eased = 1 - Math.pow(1 - progress, 3);
    el.textContent = String(Math.floor(eased * target));
    if (progress < 1) requestAnimationFrame(frame);
    else el.textContent = String(target);
  }

  requestAnimationFrame(frame);
}

/** Fire whatever a revealed block owns: counters, bar widths, the flow rail. */
function activate(el: Element): void {
  el.classList.add('is-in');

  if (el.classList.contains('counter')) runCounter(el as HTMLElement);
  el.querySelectorAll<HTMLElement>('.counter').forEach(runCounter);

  el.querySelectorAll<HTMLElement>('.bar__fill').forEach((fill) => {
    fill.style.transform = `scaleX(${fill.style.getPropertyValue('--w') || 1})`;
  });
}

const targets = document.querySelectorAll('.reveal');

if (!('IntersectionObserver' in window) || prefersReducedMotion) {
  targets.forEach(activate);
} else {
  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        activate(entry.target);
        observer.unobserve(entry.target);
      });
    },
    { threshold: 0.15, rootMargin: '0px 0px -40px 0px' },
  );

  targets.forEach((el) => observer.observe(el));
}
