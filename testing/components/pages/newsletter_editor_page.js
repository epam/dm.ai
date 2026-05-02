const { setTimeout: delay } = require('node:timers/promises');

class NewsletterEditorPage {
  constructor(page, options = {}) {
    this.page = page;
    this.options = {
      editorUrl: options.editorUrl || '',
      newslettersUrl: options.newslettersUrl || '',
      newsletterName: options.newsletterName || '',
      recipientLabel: options.recipientLabel || 'Recipient type',
      previewLabel: options.previewLabel || 'Live Preview',
      timeoutMs: Number(options.timeoutMs || 30000),
    };
  }

  async open() {
    if (this.options.editorUrl) {
      await this.page.goto(this.options.editorUrl, { waitUntil: 'domcontentloaded' });
      await this.page.waitForLoadState('networkidle').catch(() => {});
      await this.waitForReady();
      return;
    }

    if (!this.options.newslettersUrl) {
      throw new Error(
        'DMC_944_EDITOR_URL or DMC_944_NEWSLETTERS_URL must be configured for the live UI run.',
      );
    }

    await this.page.goto(this.options.newslettersUrl, { waitUntil: 'domcontentloaded' });
    await this.page.waitForLoadState('networkidle').catch(() => {});

    const opened = await this.openNewsletterFromList();
    if (!opened) {
      throw new Error(
        [
          'Could not open a newsletter editor from the newsletters list.',
          this.options.newsletterName
            ? `Configured newsletter name: ${this.options.newsletterName}`
            : 'No DMC_944_NEWSLETTER_NAME was configured, so the test attempted to open the first visible edit/open action.',
          `Current URL: ${this.page.url()}`,
        ].join(' '),
      );
    }

    await this.waitForReady();
  }

  async waitForReady() {
    await this.recipientControl().waitFor({ state: 'visible', timeout: this.options.timeoutMs });
    const preview = await this.previewRoot();
    await preview.waitFor({ state: 'visible', timeout: this.options.timeoutMs });
  }

  recipientControl() {
    return this.page.getByLabel(new RegExp(this.escapeForRegex(this.options.recipientLabel), 'i'));
  }

  async selectRecipientType(value) {
    const exactName = new RegExp(`^${this.escapeForRegex(value)}$`, 'i');
    const recipientControl = this.recipientControl().first();

    if (await this.isUsable(recipientControl)) {
      const tagName = await recipientControl.evaluate((element) => element.tagName.toLowerCase());
      if (tagName === 'select') {
        try {
          await recipientControl.selectOption({ label: value });
        } catch (error) {
          await recipientControl.selectOption(value.toLowerCase());
        }
        return;
      }
    }

    const radio = this.page.getByRole('radio', { name: exactName });
    if (await this.isUsable(radio)) {
      await radio.check({ force: true });
      return;
    }

    const tab = this.page.getByRole('tab', { name: exactName });
    if (await this.isUsable(tab)) {
      await tab.click();
      return;
    }

    const button = this.page.getByRole('button', { name: exactName });
    if (await this.isUsable(button)) {
      await button.click();
      return;
    }

    if (await this.isUsable(recipientControl)) {
      await recipientControl.click();
      const option = await this.firstUsable([
        this.page.getByRole('option', { name: exactName }),
        this.page.getByRole('menuitemradio', { name: exactName }),
        this.page.getByText(exactName).first(),
      ]);
      if (option) {
        await option.click();
        return;
      }
    }

    throw new Error(`Could not find a user-visible control to select recipient type "${value}".`);
  }

  async previewRoot() {
    const previewHeading = await this.firstUsable([
      this.page.getByRole('heading', {
        name: new RegExp(this.escapeForRegex(this.options.previewLabel), 'i'),
      }),
      this.page.getByText(new RegExp(`^${this.escapeForRegex(this.options.previewLabel)}$`, 'i')),
    ]);

    if (!previewHeading) {
      throw new Error(`Could not find the "${this.options.previewLabel}" heading or label.`);
    }

    const semanticContainer = previewHeading.locator(
      'xpath=ancestor::*[self::section or self::aside or self::main or self::div][1]',
    );
    if (await this.isUsable(semanticContainer)) {
      return semanticContainer;
    }

    return previewHeading;
  }

  async previewSnapshot() {
    const preview = await this.previewRoot();
    const text = this.normalizeWhitespace(await preview.innerText().catch(() => ''));
    const html = this.normalizeWhitespace(await preview.innerHTML().catch(() => ''));
    return {
      url: this.page.url(),
      text,
      html,
      emailIconVisible: await this.emailIconVisible(preview),
    };
  }

  async emailIconVisible(preview = null) {
    const previewRoot = preview || (await this.previewRoot());
    const iconLocator = previewRoot.locator(
      [
        '[aria-label*="email" i]',
        '[aria-label*="envelope" i]',
        '[title*="email" i]',
        '[title*="envelope" i]',
        '[alt*="email" i]',
        '[alt*="envelope" i]',
        '[data-icon*="mail" i]',
        '[data-icon*="envelope" i]',
        '[data-testid*="mail" i]',
        '[data-testid*="envelope" i]',
        '[class*="mail"]',
        '[class*="envelope"]',
        'svg[aria-label*="mail" i]',
        'svg[aria-label*="email" i]',
      ].join(','),
    );

    const count = await iconLocator.count().catch(() => 0);
    for (let index = 0; index < count; index += 1) {
      if (await iconLocator.nth(index).isVisible().catch(() => false)) {
        return true;
      }
    }

    const html = await previewRoot.innerHTML().catch(() => '');
    return /\benvelope\b|\bicon-mail\b|\bicon-email\b/i.test(html);
  }

  async observeNoEmailIconDuringWindow(durationMs, intervalMs) {
    const samples = [];
    const startedAt = Date.now();

    while (Date.now() - startedAt <= durationMs) {
      const snapshot = await this.previewSnapshot();
      samples.push({
        offsetMs: Date.now() - startedAt,
        emailIconVisible: snapshot.emailIconVisible,
        text: snapshot.text,
      });
      if (snapshot.emailIconVisible) {
        return { passed: false, samples };
      }
      await delay(intervalMs);
    }

    return { passed: true, samples };
  }

  async openNewsletterFromList() {
    if (this.options.newsletterName) {
      const namedItem = await this.firstUsable([
        this.page.getByRole('row', {
          name: new RegExp(this.escapeForRegex(this.options.newsletterName), 'i'),
        }),
        this.page.getByRole('link', {
          name: new RegExp(this.escapeForRegex(this.options.newsletterName), 'i'),
        }),
        this.page.getByText(new RegExp(this.escapeForRegex(this.options.newsletterName), 'i')),
      ]);

      if (namedItem) {
        const editAction = await this.firstUsable([
          namedItem.getByRole('link', { name: /edit|open/i }),
          namedItem.getByRole('button', { name: /edit|open/i }),
          namedItem,
        ]);
        if (editAction) {
          await editAction.click();
          await this.page.waitForLoadState('networkidle').catch(() => {});
          return true;
        }
      }
    }

    const firstAction = await this.firstUsable([
      this.page.getByRole('link', { name: /edit|open/i }).first(),
      this.page.getByRole('button', { name: /edit|open/i }).first(),
      this.page.locator('a[href*="newsletter"]').first(),
      this.page.locator('a[href*="edit"]').first(),
      this.page.locator('button').filter({ hasText: /edit|open/i }).first(),
    ]);
    if (!firstAction) {
      return false;
    }

    await firstAction.click();
    await this.page.waitForLoadState('networkidle').catch(() => {});
    return true;
  }

  async firstUsable(locators) {
    for (const locator of locators) {
      if (await this.isUsable(locator)) {
        return locator;
      }
    }
    return null;
  }

  async isUsable(locator) {
    if (!locator) {
      return false;
    }

    try {
      const count = await locator.count();
      if (count < 1) {
        return false;
      }
      return await locator.first().isVisible();
    } catch (error) {
      return false;
    }
  }

  normalizeWhitespace(value) {
    return String(value || '').replace(/\s+/g, ' ').trim();
  }

  escapeForRegex(value) {
    return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }
}

module.exports = { NewsletterEditorPage };
