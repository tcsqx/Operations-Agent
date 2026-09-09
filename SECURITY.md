# Security Policy

The OpsPilot team takes the security of our software and users seriously. This document outlines how to report security vulnerabilities and our supported versions.

---

## 1. Supported Versions

We release patches and security fixes for the following versions:

| Version | Supported          | Security Fixes |
| ------- | ------------------ | -------------- |
| 2.0.x   | :white_check_mark: | Active         |
| 1.0.x   | :x:                | End of Life    |

---

## 2. Reporting a Vulnerability

If you discover a security vulnerability in OpsPilot, please **DO NOT** create a public GitHub issue. Instead, follow these responsible disclosure practices:

1. **Email Contact**: Send an email to `security@opspilot.org` (or directly contact project maintainers).
2. **Details to Include**:
   - Vulnerability type and affected component (e.g., CommandGuard bypass, SSRF, Deserialization, SQLi).
   - Step-by-step reproduction guide with minimal payload.
   - Potential impact and affected system configurations.
3. **Response SLA**:
   - Initial acknowledgement: within **24 hours**.
   - Vulnerability triage and assessment: within **72 hours**.
   - Remediation and patch release: within **7 to 14 days** depending on severity.

---

## 3. Built-in Security Architecture

OpsPilot is architected with a defense-in-depth security model:
- **`CommandGuard`**: Prevents execution of destructive shell patterns.
- **`RiskClassifier`**: Identifies mutating system actions and tags them as `HIGH` / `CRITICAL`.
- **`HitlApprovalManager`**: Enforces human authorization before executing mutating actions.
- **`DataMasker`**: Automatically masks passwords, Bearer tokens, private keys, and cloud credentials before outputting logs or telemetry.
- **Audit Trails**: Every executed tool action is permanently recorded in `ops_audit_logs`.
