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

**Factory workflows** live in [`src/lib/factory-templates/`](src/lib/factory-templates/).
The folders separate platform-specific workflows from reusable configuration:

- [`github/`](src/lib/factory-templates/github/): GitHub Actions workflow templates.
- [`gitlab/machine-sm.yml`](src/lib/factory-templates/gitlab/machine-sm.yml): the GitLab caller pipeline that includes the pinned `gitlab/factory-sm.yml` parent from dmtools-agentic-workflows.
- [`gitlab/ai-teammate.yml`](src/lib/factory-templates/gitlab/ai-teammate.yml): the issue/MR teammate caller that extends the pinned `gitlab/factory-teammate.yml` parent.
- [`shared/`](src/lib/factory-templates/shared/): runner JSON templates and agent configuration used by both platforms.

GitLab setup generates `.gitlab-ci.yml`, `ai-teammate.yml`,
`.dmtools/config.js`, and GitLab-compatible runner JSON files under
`.dmtools/runners/` when they do not already exist. Existing `.dmtools/`
configuration is preserved; review the setup MR and ensure
`.dmtools/config.js` points to GitLab-compatible runner files before running
the pipeline. The root pipeline includes the pinned `gitlab/factory-sm.yml`
parent and the local teammate caller. Both parent templates are pinned to the
same immutable commit from the GitLab-support branch of dmtools-agentic-workflows. The
generated files are thin callers: tool installation, token selection, the
queue scan, and the teammate helpers described below live in the shared parents
and are enabled by `DMTOOLS_GITLAB_INSTALL_TOOLS: "true"` and
`DMTOOLS_GITLAB_FACTORY_HELPERS: "true"` in `.gitlab-ci.yml`. The
included `factory-sm` job runs on schedules; to run it manually, start a web pipeline with
`DMTOOLS_MACHINE_SM=true`. Scheduled runs apply actions; manual machine-SM
runs default to dry-run. Set `SM_DRY_RUN=false` in the manual pipeline
variables to apply actions.

The `ai-teammate` job supports one project-local issue or merge-request IID.
In GitLab, open **Build → Pipelines → Run pipeline**, select the default
branch, enter an IID in either `FACTORY_ISSUE` or `FACTORY_MR` (not both),
then run the pipeline. These variables are declared in `.gitlab-ci.yml` so
GitLab exposes them in the run form. Web, API, and trigger pipelines are
supported. Leave
`FACTORY_LEG` empty to let the parent derive the action from the issue/MR
labels and machine assignment, or set it to `dev`, `bug`, `story`, `review`,
or `rework`. The parent requires the include SHA in `FACTORY_HOME_REF` to
match the immutable commit used by `ai-teammate.yml`.

To automatically run the `dev` or `rework` teammate for queued issues, create a pipeline
schedule in **Build → Pipeline schedules** on the default branch with cron
`*/10 * * * *`. Every scheduled pipeline automatically runs both `factory-sm`
and the shared parent's `ai-teammate-scan`; no scan opt-in variable or issue IID is required.
The scan selects open issues with the `agent:rework` label, regardless of
assignment, or with the `agent:dev` label assigned to an account
whose username or display name is `ai-dev`, including project bots with
generated usernames. Set `DMTOOLS_AI_DEV_USERNAME` only to override that
assignee selector (an exact username is recommended if display names are
ambiguous). The scan also queues review and rework for open merge requests.
It starts one API pipeline per matching issue or MR and passes
the resolved actual username as `AGENT_HANDLE` to `ai-teammate-auto`, which
extends the shared teammate parent. The parent checks whether the issue is
eligible and derives the leg from live labels (`agent:rework` takes precedence).
Issues held by `agent:skip`, `blocked`, `provider_quota`, or `needs-human`
are skipped, as are rework issues already labeled `status:In Rework`.
Dev issues waiting for review, answers, or an active leg are also skipped.
Use the schedule's **Run** action to scan immediately.
The same scan also keeps merge request validation labels in sync with the
head pipeline, like the GitHub state machine: `ai_validating` while CI runs on
the current head, `ai_validated` when it succeeds, and `validation_failed` when
it fails. A moved head or a missing head pipeline removes a stale `ai_validated`.
Draft MRs are skipped. `ai_validated` enables the `review-after-dev` and
`review-machine-unlinked` state-machine rules.
When the head pipeline of a machine-authored MR fails, the scan arms rework the
way GitHub's `rework-on-validation-failed` rule does. It adds `agent:rework` to
the linked issue (`Closes #N` or an `ai/gl-<IID>` branch), so the next scan
dispatches it, or to the MR itself, where the state machine's `rework-on-label`
rule picks it up. A marker note limits this to one arm per head SHA. If CI is
still red on the same head after that rework, the arm is withheld:
`needs-human` is added once to the MR and its issue, with an explanatory note.
A new push restarts the budget. MRs and issues that are already in rework or
held (`needs-human`, `blocked`, `agent:skip`, `provider_quota`) are not armed.
If the MR notes cannot be read, the arm is skipped for that scan.
If pipeline creation fails, the scan fails and logs GitLab's API response body
to help diagnose validation errors or pipeline-variable restrictions.
Dispatched pipelines also pass the pinned `FACTORY_HOME_REF` as a pipeline
variable, so a project or group CI/CD variable with the same name cannot
override it. Remove any such stale variable anyway.
Before the parent script runs, both teammate jobs log the issue's state, labels,
and assignees. They also resolve `AGENT_HANDLE` from an assignee display name
(for example `ai-dev`) to that assignee's actual username, because the parent
derives the dev leg only from exact username matches. This also covers manual
web runs. The same step creates `.git/info`, which GitLab Runner clones omit
but the parent needs for its `.git/info/exclude` entries. It also removes the
runner's `url.*.insteadOf` and credential entries that carry the read-only
`gitlab-ci-token`, from whichever git config file defines them (GitLab Runner
usually adds them through an `include.path` template config). Otherwise git
pushes with the CI job token and GitLab rejects the branch push ("You are not
allowed to push code to this project"), so no merge request is created. The
job then logs the effective push URL (credentials masked), the `GITLAB_TOKEN`
access level and scopes, and the result of a `git push --dry-run` with
`GITLAB_TOKEN`, warning when the token cannot push.
After a successful dev leg, both teammate jobs also require an open merge
request linked to the issue before review is queued. If the pack's MR lacks the
closing reference, the job links the `ai/gl-<IID>` branch MR by prepending
`Closes #<IID>`. If no MR exists (for example because the branch push failed),
the job fails, swaps `agent:review` for `needs-human`, and comments on the
issue instead of reporting success.
When the MR exists, the job adds `ai_developed` and removes `agent:dev`, so the
issue is no longer queued for another development run. The issue remains queued
for review via `agent:review`. The agent pack cannot label GitLab issues
itself. When a new dev leg starts, a stale `ai_developed` from a previous cycle
is cleared first, so the label always marks the latest implementation. A dev
leg that ends in ask-mode
(`ai_questions_asked`) is not treated as a missing MR.
After every rework or review leg, whether it succeeds, fails, or is canceled,
both teammate jobs close the cycle on the linked counterpart too, like GitHub's
`close-rework-cycle.sh` and `close-review-cycle.sh`. The parent relabels only
the issue or MR it ran on. This step also removes `agent:rework` and
`status:In Rework` (or `agent:review` and `status:In Review`) from the linked
MR or issue. A failed or canceled rework removes only `status:In Rework`, so
`agent:rework` stays and the rework is retried. The link comes from the issue's open related MR (preferring
`ai/gl-<IID>`), or from the MR's `Closes #<IID>` reference or `ai/gl-<IID>`
branch. After a successful rework, the MR's `ai_validated`, `ai_validating`,
`validation_failed`, `ai_pr_reviewed`, and `pr_approved` labels are cleared,
because the head moved. After a successful review, the MR gets `ai_pr_reviewed`
for any verdict, so `review-external-once` does not review it again. An
approval also clears `rework-round:<N>` and `needs-human`.
The schedule interval is configured in GitLab, not in the YAML, and GitLab
instance schedule limits apply.

The generated `.dmtools/config.js` configures the machine-SM and teammate
runners (`sm.runners.bug`, `story`, `review`, and `rework`). As with the GitHub
factory, the runners keep the agent pack's own pre/post actions: the dev leg
creates the branch, then commits, pushes, and opens the merge request; rework
pushes to the existing merge request; review publishes its findings. The parent
template supplies `GITLAB_TOKEN` push credentials and the protected-branch push
guard. The runners set `customParams.scmProvider` to `gitlab` and
`targetRepository.owner`/`repo` to the project group and name, so the pack
calls GitLab merge request APIs instead of GitHub. Dev runners require the MR
description to start with `Closes #<IID>`, which lets the review leg resolve
the MR from the issue. The review runner also writes the `decision` field that
the parent reads. The generated
pipeline installs the required tools in its default Temurin image. Configure
masked `GITLAB_TOKEN` and
`SOURCE_GITHUB_TOKEN` CI/CD variables. `GITLAB_TOKEN` needs GitLab `api` and
`write_repository` scopes plus project access; the GitHub token is used to
resolve and download the DMTools CLI and agent-pack releases.
Project and group CI/CD variables take precedence over job `variables:` in
`.gitlab-ci.yml`, so tokens are selected in the job script, not by YAML
mapping. Every job's shared `before_script` exports
`GITLAB_DEV_TOKEN` as `GITLAB_TOKEN` whenever it is set (in either mode), for
the machine SM, scanning, development, rework, pushes and MR creation, and logs
the token name and its GitLab user. In multi-teammate mode
(`DMTOOLS_GITLAB_TEAMMATES: "multiple"`) the job fails if `GITLAB_DEV_TOKEN` is
missing. Before a review leg (`FACTORY_LEG=review`, or an `agent:review` label
without `status:In Review`), `ai-teammate` and `ai-teammate-auto` switch
`GITLAB_TOKEN` to `GITLAB_REVIEWER_TOKEN` whenever it is set (and, in multi-teammate mode,
`AGENT_HANDLE` to the reviewer account), so review notes, MR comments and label
changes come from the reviewer. In multi-teammate mode the job fails if
`GITLAB_REVIEWER_TOKEN` is missing on a review leg.

Run `npm test` to check workflow rendering and GitLab shell syntax.

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

### Custom domain

When the repository uses a GitHub Pages custom domain, `configure-pages` reports
that domain and `base` becomes `/`. The DNS record must point to GitHub Pages
(CNAME to `<user>.github.io` or A records to GitHub's IPs) and the domain must
be entered in **Settings → Pages → Custom domain**. GitHub then provisions the
SSL certificate and `Enforce HTTPS` can be enabled. After changing the domain,
re-run the deploy workflow so the build picks up the new `SITE_URL`.

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
