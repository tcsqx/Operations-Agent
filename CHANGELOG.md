# Changelog

All notable changes to **OpsPilot (SuperBizAgent)** are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.0.0] - 2026-09-09

### 🚀 Major Transformation: From Personal Demo to Enterprise AI SRE Copilot

This release transforms the legacy prototype into **OpsPilot 2.0**, an enterprise-grade AI SRE Agent with ground-truth system telemetry, safety sandboxing, evidence-based hypothesis reasoning, and human-in-the-loop governance.

### ✨ Added
- **Safety Sandbox & Governance**:
  - `CommandGuard`: Multi-pattern regex firewall blocking destructive commands (`rm -rf`, `mkfs`, `dd`, `shutdown`, `:(){ :|:& };:`, etc.).
  - `RiskClassifier`: 4-tier risk classification engine (LOW, MEDIUM, HIGH, CRITICAL).
  - `DataMasker`: Automated masking for passwords, Bearer tokens, AKSK credentials, and RSA private keys.
  - `AuditLogger`: Tamper-evident structured audit logging service backed by database persistence.
- **Ground-Truth Telemetry Probes**:
  - `server_info`: Real-time host OS, JVM uptime, CPU cores, load average, and architecture discovery.
  - `cpu_inspector`: Deep CPU utilization and thread load metrics via JVM MXBean.
  - `memory_inspector`: Physical RAM and JVM heap memory breakdown with high-watermark alert detection.
  - `disk_usage`: Comprehensive multi-mount storage inspection and >90% capacity warnings.
  - `process_top`: Real-time top resource-consuming process inspection across Windows and Linux.
  - `port_check`: Low-level TCP connectivity latency and status validation.
  - `service_status`: OS service and process state verification.
  - `system_log_search`: Reverse log scanner with keyword search and regex matching.
  - `safe_shell`: Sandboxed command executor with 15-second timeout and 64KB output truncation.
- **Decision & Diagnosis Engine**:
  - `TaskStateMachine`: Explicit finite state machine enforcing legal transitions (`CREATED` -> `PLANNING` -> `RUNNING` -> `WAITING_APPROVAL` -> `DIAGNOSING` -> `SUCCESS` / `FAILED`).
  - `HitlApprovalManager`: Human-in-the-loop approval tickets for high-risk mutating operations with 10-minute expiry.
  - `HypothesisEngine`: Automated root cause analysis (RCA) evaluating gathered evidence to generate confidence-ranked diagnostic hypotheses.
  - `ReportBuilder`: Automated Markdown SRE Incident Diagnosis Report generation.
  - `AlertDeduplicator`: 5-minute sliding window alert deduplication gate suppressing alert storms.
- **APIs & Web Console**:
  - REST API v2 endpoints (`/api/v2/task/create`, `/stream`, `/execute`, `/approve`, `/approvals/pending`, `/tools`).
  - Native Alertmanager Webhook endpoint (`/api/v2/webhook/alertmanager`).
  - Integrated **OpsPilot SRE Control Center** modal with Auto-Diagnosis, Tool Console, HITL Approvals, and Audit Flow.
- **Testing & Quality Assurance**:
  - 31 automated test cases covering unit tests, probe integration tests, and full E2E lifecycle tests with 100% pass rate.
- **Containerization & CI/CD**:
  - Multi-stage `Dockerfile` with minimal Alpine JRE runner and non-root security.
  - `docker-compose.yml` integrating OpsPilot, Prometheus, and Alertmanager.
  - GitHub Actions CI workflow (`.github/workflows/ci.yml`) with automated testing, packaging, and secret leak scanning.

### 🔄 Changed
- Replaced in-memory only state with JPA entities backed by H2 in-memory database.
- Enhanced Milvus and MCP integrations to be fully optional with graceful offline fallback.
- Updated Maven dependencies to use high-availability mirrors.

---

## [1.0.0] - 2026-01-02

### Initial Baseline Prototype
- Prototype chat assistant with basic DashScope LLM chat.
- Naive Milvus RAG embedding demo.
- 4 basic mock tools (`DateTimeTools`, `InternalDocsTools`, `QueryMetricsTools`, `QueryLogsTools`).
