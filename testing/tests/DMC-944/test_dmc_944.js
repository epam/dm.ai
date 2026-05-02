const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium, firefox, webkit } = require('playwright');

const { NewsletterEditorPage } = require('../../components/pages/newsletter_editor_page');

const TICKET_KEY = 'DMC-944';
const REPOSITORY_ROOT = path.resolve(__dirname, '../../..');
const OUTPUTS_DIR = path.join(REPOSITORY_ROOT, 'outputs');
const SCREENSHOT_DIR = path.join(OUTPUTS_DIR, 'screenshots', TICKET_KEY);
const RUN_DETAILS_PATH = path.join(OUTPUTS_DIR, `${TICKET_KEY.toLowerCase()}_run_details.json`);
const DEFAULT_TIMEOUT_MS = Number(process.env.DMC_944_TIMEOUT_MS || 30000);
const STABILIZATION_WINDOW_MS = Number(process.env.DMC_944_STABILIZATION_WINDOW_MS || 3000);
const STABILIZATION_POLL_MS = Number(process.env.DMC_944_STABILIZATION_POLL_MS || 250);

function env(name, fallback = '') {
  return (process.env[name] || fallback).trim();
}

function normalizeWhitespace(value) {
  return String(value || '').replace(/\s+/g, ' ').trim();
}

function ensureOutputsDirectory() {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

function browserLauncher(name) {
  const normalized = (name || 'chromium').toLowerCase();
  if (normalized === 'firefox') return firefox;
  if (normalized === 'webkit') return webkit;
  return chromium;
}

function buildConfig() {
  return {
    browser: env('DMC_944_BROWSER', 'chromium'),
    headless: env('DMC_944_HEADLESS', 'true').toLowerCase() !== 'false',
    editorUrl: env('DMC_944_EDITOR_URL'),
    newslettersUrl: env('DMC_944_NEWSLETTERS_URL'),
    newsletterName: env('DMC_944_NEWSLETTER_NAME'),
    storageStatePath: env('DMC_944_STORAGE_STATE_PATH'),
    recipientLabel: env('DMC_944_RECIPIENT_LABEL', 'Recipient type'),
    previewLabel: env('DMC_944_LIVE_PREVIEW_LABEL', 'Live Preview'),
    emailStateText: env('DMC_944_EMAIL_STATE_TEXT'),
    pushStateText: env('DMC_944_PUSH_STATE_TEXT'),
    noneStateText: env('DMC_944_NONE_STATE_TEXT'),
    baseTimeoutMs: DEFAULT_TIMEOUT_MS,
  };
}

function serializeError(error) {
  if (!error) {
    return null;
  }
  return {
    message: error.message || String(error),
    stack: error.stack || String(error),
  };
}

function buildStep(name, expected) {
  return {
    name,
    expected,
    status: 'pending',
    actual: '',
    screenshot: '',
  };
}

async function captureScreenshot(page, name) {
  const safeName = name.replace(/[^a-z0-9-]+/gi, '-').toLowerCase();
  const screenshotPath = path.join(SCREENSHOT_DIR, `${safeName}.png`);
  await page.screenshot({ path: screenshotPath, fullPage: true });
  return screenshotPath;
}

function expectationPattern(explicitValue, fallbackPattern) {
  if (explicitValue) {
    return new RegExp(explicitValue, 'i');
  }
  return fallbackPattern;
}

async function runScenario() {
  ensureOutputsDirectory();
  const config = buildConfig();
  const runDetails = {
    ticket: TICKET_KEY,
    status: 'failed',
    summary: '',
    startedAt: new Date().toISOString(),
    finishedAt: '',
    environment: {
      editorUrl: config.editorUrl,
      newslettersUrl: config.newslettersUrl,
      newsletterName: config.newsletterName,
      storageStatePath: config.storageStatePath,
      browser: config.browser,
      headless: config.headless,
      os: `${os.platform()} ${os.release()}`,
      stabilizationWindowMs: STABILIZATION_WINDOW_MS,
      stabilizationPollMs: STABILIZATION_POLL_MS,
    },
    steps: [
      buildStep(
        'Open newsletter editor',
        'Open the newsletters list or editor and land on a page where Recipient type and Live Preview are visible.',
      ),
      buildStep(
        'Switch to Email',
        'Recipient type Email shows the email/envelope preview state.',
      ),
      buildStep(
        'Switch to Push',
        'Live Preview updates away from Email without stale Email content.',
      ),
      buildStep(
        'Switch to None',
        'Live Preview immediately shows the None/no-recipient state and never re-renders the email/envelope icon during stabilization.',
      ),
    ],
    observations: {},
    consoleErrors: [],
    pageErrors: [],
    failure: null,
  };

  let browser;
  let context;
  let page;
  try {
    if (!config.editorUrl && !config.newslettersUrl) {
      throw new Error(
        'Missing live target configuration. Set DMC_944_EDITOR_URL or DMC_944_NEWSLETTERS_URL before running the Playwright scenario.',
      );
    }

    const launch = browserLauncher(config.browser);
    browser = await launch.launch({ headless: config.headless });
    context = await browser.newContext(
      config.storageStatePath ? { storageState: config.storageStatePath } : {},
    );
    page = await context.newPage();
    page.setDefaultTimeout(config.baseTimeoutMs);
    page.setDefaultNavigationTimeout(config.baseTimeoutMs);
    page.on('console', (message) => {
      if (message.type() === 'error') {
        runDetails.consoleErrors.push(message.text());
      }
    });
    page.on('pageerror', (error) => {
      runDetails.pageErrors.push(error.message || String(error));
    });

    const editorPage = new NewsletterEditorPage(page, {
      editorUrl: config.editorUrl,
      newslettersUrl: config.newslettersUrl,
      newsletterName: config.newsletterName,
      recipientLabel: config.recipientLabel,
      previewLabel: config.previewLabel,
      timeoutMs: config.baseTimeoutMs,
    });

    await editorPage.open();
    runDetails.steps[0].status = 'passed';
    runDetails.steps[0].actual = `Opened ${page.url()} and found the editor controls.`;
    runDetails.steps[0].screenshot = await captureScreenshot(page, '01-editor-open');

    await editorPage.selectRecipientType('Email');
    const emailSnapshot = await editorPage.previewSnapshot();
    const emailPattern = expectationPattern(config.emailStateText, /(email|subject|from|envelope|mail)/i);
    assert.equal(
      emailSnapshot.emailIconVisible || emailPattern.test(emailSnapshot.text),
      true,
      [
        'Expected the Email selection to render the email preview state.',
        `Preview text: ${emailSnapshot.text || '<empty>'}`,
        `Preview URL: ${emailSnapshot.url}`,
      ].join('\n'),
    );
    runDetails.observations.email = emailSnapshot;
    runDetails.steps[1].status = 'passed';
    runDetails.steps[1].actual = `Preview after Email showed: "${emailSnapshot.text || '<empty>'}" (emailIconVisible=${emailSnapshot.emailIconVisible}).`;
    runDetails.steps[1].screenshot = await captureScreenshot(page, '02-email-preview');

    await editorPage.selectRecipientType('Push');
    await page.waitForLoadState('networkidle').catch(() => {});
    const pushSnapshot = await editorPage.previewSnapshot();
    const pushPattern = expectationPattern(config.pushStateText, /(push|notification)/i);
    assert.notEqual(
      normalizeWhitespace(pushSnapshot.text),
      normalizeWhitespace(emailSnapshot.text),
      [
        'Expected the Push preview text to change away from the Email state.',
        `Email preview text: ${emailSnapshot.text || '<empty>'}`,
        `Push preview text: ${pushSnapshot.text || '<empty>'}`,
      ].join('\n'),
    );
    assert.equal(
      pushPattern.test(pushSnapshot.text) || !pushSnapshot.emailIconVisible,
      true,
      [
        'Expected the Push selection to remove the stale Email envelope state.',
        `Push preview text: ${pushSnapshot.text || '<empty>'}`,
        `emailIconVisible=${pushSnapshot.emailIconVisible}`,
      ].join('\n'),
    );
    runDetails.observations.push = pushSnapshot;
    runDetails.steps[2].status = 'passed';
    runDetails.steps[2].actual = `Preview after Push showed: "${pushSnapshot.text || '<empty>'}" (emailIconVisible=${pushSnapshot.emailIconVisible}).`;
    runDetails.steps[2].screenshot = await captureScreenshot(page, '03-push-preview');

    await editorPage.selectRecipientType('None');
    const noneImmediateSnapshot = await editorPage.previewSnapshot();
    assert.equal(
      noneImmediateSnapshot.emailIconVisible,
      false,
      [
        'Expected the envelope icon to disappear immediately after selecting None.',
        `Preview text: ${noneImmediateSnapshot.text || '<empty>'}`,
      ].join('\n'),
    );

    const nonePattern = expectationPattern(
      config.noneStateText,
      /(none|no recipient|no preview|not configured|not selected|empty preview)/i,
    );
    assert.equal(
      nonePattern.test(noneImmediateSnapshot.text) ||
        normalizeWhitespace(noneImmediateSnapshot.text) !== normalizeWhitespace(pushSnapshot.text),
      true,
      [
        'Expected the None state to present a no-recipient preview instead of stale Push content.',
        `Push preview text: ${pushSnapshot.text || '<empty>'}`,
        `None preview text: ${noneImmediateSnapshot.text || '<empty>'}`,
      ].join('\n'),
    );

    const stabilizationObservation = await editorPage.observeNoEmailIconDuringWindow(
      STABILIZATION_WINDOW_MS,
      STABILIZATION_POLL_MS,
    );
    assert.equal(
      stabilizationObservation.passed,
      true,
      [
        `Expected the email icon to stay absent for ${STABILIZATION_WINDOW_MS}ms after selecting None.`,
        ...stabilizationObservation.samples.map(
          (sample) =>
            `offset=${sample.offsetMs}ms emailIconVisible=${sample.emailIconVisible} text="${sample.text || '<empty>'}"`,
        ),
      ].join('\n'),
    );

    const noneFinalSnapshot =
      stabilizationObservation.samples[stabilizationObservation.samples.length - 1] ||
      noneImmediateSnapshot;
    runDetails.observations.none = {
      immediate: noneImmediateSnapshot,
      stabilized: noneFinalSnapshot,
      window: stabilizationObservation,
    };
    runDetails.steps[3].status = 'passed';
    runDetails.steps[3].actual =
      `Preview after None showed: "${noneImmediateSnapshot.text || '<empty>'}" immediately and ` +
      `the email icon stayed absent for ${STABILIZATION_WINDOW_MS}ms.`;
    runDetails.steps[3].screenshot = await captureScreenshot(page, '04-none-preview');

    runDetails.status = 'passed';
    runDetails.summary =
      'Live preview updated from Email to Push to None and the email/envelope icon did not reappear during the stabilization window.';
    return runDetails;
  } catch (error) {
    if (page) {
      try {
        const failureShot = await captureScreenshot(page, '99-failure');
        runDetails.failure = {
          screenshot: failureShot,
        };
      } catch (screenshotError) {
        runDetails.failure = runDetails.failure || {};
      }
    }
    runDetails.failure = {
      ...(runDetails.failure || {}),
      ...serializeError(error),
      currentUrl: page ? page.url() : '',
    };
    runDetails.summary = runDetails.failure.message || 'The DMC-944 live UI scenario failed.';
    return runDetails;
  } finally {
    runDetails.finishedAt = new Date().toISOString();
    fs.writeFileSync(RUN_DETAILS_PATH, `${JSON.stringify(runDetails, null, 2)}\n`, 'utf8');
    if (context) {
      await context.close().catch(() => {});
    }
    if (browser) {
      await browser.close().catch(() => {});
    }
  }
}

async function main() {
  const result = await runScenario();
  if (result.status === 'passed') {
    console.log(
      'PASS DMC-944: live preview updates from Email to Push to None without stale email icon reappearing.',
    );
    return;
  }

  console.error('FAIL DMC-944: live preview regression detected or the live test setup could not run.');
  if (result.failure && result.failure.stack) {
    console.error(result.failure.stack);
  } else if (result.failure && result.failure.message) {
    console.error(result.failure.message);
  }
  process.exitCode = 1;
}

main().catch((error) => {
  console.error(`FAIL ${TICKET_KEY}: ${error.stack || error.message || String(error)}`);
  process.exitCode = 1;
});
