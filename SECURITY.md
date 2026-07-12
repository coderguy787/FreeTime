# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in FreeTime, please report it by emailing the project maintainers. Do not create a public GitHub issue.

## What to Include

- A description of the vulnerability
- Steps to reproduce
- Potential impact
- Any suggested fixes or mitigations

## What to Expect

You will receive an acknowledgment within 48 hours, and a detailed response within 5 business days. We will keep you informed of progress toward a fix.

## Responsible Disclosure

Please allow us reasonable time to address the issue before any public disclosure. We will coordinate disclosure timelines with you.

## Supported Versions

| Version | Supported |
|---------|-----------|
| v1.0.0  | ✅ |

## Security Measures

- All messages use AES-256-GCM encryption
- Media files are encrypted with per-file keys
- Authentication uses JWT tokens with TOTP 2FA
- Network communication should use TLS in production
- Hardware-backed keystore for encryption keys on Android
