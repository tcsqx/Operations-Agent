# Contributing to OpsPilot (SuperBizAgent)

Thank you for your interest in contributing to **OpsPilot**! We welcome contributions of all kinds: new diagnostic tools, bug fixes, documentation improvements, security enhancements, and feature requests.

---

## 1. Code of Conduct

We are committed to providing a welcoming, inclusive, and harassment-free environment for all contributors. Please treat fellow community members with respect and courtesy.

---

## 2. Getting Started & Development Setup

### Prerequisites
- **Java**: OpenJDK 17 or higher
- **Maven**: 3.8.0 or higher
- **Docker & Docker Compose** (Optional, for running Prometheus / Alertmanager stack)
- **Git**

### Clone and Build
```bash
git clone https://github.com/your-org/opspilot.git
cd opspilot

# Run full test suite (31 automated tests)
mvn clean test

# Package release JAR
mvn clean package -DskipTests
```

---

## 3. Git Workflow & Commit Conventions

We strictly follow the **Conventional Commits 1.0.0** specification:

```
<type>(<scope>): <short description>
```

### Supported Types:
- `feat`: A new feature or capability (e.g., a new probe tool, engine algorithm)
- `fix`: A bug fix
- `docs`: Documentation only changes
- `test`: Adding or correcting unit/integration tests
- `refactor`: Code changes that neither fix a bug nor add a feature
- `ci`: Changes to CI/CD workflows and Docker configurations
- `chore`: Dependency updates, tooling, or build configuration

### Branching Strategy
- Fork the repository and create a feature branch off `main`:
  `git checkout -b feat/my-new-probe-tool`
- Keep commits focused and atomic.

---

## 4. Coding Standards

1. **Safety First**:
   - Never implement shell tools that execute unfiltered arbitrary user input.
   - All tool commands must pass through `CommandGuard` validation.
   - Mutating operations (e.g., restarts, terminations) must specify `RiskLevel.HIGH` to trigger Human-In-The-Loop (HITL) approval.
2. **Zero Plaintext Secrets**:
   - Never commit API keys, cloud provider access keys, passwords, or private certificates.
   - Use `DataMasker` for logs and telemetry outputs.
3. **Comprehensive Testing**:
   - Every new probe or service must be covered by JUnit 5 tests.
   - Ensure `mvn test` passes 100% cleanly before opening a Pull Request.

---

## 5. Submitting a Pull Request (PR)

1. Ensure all tests pass: `mvn test`.
2. Push your feature branch to your fork.
3. Open a Pull Request against `main`.
4. Provide a clear description of:
   - What problem does this PR solve?
   - How was it tested?
   - Are there any breaking changes or new configuration properties?
5. Wait for CI status checks and code review feedback.
