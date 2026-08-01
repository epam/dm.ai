/** Shared status strip. Anything that reports the outcome of a click uses it. */
const toast = document.getElementById('toast');
let timer = null;

export function showToast(message) {
  if (!toast) return;
  toast.textContent = message;
  toast.classList.add('is-visible');
  clearTimeout(timer);
  timer = setTimeout(() => toast.classList.remove('is-visible'), 2400);
}
