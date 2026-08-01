# DMTools landing page

Static page served straight from this directory by
[`.github/workflows/deploy-landing.yml`](../.github/workflows/deploy-landing.yml).
No bundler, no dependencies, no build step — the workflow uploads these files
as they stand.

## Layout

```
landing/
├── index.html                  GENERATED — run build.py, do not edit
├── template.html               head, meta, structured data, GA, include markers
├── sections/*.html             one file per section; edit these
├── build.py                    assembles index.html and the CSS bundle;
│                               --check guards both in CI
├── robots.txt                  crawler policy; __SITE_URL__ filled at deploy
├── sitemap.xml                 one URL today, grows with the docs
├── llms.txt                    extractable summary for AI assistants
├── main.js                     entry point; imports the modules below
├── js/
│   ├── motion.js               reduced-motion preference, read once
│   ├── toast.js                shared status strip
│   ├── theme.js                Snow / Night switch
│   ├── nav.js                  sticky-header hairline
│   ├── counters.js             count-up for the hero and Numbers figures
│   ├── reveal.js               scroll reveal; drives counters and bar widths
│   ├── tabs.js                 install-command tablist
│   ├── clipboard.js            copy buttons
│   └── video.js                on-demand film player
├── styles/
│   ├── bundle.css              GENERATED — every sheet below, in cascade order
│   ├── tokens.css              palette, both themes, scoped overrides
│   ├── base.css                reset and base elements
│   ├── typography.css          type scale and text helpers
│   ├── layout.css              shell, band, split
│   ├── ui.css                  buttons, toast, reveal
│   ├── sections/*.css          one file per section
│   └── a11y.css                motion and print preferences
└── assets/
    ├── favicon.svg
    ├── film-poster.jpg         the frame YouTube uses, served locally
    └── og-cover.png            social card, 1200×630
```

## Editing the markup

Sections live in `sections/`. Edit one, then rebuild:

```bash
python landing/build.py
```

`template.html` holds everything that is not a section — the `<head>`, the
analytics block, the `<main>` wrapper — and pulls the parts in with
`<!-- include: sections/<name>.html -->` markers. Indentation on the marker
line is applied to the whole partial, so the built file stays readable.

`index.html` is generated **and committed**. That is deliberate: serving the
site then needs nothing but this directory, and a reviewer reading the repo
sees the real page. The cost is that the two can drift, so CI runs
`build.py --check` and fails if `index.html` no longer matches its sources. The
same check catches a section that no longer appears in the template and a
misspelt include path.

Nothing is installed to run any of this — `build.py` is standard library only.

## Rules worth knowing before editing

**Stylesheet order is load-bearing.** The `<link>` tags in `template.html` set
the cascade. `a11y.css` must stay last: it overrides `.reveal`, `.bar__fill` and
`.flow__step::after` at equal specificity and only wins by coming after the
section files. Section files are otherwise independent — each owns its own
media queries.

**The stylesheets ship as one file.** Eighteen `<link>` tags are eighteen
render-blocking requests for ~47 KB that never changes between them, so
`build.py` concatenates them into `styles/bundle.css` and emits a single link.
Authoring stays split by concern; only delivery is joined. The order comes from
the `<link>` tags in `template.html` rather than a list inside `build.py`, so
the two cannot disagree — add a section stylesheet by adding its tag, as before.
`bundle.css` is generated and committed, and `--check` guards it alongside
`index.html`.

**The theme is applied twice, on purpose.** An inline script in `<head>` sets
`data-theme` before first paint, because a module is deferred by definition and
would let the wrong theme flash. `js/theme.js` only handles changes after load.

**Night is the default, not the OS preference.** The page is designed as a dark
surface; Snow is the alternate a reader opts into with the toggle, and a stored
choice wins over both. `theme.js` deliberately carries no
`prefers-color-scheme` listener — following a mid-session OS change to light
would drop a reader onto Snow without them asking.

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

**JavaScript is native ES modules.** `main.js` is loaded with
`type="module"`, so relative import paths must include the `.js` extension.

## The published site URL

`canonical`, `og:url`, `og:image`, three Schema.org fields, `sitemap.xml` and
`robots.txt` all need an **absolute** URL, and the correct one differs per
repository: a fork publishes to its own `github.io` path. Hardcoding one domain
means every fork ships broken social previews and a canonical pointing at
someone else's site.

So the sources carry a `__SITE_URL__` placeholder, and the deploy workflow
substitutes the address that `actions/configure-pages` reports for whichever
repository is publishing. Nothing to configure after forking, and the step fails
loudly if a placeholder survives.

The placeholder is what sits in the committed `index.html` — like the GA block
below, the file is deploy-ready rather than browser-ready in that one respect.

## Google Analytics

`index.html` ships a `__GA_MEASUREMENT_ID__` placeholder wrapped in
`<!-- ga:start -->` / `<!-- ga:end -->` markers. At deploy time the workflow
substitutes the `GA_MEASUREMENT_ID` **repository variable** — not a secret: a
GA4 measurement ID is public the moment the page is served. Leave the variable
unset and the whole block is removed, so the page ships with no tracking. The
step fails loudly if a placeholder survives either path.

## Working on it locally

```bash
cd landing
python -m http.server 8000
```

Then open <http://127.0.0.1:8000/>. Serving over HTTP rather than opening the
file directly matters: the Clipboard API needs a secure context, and video
embeds are refused from `file://` origins.
