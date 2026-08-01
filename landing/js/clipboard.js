import { showToast } from './toast.js';

/**
 * Copy buttons for the install commands.
 *
 * The Clipboard API needs a secure context, so it is absent over plain http
 * and on file://. Failing loudly beats a button that looks like it worked.
 */
export function initClipboard() {
  document.querySelectorAll('[data-copy]').forEach((button) => {
    button.addEventListener('click', () => {
      const source = document.getElementById(button.dataset.copy);
      if (!source) return;

      const text = source.textContent.trim();

      if (!navigator.clipboard?.writeText) {
        showToast('Copy failed — select the command manually');
        return;
      }

      navigator.clipboard.writeText(text).then(
        () => showToast('Copied to clipboard'),
        () => showToast('Copy failed — select the command manually'),
      );
    });
  });
}
