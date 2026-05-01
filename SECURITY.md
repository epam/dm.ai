# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| Latest (`main`) | ✅ |
| Older releases | ❌ — please upgrade |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub Issues.**

To report a security issue responsibly:

1. Open a [GitHub Security Advisory](https://github.com/IstiN/dmtools-cli/security/advisories/new) (preferred — keeps the report private until patched).
2. Alternatively, email the maintainers directly via the contact listed on the [GitHub profile](https://github.com/IstiN).

Please include:
- A description of the vulnerability and its potential impact
- Steps to reproduce or a proof-of-concept (if safe to share)
- Affected versions or files
- Any suggested fix (optional but appreciated)

## What to Expect

- **Acknowledgement** within 5 business days.
- **Status update** (confirmed / not reproducible / out of scope) within 14 days.
- **Patch and public disclosure** coordinated with the reporter, typically within 90 days of confirmation.

We follow the [GitHub coordinated disclosure guidelines](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/about-coordinated-disclosure-of-security-vulnerabilities).

## Security Best Practices for Users

- Never commit real credentials to your repository. Use `dmtools.env` (already in `.gitignore`).
- Rotate any credentials that may have been accidentally exposed immediately.
- Run DMTools with the minimum required permissions for each integration.
- Keep your DMTools installation up to date: `curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash`
