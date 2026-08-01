import { prefersReducedMotion } from './motion.js';
import { runCounter } from './counters.js';

/** Fire whatever a revealed block owns: counters, bar widths, the flow rail. */
function activate(el) {
  el.classList.add('is-in');

  if (el.classList.contains('counter')) runCounter(el);
  el.querySelectorAll('.counter').forEach(runCounter);

  el.querySelectorAll('.bar__fill').forEach((fill) => {
    fill.style.transform = `scaleX(${fill.style.getPropertyValue('--w') || 1})`;
  });
}

export function initReveal() {
  const targets = document.querySelectorAll('.reveal');

  if (!('IntersectionObserver' in window) || prefersReducedMotion) {
    targets.forEach(activate);
    return;
  }

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
