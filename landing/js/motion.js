/** Read once: every animated module needs the same answer. */
export const prefersReducedMotion =
  window.matchMedia('(prefers-reduced-motion: reduce)').matches;
