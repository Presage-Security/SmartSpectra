# Security Policy

Presage Technologies takes the security of the SmartSpectra SDK seriously. We appreciate the
efforts of security researchers and the wider community in helping us keep our users
safe, and we are committed to working with you to verify and address any vulnerabilities
you report.

## Supported Versions

Security fixes are issued for the latest `3.x` release line. We recommend always
updating to the most recent released version of each platform SDK.

| Version | Supported          |
| ------- | ------------------ |
| 3.x     | :white_check_mark: |
| < 3.0   | :x:                |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues, pull
requests, or discussions.**

Instead, report them privately through GitHub's coordinated disclosure feature:

1. Go to the [Security tab](https://github.com/Presage-Security/SmartSpectra/security)
   of this repository.
2. Click **Report a vulnerability** to open a private security advisory.
3. Provide the details described below.

If you are unable to use GitHub private advisories, you may email
<support@presagetech.com> with the subject line `SECURITY` instead.

To help us triage and prioritize, please include as much of the following as you can:

- The type of issue (e.g. memory corruption, injection, authentication bypass,
  sensitive-data exposure).
- The affected platform(s) and SDK version(s) (Android, iOS, or C++ for
  Windows/macOS/Linux).
- Full paths of the source file(s) related to the issue.
- Step-by-step instructions to reproduce the issue, including any proof-of-concept
  code, configuration, or sample input required.
- The impact of the issue and how an attacker might exploit it.

## What to Expect

- **Acknowledgement:** We will acknowledge receipt of your report within 3 business days.
- **Updates:** We will keep you informed of our progress as we investigate and validate
  the issue.
- **Disclosure:** We follow a coordinated disclosure process. We ask that you give us a
  reasonable amount of time to release a fix before publicly disclosing the
  vulnerability, and we will coordinate the timing of any public disclosure with you.

Please make a good-faith effort to avoid privacy violations, data destruction, and
service disruption while investigating. Only interact with accounts you own or have
explicit permission to test.

## Scope

This policy covers the SmartSpectra SDK source distributed in this repository. The SDK
processes camera input to measure physiological signals; reports relating to the
handling, transmission, or storage of that data are especially welcome.

Thank you for helping keep SmartSpectra and its users secure.
