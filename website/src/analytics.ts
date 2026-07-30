// Google Analytics (GA4), wired via a build-time environment variable so no
// tracking ID is ever hard-coded into the repository. Set VITE_GA_MEASUREMENT_ID
// as a GitHub Actions secret (see .github/workflows/deploy-landing.yml and
// website/README.md) — without it, this module is a no-op.

declare global {
  interface Window {
    dataLayer: unknown[];
  }
}

export function initAnalytics(): void {
  const measurementId = import.meta.env.VITE_GA_MEASUREMENT_ID as string | undefined;
  if (!measurementId) {
    return;
  }

  const script = document.createElement('script');
  script.async = true;
  script.src = `https://www.googletagmanager.com/gtag/js?id=${measurementId}`;
  document.head.appendChild(script);

  window.dataLayer = window.dataLayer || [];
  function gtag(...args: unknown[]) {
    window.dataLayer.push(args);
  }
  gtag('js', new Date());
  gtag('config', measurementId);
}
