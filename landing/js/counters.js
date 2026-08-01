import { prefersReducedMotion } from './motion.js';

/** Count up to data-to once, the first time the element is revealed. */
export function runCounter(el) {
  if (el.dataset.done) return;
  el.dataset.done = '1';

  const target = parseInt(el.dataset.to, 10) || 0;

  if (prefersReducedMotion) {
    el.textContent = String(target);
    return;
  }

  const duration = 1500;
  const start = performance.now();

  function frame(now) {
    const progress = Math.min((now - start) / duration, 1);
    const eased = 1 - Math.pow(1 - progress, 3);
    el.textContent = String(Math.floor(eased * target));
    if (progress < 1) requestAnimationFrame(frame);
    else el.textContent = String(target);
  }

  requestAnimationFrame(frame);
}
