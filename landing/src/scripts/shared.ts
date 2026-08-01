/**
 * The two things more than one behaviour needs: the reader's motion preference
 * and the status strip. Both were a file of their own; neither was ever going
 * to grow, and keeping them apart only spread three lines across two imports.
 */

/** Read once: every animated module needs the same answer. */
export const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const toast = document.getElementById('toast');
let timer: ReturnType<typeof setTimeout> | undefined;

/** Shared status strip. Anything that reports the outcome of a click uses it. */
export function showToast(message: string): void {
  if (!toast) return;
  toast.textContent = message;
  toast.classList.add('is-visible');
  clearTimeout(timer);
  timer = setTimeout(() => toast.classList.remove('is-visible'), 2400);
}
