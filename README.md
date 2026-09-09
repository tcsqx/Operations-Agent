<div align="center">

#  OpsPilot (SuperBizAgent 2.0)

### Enterprise AI SRE & DevOps Incident Copilot
**面向生产环境的企业级 AI SRE 智能运维中枢：自动化告警分析、真实探针采样、根因推断与人机协同审批**

[![Java](https://img.shields.io/badge/Java-17-orange.svg?style=flat-square&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.4-brightgreen.svg?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.0-blue.svg?style=flat-square)](https://spring.io/projects/spring-ai)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square)](LICENSE)
[![Tests](https://img.shields.io/badge/Tests-31%2F31%20Passing-success.svg?style=flat-square)]()
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg?style=flat-square&logo=docker)](Dockerfile)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square)](CONTRIBUTING.md)

[English](#-overview) | [中文说明](#-项目简介) | [快速开始](#-quick-start) | [架构设计](#-architecture) | [API 文档](#-api-reference)

</div>

---

## 📖 项目简介 (Overview)

**OpsPilot 2.0**（前身为 SuperBizAgent）是一个面向生产级 SRE / DevOps 场景设计的开源 AI 智能运维系统。

传统的运维聊天机器人往往停留在“文字问答”或“Mock 模拟返回”，而 OpsPilot 具备**真实运维能力与严密安全边界**：
- 能够接入 **Prometheus / Alertmanager** 监控告警；
- 自主调度**真实底层探针**探测主机 CPU、内存、磁盘分区、Top 进程与 TCP 端口；
- 独创 **CommandGuard 安全沙箱** 熔断任何高危破坏性指令；
- 支持 **Human-In-The-Loop（HITL）人工授权审批流**，高危变更由人把关；
- 基于事实证据生成带置信度打分的**根因假设树与标准 SRE Incident 诊断报告**。

---

## ⚡ 特性对比 (Feature Comparison)

| 核心维度 | 原 SuperBizAgent 1.0 (Demo) | 顶级开源项目 (HolmesGPT / K8sGPT) | **OpsPilot 2.0 (本项目)** |
| :--- | :--- | :--- | :--- |
| **定位场景** | 基础个人聊天机器人 Demo | 专业生产级 CLI 诊断工具 | **企业级 Web / API 双模 SRE 运维中枢** |
| **探针数据** | 仅 4 个静态/Mock 模拟工具 | K8s API、Prometheus 实时拉取 | **9 大真实底层探针（硬件/JVM/系统/网络）** |
| **执行沙箱** | 无命令执行能力，无安全防线 | 受限于外部容器环境 | **内置 CommandGuard 熔断 + 15s 超时防僵死** |
| **敏感治理** | 无脱敏，明文传递 | 依赖用户自行过滤 | **自动抹除密码、Bearer Token、RSA 私钥** |
| **人机协同** | ❌ 无，无法挂起任务 | ❌ 无，通常为只读分析 | **✅ 完整 HITL 审批流（工单挂起/恢复/拒绝）** |
| **推理决策** | 简单单步 LLM Chat | 基于规则与提示词链 | **事实抽取 -> 假设打分 (0~100%) -> SRE 报告** |
| **告警联动** | ❌ 无原生告警接入能力 | 支持 Alertmanager 轮询 | **原生 Webhook + 5分钟滑动窗口防重压制** |
| **工程质量** | 无单元测试，缺乏持久化 | Python 测试覆盖 | **31 个自动化测试用例，100% 通过** |

---

## 🏗️ 架构设计 (Architecture)

```
                       +-----------------------------------+
                       |    Prometheus / Alertmanager      |
                       +-----------------+-----------------+
                                         | Webhook Post
                                         v
   +-----------------------------------------------------------------------+
   |                            OpsPilot 2.0                               |
   |                                                                       |
   |   [ API Gateway & Ingress ]                                           |
   |   ├── REST API v2 (/api/v2/task/*)                                    |
   |   ├── SSE Stream Controller                                           |
   |   ├── Alertmanager Webhook (with 5-min AlertDeduplicator)             |
   |   └── OpsPilot SRE Control Center (Interactive Web UI)                |
   |                                                                       |
   |   [ Orchestration & Engine ]                                          |
   |   ├── TaskStateMachine (CREATED -> PLANNING -> RUNNING -> DIAGNOSIS)  |
   |   ├── HitlApprovalManager (Human-in-the-loop tickets & auto-expire)   |
   |   └── OpsPilotAgentEngine (Autonomous multi-step planner)             |
   |                                                                       |
   |   [ Security Sandbox & Governance ]                                   |
   |   ├── CommandGuard (Regex blacklist: rm -rf, mkfs, dd, fork bomb)     |
   |   ├── RiskClassifier (4-tier rating: LOW / MEDIUM / HIGH / CRITICAL)  |
   |   ├── DataMasker (Regex redaction: passwords, tokens, private keys)   |
   |   └── AuditLogger (Tamper-evident DB persistent audit trail)          |
   |                                                                       |
   |   [ Ground-Truth Telemetry Probes ]                                   |
   |   ├── server_info        ├── cpu_inspector     ├── memory_inspector   |
   |   ├── disk_usage         ├── process_top       ├── port_check         |
   |   ├── service_status     ├── system_log_search └── safe_shell         |
   |                                                                       |
   |   [ Reasoning & Root Cause Analysis ]                                 |
   |   ├── Evidence Collector (Structured facts & anomaly detection)       |
   |   ├── HypothesisEngine (Diagnostic rule scoring & pruning)            |
   |   └── ReportBuilder (Standard SRE Markdown Incident Reports)          |
   |                                                                       |
   |   [ Persistence Layer ]                                               |
   |   └── Spring Data JPA + H2 In-Memory (Zero-external DB dependency)   |
   +-----------------------------------------------------------------------+
```

---

## 🛠️ 核心模块与组件

### 1. 🛡️ 安全沙箱与合规治理
- **`CommandGuard`**：前置拦截危险破坏性指令（`rm -rf /`、`mkfs.*`、`dd of=/dev/sd*`、`shutdown`、`:(){ :|:& };:` 等）。
- **`RiskClassifier`**：四级风险分类模型，动态标记只读探针（LOW）、排查命令（MEDIUM）、服务重启/终止（HIGH）与破坏性操作（CRITICAL）。
- **`DataMasker`**：自动识别并脱敏输出中的密钥凭证（`password=***`、`Bearer ***REDACTED***`、`-----BEGIN PRIVATE KEY-----`）。
- **`AuditLogger`**：全量持久化操作审计日志，包含操作人、命令内容、时间戳与 IP 地址。

### 2. 🔌 9 大真实底层运维探针
- `server_info`：主机操作系统架构、可用物理 CPU 核数、JVM 运行时间与系统均载。
- `cpu_inspector`：JVM 进程及主机 CPU 占用百分比，自动标记高载警报。
- `memory_inspector`：物理内存总容量、已用容量、可用容量与 JVM 堆水位。
- `disk_usage`：扫描全量挂载存储分区，计算可用百分比并对 >90% 分区预警。
- `process_top`：跨平台查询当前系统资源消耗排名前列的活跃进程。
- `port_check`：低层 TCP 网络套接字探测，测量连接握手延迟毫秒数。
- `service_status`：检查目标服务或系统守护进程存活状态。
- `system_log_search`：倒序扫描最新日志文件，精准检索异常堆栈。
- `safe_shell`：安全受限的 Shell 命令沙箱，15秒强制超时防挂死，64KB 输出截断保护。

### 3. 🧠 假设推理与 SRE 报告引擎
- **事实证据化（Evidence）**：探针采集结果自动转换为包含 `isAnomaly` 状态的证据结构。
- **根因推断（HypothesisEngine）**：匹配 SRE 知识规则库，推断并打分（如内存泄漏置信度 95%、磁盘打满置信度 90%）。
- **标准报告（ReportBuilder）**：自动合成涵盖事件背景、事实度量、根因分析、排障修复建议与操作轨迹的 Markdown 报告。

### 4. 🤝 人机协同审批流（Human-In-The-Loop）
- 当 Agent 规划中涉及 `RiskLevel.HIGH` 的变更（如重启服务、修改配置、强制杀进程）时，任务自动挂起为 `WAITING_APPROVAL`。
- 生成唯一审批单（带10分钟超时时效），运维人员可通过 Web 控制台或 API 进行确认授权（Approve）或拒绝终止（Reject）。

---

## 🚀 快速开始 (Quick Start)

### 选项 A：本地编译运行 (JAR Mode)

**环境要求**：JDK 17+, Maven 3.8+

```bash
# 1. 克隆代码库
git clone https://github.com/your-org/opspilot.git
cd opspilot

# 2. 运行自动化测试套件
mvn clean test

# 3. 本地启动服务
mvn spring-boot:run
```

启动完成后，在浏览器中打开：**`http://localhost:9900`**，即可使用智能对话及 OpsPilot SRE 控制中心！

---

### 选项 B：Docker Compose 一键部署监控集群

项目自带完整的容器化编排环境，一键拉起 **OpsPilot + Prometheus + Alertmanager**：

```bash
# 启动完整 SRE 智能诊断集群
docker-compose up -d --build

# 查看运行状态
docker-compose ps

# 查看 OpsPilot 实时诊断日志
docker-compose logs -f opspilot
```

服务端口映射：
- **OpsPilot 控制台**: `http://localhost:9900`
- **Prometheus 监控**: `http://localhost:9090`
- **Alertmanager 告警**: `http://localhost:9093`

---

## 📡 API 参考手册 (API Reference)

### 1. 创建并触发自动诊断任务
```http
POST /api/v2/task/create
Content-Type: application/json

{
  "prompt": "服务器负载异常偏高，8080端口偶发无法访问，请排查",
  "intent": "AUTO_DIAGNOSIS",
  "autoExecute": true
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "task-65d1f6ab",
    "status": "CREATED",
    "execution": "ASYNC_TRIGGERED"
  }
}
```

### 2. 查询任务详情与诊断报告
```http
GET /api/v2/task/{taskId}
```

### 3. SSE 流式跟踪任务进展
```http
GET /api/v2/task/stream/{taskId}
Accept: text/event-stream
```

### 4. 人工审批授权 (HITL)
```http
POST /api/v2/task/approve
Content-Type: application/json

{
  "approvalId": "appr-8f3a1b2c",
  "action": "APPROVE",
  "operator": "sre-lead",
  "comment": "已完成业务低峰期确认，允许执行重启"
}
```

### 5. Alertmanager 告警接收 Webhook
```http
POST /api/v2/webhook/alertmanager
Content-Type: application/json

{
  "version": "4",
  "status": "firing",
  "alerts": [
    {
      "status": "firing",
      "labels": {
        "alertname": "HostHighCpuLoad",
        "severity": "warning",
        "instance": "192.168.1.10"
      },
      "annotations": {
        "description": "CPU load exceeds 90% for 3 minutes"
      }
    }
  ]
}
```

---

## 📄 诊断报告产出样例 (Sample SRE Report)

```markdown
# 🚨 OpsPilot SRE Incident Diagnosis Report

**Task ID**: `task-65d1f6ab` | **Status**: `SUCCESS` | **Risk Level**: `LOW` | **Generated At**: 2026-09-09 10:04:42

---

## 1. 📋 Incident Overview
- **Title / Intent**: Ops Task: Auto-Diagnosis
- **Trigger Description**: 诊断当前主机服务器环境、CPU利用率与磁盘分区水位
- **Diagnosis Summary**: **System Healthy / Transient Anomaly** (Confidence: **85%**)

## 2. 🔍 Gathered Telemetry & Evidence
| Source Tool | Status | Observation Details | Time |
| :--- | :---: | :--- | :--- |
| `server_info` | ✅ Normal | Hostname: DESKTOP-OP (192.168.1.5)<br/>OS: Windows 11 (amd64)<br/>Cores: 16 | 2026-09-09 10:04:41 |
| `cpu_inspector` | ✅ Normal | CPU Status: HEALTHY<br/>- Cores: 16<br/>- Host CPU Load: 12.4% | 2026-09-09 10:04:42 |
| `disk_usage` | ✅ Normal | Mount 'C:\': Total 475 GB, Used 192 GB (40.4%)<br/>Mount 'D:\': Total 931 GB, Used 310 GB (33.3%) | 2026-09-09 10:04:42 |

## 3. 🧠 Root Cause Analysis & Ranked Hypotheses
### 1. System Healthy / Transient Anomaly (Confidence: **85%**)
- **Root Cause Mechanism**: All executed telemetry probes reported metrics within nominal thresholds.
- **Recommended Actions / Fixes**:
  - 💡 Continue normal monitoring; inspect upstream traffic or network load balancer if user reports persist.

## 4. ⚙️ Execution Step Trail
| # | Step | Tool | Status | Duration |
| :---: | :--- | :--- | :---: | :---: |
| 1 | Collect Server Host Telemetry | `server_info` | SUCCESS | 28 ms |
| 2 | Inspect CPU Load | `cpu_inspector` | SUCCESS | 1087 ms |
| 3 | Inspect Top Processes | `process_top` | SUCCESS | 656 ms |
| 4 | Inspect Disk Space | `disk_usage` | SUCCESS | 5 ms |
```

---

## 🧪 自动化测试验证 (Testing)

项目拥有全套高质量自动化测试套件（JUnit 5 + Spring Boot Test），无需外部真实依赖即可一键运行通过：

```bash
mvn test
```

```
[INFO] Running org.example.security.CommandGuardTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.example.security.DataMaskerTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.example.security.RiskClassifierTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.example.tools.GroundTruthToolsTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.example.tools.SafeCommandExecutorTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.example.engine.TaskStateMachineTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.example.engine.AlertDeduplicatorTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.example.engine.HitlApprovalFlowTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.example.engine.OpsPilotE2ETest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.example.diagnosis.HypothesisEngineTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.example.diagnosis.ReportBuilderTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS (31/31 Tests Passed, 100% Success)
[INFO] ------------------------------------------------------------------------
```

---

## 🗺️ 未来规划 (Roadmap)

- [x] 核心有限状态机与生命周期模型升级
- [x] 9 大底层系统真实探针工具体系
- [x] CommandGuard 安全沙箱与四级风险过滤
- [x] 敏感凭据/私钥动态脱敏
- [x] Human-In-The-Loop 人机协同审批工单机制
- [x] 基于事实证据的假设推断打分引擎 (HypothesisEngine)
- [x] Alertmanager 原生 Webhook 接入与 5 分钟滑动窗口防重
- [x] 多阶段 Docker 容器化与 Docker Compose 监控栈编排
- [x] GitHub Actions 全自动化 CI/CD 流水线
- [ ] Kubernetes CRD & Cloud-Native Operator 扩展
- [ ] 基于 eBPF 的无侵入内核网络与调用链深层遥测
- [ ] 飞书 / 钉钉 / Slack 智能 OnCall 机器人双向交互

---

## 📄 开源许可证 (License)

本项目遵循 [Apache 2.0 开源许可证](LICENSE)。欢迎自由使用、修改与商业分发。
