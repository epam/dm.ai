# DMTools landing page

The landing page and the reference documentation, built with
[Astro](https://astro.build). Components and data are TypeScript; the output is
plain HTML and CSS with about 2 KB of JavaScript for the theme switch, the menu,
the tabs and the copy buttons. No framework runtime ships to the browser.

The landing is a single page. The reference pages are generated from
`dmtools-ai-docs/`, the sibling directory contributors already edit, so a build
reports many more pages than there are files here.

## Layout

```
landing/
├── astro.config.mjs            site and base come from $SITE_URL
├── src/
│   ├── content.config.ts       the docs collection, loaded from ../dmtools-ai-docs
│   ├── pages/
│   │   ├── index.astro         the landing: a Layout and thirteen sections
│   │   ├── docs/
│   │   │   ├── index.astro     the reference index
│   │   │   └── [...slug].astro one page per Markdown file in the collection
│   │   ├── pricing.md.ts       generated, the pricing an assistant can read
│   │   ├── robots.txt.ts       generated, so the address stays in one place
│   │   └── sitemap.xml.ts      likewise
│   ├── layouts/
│   │   ├── Layout.astro        head, meta, structured data, the theme script
│   │   └── Doc.astro           the same, for a reference page
│   ├── sections/*.astro        one file per section; edit these
│   ├── components/             the markup that stood in more than one place
│   │   ├── SectionHead.astro   eyebrow + headline + standfirst (used 7×)
│   │   ├── CopyButton.astro    button, icon and the clipboard script (5×)
│   │   ├── Brand.astro         the wordmark (2×)
│   │   ├── PlayIcon.astro      (2×)
│   │   ├── BridgeDiagram.astro the layer diagram, laid out from data
│   │   └── Analytics.astro     the gtag block; both layouts render it
│   ├── data/content.ts         everything the page states, stated once
│   ├── lib/
│   │   ├── docs.ts             ids, titles and the reference navigation tree
│   │   └── remark-doc-links.mjs rewrites Markdown links to resolve as HTML
│   ├── scripts/shared.ts       motion preference and the status strip
│   └── styles/                 imported by the layouts, in this order
│       ├── tokens.css          palette, both themes, scoped overrides
│       ├── base.css            reset, type scale, shell and band, buttons
│       ├── sections.css        one banner-commented block per section
│       ├── docs.css            the reference pages; Doc.astro only
│       └── a11y.css            motion and print preferences; must stay last
└── public/
    ├── llms.txt                extractable summary for AI assistants
    └── assets/
        ├── favicon.svg         the DMT mark
        ├── dmt-mark.png        the same mark rasterised, for the structured data
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

**Text that appears twice or more** lives in `src/data/content.ts`. The FAQ is
the reason this rule exists: its eight questions render both as the visible
accordion and as the `FAQPage` structured data, and when they were typed out
separately the only thing keeping them in step was a comment asking the next
maintainer to edit both. Markup that contradicts the visible page is a
manual-action risk.

The same holds for the bridge diagram. Its fourteen pills used to carry
hand-written coordinates, with fourteen bezier curves whose control points had
to be moved with them. Adding an integration is now a line in `content.ts`;
`BridgeDiagram.astro` places it.

## Rules worth knowing before editing

**Stylesheet order is load-bearing.** The four imports at the top of
`Layout.astro` set the cascade. `a11y.css` must stay last: it overrides
`.reveal`, `.bar__fill` and `.flow__arrow::after` at equal specificity and only
wins by coming after the section rules. Inside `sections.css` the blocks are
independent — each owns its own media queries — but the file order is not, so
keep new rules in their banner-marked block rather than appending at the end.

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

`--sea-ink` is the same problem one size down. The eyebrows and the numbered
steps are 11px, so they need 4.5:1, and `--sea` measures 4.01:1 against Snow's
canvas — just under. `--sea-ink` is that hue and saturation pulled dark enough
to clear it, in both directions: as eyebrow ink, and as the fill the step number
sits on. Night's `--sea` is already 15:1 there and aliases straight back. Reach
for `--sea` when the accent is a line or a large mark, `--sea-ink` when small
text is involved either way round.

**One gradient keyword per headline — plus the counted figures.** A brandbook
rule and the thing that keeps the page reading as EPAM. The gradient carries the
one keyword per headline, the `.figure__value` counters, and the bars that
measure them; everything else stays monochrome. Gradient text is a transparent
fill over a clipped background, so `a11y.css` restores solid ink for print,
where backgrounds are dropped and the text would otherwise vanish.

**Behaviour lives beside the markup it drives.** A `<script>` inside a component
is bundled by Astro, not inlined per use, so `CopyButton.astro` carries the
clipboard code once no matter how many buttons the page renders. The scroll
reveal is the exception: `.reveal` appears in every section, so it sits in
`Layout.astro` rather than in any one of them.

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

It is a **repository** variable, not an environment one. The value is read by
the `build` job, which declares no environment, so a variable scoped to
`github-pages` would never reach it.

`components/Analytics.astro` owns the block and both layouts render it. Keeping
it in Layout.astro alone is what left every reference page uncounted.

## Deploying

[`.github/workflows/deploy-landing.yml`](../.github/workflows/deploy-landing.yml)
publishes the default branch to GitHub Pages: check out, install with `npm ci`,
ask `configure-pages` for the address this repository publishes to, build with
that address in `SITE_URL`, and upload `landing/dist`.

`Setup Pages` has to stay ahead of `Build` — it is what reports the address, and
the build needs it. The build step fails loudly if it comes back empty, because
`astro.config.mjs` would otherwise fall back to `http://localhost:4321` and ship
a canonical pointing at nothing.

Nothing is committed for the workflow to patch: the page is generated on each
run, so `dist/` stays out of the repository.
