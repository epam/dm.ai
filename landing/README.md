# DMTools landing page

One static page, built with [Astro](https://astro.build). Components and data
are TypeScript; the output is plain HTML and CSS with about 2 KB of JavaScript
for the theme switch, the menu, the tabs and the copy buttons. No framework
runtime ships to the browser.

## Layout

```
landing/
├── astro.config.mjs            site and base come from $SITE_URL
├── src/
│   ├── pages/
│   │   ├── index.astro         the page: a Layout and thirteen sections
│   │   ├── robots.txt.ts       generated, so the address stays in one place
│   │   └── sitemap.xml.ts      likewise
│   ├── layouts/Layout.astro    head, meta, structured data, the theme script
│   ├── sections/*.astro        one file per section; edit these
│   ├── components/             the markup that stood in more than one place
│   │   ├── SectionHead.astro   eyebrow + headline + standfirst (used 7×)
│   │   ├── CopyButton.astro    button, icon and the clipboard script (5×)
│   │   ├── Brand.astro         the wordmark (2×)
│   │   ├── PlayIcon.astro      (2×)
│   │   └── BridgeDiagram.astro the layer diagram, laid out from data
│   ├── data/                   what the page states, stated once
│   │   ├── site.ts             meta, the film, the fields of the schema blocks
│   │   ├── faq.ts              renders the accordion AND the FAQPage schema
│   │   ├── nav.ts              header links and the four footer columns
│   │   ├── integrations.ts     the two diagram columns and the bar chart
│   │   ├── flow.ts             the seven architecture steps
│   │   ├── paths.ts            the four adoption cards
│   │   └── install.ts          the install tabs and their commands
│   ├── scripts/
│   │   ├── shared.ts           motion preference and the status strip
│   │   └── reveal.ts           scroll reveal, the counters and the bar widths
│   └── styles/
│       ├── index.css           imports every sheet below, in cascade order
│       ├── tokens.css          palette, both themes, scoped overrides
│       ├── base.css            reset and base elements
│       ├── typography.css      type scale and text helpers
│       ├── layout.css          shell, band, split
│       ├── ui.css              buttons, toast, reveal
│       ├── sections/*.css      one file per section
│       └── a11y.css            motion and print preferences
└── public/
    ├── llms.txt                extractable summary for AI assistants
    └── assets/
        ├── favicon.svg
        ├── film-poster.jpg     the frame YouTube uses, served locally
        └── og-cover.png        social card, 1200×630
```

## Working on it

```bash
cd landing
npm install
npm run dev        # http://localhost:4321
npm run build      # writes dist/
npm run preview    # serve dist/ as it will be published
npm run check      # TypeScript and Astro diagnostics
```

`dist/` is generated and **not** committed — the deploy workflow builds it.

## Editing

**Text that appears once** lives in its section under `src/sections/`.

**Text that appears twice or more** lives in `src/data/`. The FAQ is the reason
this rule exists: its eight questions render both as the visible accordion and
as the `FAQPage` structured data, and when they were typed out separately the
only thing keeping them in step was a comment asking the next maintainer to edit
both. Markup that contradicts the visible page is a manual-action risk.

The same holds for the bridge diagram. Its fourteen pills used to carry
hand-written coordinates, with fourteen bezier curves whose control points had
to be moved with them. Adding an integration is now a line in
`src/data/integrations.ts`; `BridgeDiagram.astro` places it.

## Rules worth knowing before editing

**Stylesheet order is load-bearing.** `src/styles/index.css` sets the cascade
with `@import`. `a11y.css` must stay last: it overrides `.reveal`, `.bar__fill`
and `.flow__step::after` at equal specificity and only wins by coming after the
section files. Section files are otherwise independent — each owns its own media
queries. The order lives in CSS rather than in a list of JavaScript imports
because `@import` guarantees it and bundler ordering does not.

**The theme is applied twice, on purpose.** An inline `is:inline` script in
`<head>` sets `data-theme` before first paint. `is:inline` is what makes it
work: any script Astro processes is deferred by definition, which would let the
wrong theme flash. The script in `Nav.astro` only handles changes after load.

**Night is the default, not the OS preference.** The page is designed as a dark
surface; Snow is the alternate a reader opts into with the toggle, and a stored
choice wins over both. There is deliberately no `prefers-color-scheme` listener
— following a mid-session OS change to light would drop a reader onto Snow
without them asking.

**Snow is not a recolour of Night.** Its accents are darkened so they hold WCAG
AA as *text* on a light canvas, which makes them too light to use as a *fill*:
white on Snow's mint measures 3.42:1, under AA for a 15px label. That is why the
filled button reads `--grad-btn` rather than `--grad` — Snow gives it its own
darker stops, Night aliases it straight back to `--grad`. `--btn-ink` flips with
the theme for the same reason. Any scope that restates the Night accents
(`.panel--dark`, `.footer`) has to restate both of those tokens with them.

**One gradient keyword per headline — plus the counted figures.** A brandbook
rule and the thing that keeps the page reading as EPAM. The gradient carries the
one keyword per headline, the `.figure__value` counters, and the bars that
measure them; everything else stays monochrome. Gradient text is a transparent
fill over a clipped background, so `a11y.css` restores solid ink for print,
where backgrounds are dropped and the text would otherwise vanish.

**Behaviour lives beside the markup it drives.** A `<script>` inside a component
is bundled by Astro, not inlined per use, so `CopyButton.astro` carries the
clipboard code once no matter how many buttons the page renders. Only
`reveal.ts` is loaded globally, because `.reveal` appears in every section.

## The published site URL

`canonical`, `og:url`, `og:image`, three Schema.org fields, `sitemap.xml` and
`robots.txt` all need an **absolute** URL, and the correct one differs per
repository: a fork publishes to its own `github.io` path. Hardcoding one domain
means every fork ships broken social previews and a canonical pointing at
someone else's site.

So `astro.config.mjs` reads `SITE_URL` from the environment and splits it into
Astro's `site` and `base`. The `base` half matters on a project page: the site
lives under `/<repo>/`, and every generated asset link has to carry that prefix.
Unset, it falls back to `http://localhost:4321` for local work.

## Google Analytics

Set `GA_MEASUREMENT_ID` at build time and the gtag block is rendered; leave it unset
and it is never emitted, so the page ships with no tracking. A GA4 measurement
ID is public the moment the page is served, so it belongs in a repository
variable rather than a secret.

## Deploying

> **This part is not done yet.** `.github/workflows/deploy-landing.yml` still
> expects the previous no-build setup and will fail on the first run: it calls
> `python3 landing/build.py --check`, and that script no longer exists.

The workflow needs four changes:

1. **Replace** the `Verify index.html matches its sources` step with a Node
   setup and an install:

   ```yaml
   - uses: actions/setup-node@v4
     with:
       node-version: 20
       cache: npm
       cache-dependency-path: landing/package-lock.json
   - run: npm ci
     working-directory: landing
   ```

2. **Delete** the `Inject Google Analytics measurement ID` and `Substitute the
   published site URL` steps. Both patched placeholders into a committed
   `index.html`; there is no committed HTML any more, and both values are now
   build inputs.

3. **Add** the build after `Setup Pages` — that step is what reports the address
   this repository publishes to, so it has to come first:

   ```yaml
   - run: npm run build
     working-directory: landing
     env:
       SITE_URL: ${{ steps.pages.outputs.base_url }}
       GA_MEASUREMENT_ID: ${{ vars.GA_MEASUREMENT_ID }}
   ```

4. **Point the upload at the build output:** `path: landing/dist`.

The placeholder guards that failed the run if a `__SITE_URL__` survived are no
longer needed — there is nothing to substitute, and a missing `SITE_URL` shows
up as a localhost canonical rather than a broken one.
