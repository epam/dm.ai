# DMC-944 automated test

This test validates the live Newsletter editor preview for the timing-sensitive
Email -> Push -> None transition on Canary/non-CCTM, with explicit checks that
the envelope icon never reappears during a short stabilization window after
selecting `None`.

## Install dependencies

```bash
npm install --prefix testing
```

## Run this test

```bash
node testing/tests/DMC-944/test_dmc_944.js
```

## Required environment

- `DMC_944_EDITOR_URL` or `DMC_944_NEWSLETTERS_URL`
- authenticated browser state via `DMC_944_STORAGE_STATE_PATH` if the target
  app requires login

Optional overrides:

- `DMC_944_NEWSLETTER_NAME`
- `DMC_944_NONE_STATE_TEXT`
- `DMC_944_PUSH_STATE_TEXT`
- `DMC_944_EMAIL_STATE_TEXT`
- `DMC_944_RECIPIENT_LABEL`
- `DMC_944_LIVE_PREVIEW_LABEL`
- `DMC_944_BROWSER`
- `DMC_944_HEADLESS`

## Expected passing output

```text
PASS DMC-944: live preview updates from Email to Push to None without stale email icon reappearing.
```
