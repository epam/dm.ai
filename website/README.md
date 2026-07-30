# DMTools landing page

A marketing single-page app (Vite + React + TypeScript) for
[DMTools](https://github.com/epam/dm.ai), EPAM's open-source enterprise
AI-factory orchestrator. Built in the EPAM Brandbook 2023 "Night" theme.

## Local development

```bash
cd website
npm install
npm run dev
```

Build and preview the production bundle (respects the `/dm.ai/` base path
used for GitHub Pages):

```bash
npm run build
npm run preview
```

## Google Analytics

Analytics is entirely opt-in and configured at build time via an environment
variable — **no tracking ID is committed to the repository**.

- Set a repository secret named `GA_MEASUREMENT_ID` with your GA4 Measurement
  ID (format `G-XXXXXXXXXX`).
- `.github/workflows/deploy-landing.yml` passes it to the build as
  `VITE_GA_MEASUREMENT_ID`.
- [`src/analytics.ts`](src/analytics.ts) loads `gtag.js` only if that
  variable is present; otherwise it is a no-op, so local dev and any fork
  without the secret never send data.

For local testing with analytics enabled, create `website/.env.local`:

```bash
VITE_GA_MEASUREMENT_ID=G-XXXXXXXXXX
```

## Deployment (GitHub Pages)

[`../.github/workflows/deploy-landing.yml`](../.github/workflows/deploy-landing.yml)
builds this app and publishes `website/dist` to GitHub Pages on every push to
`main` that touches `website/**`, plus manual `workflow_dispatch` runs.

One-time setup required from a repository admin (a pull request cannot change
repository settings):

1. **Settings → Pages → Build and deployment → Source** → select
   **GitHub Actions**.
2. **Settings → Secrets and variables → Actions** → add `GA_MEASUREMENT_ID`
   (optional — omit it to ship without analytics).
3. Push to `main` or run the workflow manually via
   **Actions → Deploy landing page to GitHub Pages → Run workflow**.

The site will be available at `https://<org-or-user>.github.io/dm.ai/`.

## Structure

```
website/
  src/
    components/     one component per landing-page section
    styles/
      tokens.css    EPAM Night design tokens (color, type, spacing)
      app.css       component styles
    analytics.ts    env-gated Google Analytics loader
    App.tsx         assembles all sections
  vite.config.ts    base: '/dm.ai/' for GitHub Pages project-site hosting
```

## Notes on brand assets

Content and the EPAM Night color palette are based on the public DMTools
promo-film brief (`branded/epam/dmtools/GOAL.md` in
[IstiN/yoclip-examples](https://github.com/IstiN/yoclip-examples)), used here
for brand/content reference only. That project bundles **Museo Sans**, a
commercial font — this SPA instead uses **Sora** (Google Fonts, SIL Open Font
License) as an open, visually-similar substitute so it can be distributed
under this repository's Apache-2.0 license.
