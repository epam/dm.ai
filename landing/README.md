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
├── build.py                    assembles index.html; --check guards CI
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

**Stylesheet order is load-bearing.** The `<link>` tags in `index.html` set the
cascade. `a11y.css` must stay last: it overrides `.reveal`, `.bar__fill` and
`.flow__step::after` at equal specificity and only wins by coming after the
section files. Section files are otherwise independent — each owns its own
media queries.

**The theme is applied twice, on purpose.** An inline script in `<head>` sets
`data-theme` before first paint, because a module is deferred by definition and
would let the wrong theme flash. `js/theme.js` only handles changes after load.

**One gradient keyword per headline.** A brandbook rule, and the thing that
keeps the page reading as EPAM. Everything else stays monochrome.

**JavaScript is native ES modules.** `main.js` is loaded with
`type="module"`, so relative import paths must include the `.js` extension.

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
