# JavaCLI

🚀 **一个基于 Java 21 开发的、对标 Claude Code 的终端原生 AI 编码智能体 (Agent CLI)。**

JavaCLI 是一个专为开发人员设计的生产级终端 AI 助手。它不仅支持传统的 ReAct 思考循环，还引入了多步任务规划 (Plan-and-Execute)、多智能体协同 (Multi-Agent) 以及代码库级嵌入检索 (RAG)，并完全适配了 MCP (Model Context Protocol) 协议与 Chrome 浏览器自动化操控。

---

## 🌟 核心特性

- 🤖 **双智能体执行引擎**：
  - **ReAct 模式**：流式推理，适合快速问答与单步代码生成。
  - **Plan-and-Execute 模式**：基于有向无环图 (DAG) 依赖进行多步任务拆解与并发执行，支持执行中重新规划。
- 👥 **多智能体协同 (Multi-Agent)**：
  - 采用 **Orchestrator - Worker - Reviewer（编排器-执行者-审查者）** 架构。规划者拆解任务，执行者并发编写，检查者自动进行质量审查与反馈修复。
- 🔌 **Model Context Protocol (MCP) 支持**：
  - 手写 JSON-RPC 2.0 客户端，兼容 stdio 子进程与 Streamable HTTP 双协议。
  - 自动注册 60+ 外部工具，支持 `@mention` 引用外部资源与被动更新通知。
- 🌐 **Chrome 自动化与登录态复用**：
  - 集成 `chrome-devtools-mcp`，支持导航、点击、表单填充与页面快照抓取。
  - 支持 `/browser connect` 运行时无缝切换 shared 调试模式，复用本地 Chrome 登录态，安全访问私有页面。
- 🧠 **分层记忆与上下文管理**：
  - 区分短期会话记忆与基于本地存储的长期事实记忆 (`/save <事实>`)。
  - 上下文预算动态计算，在接近限制时自动采用 Map-Reduce 压缩生成摘要，极大节约 Token。
- 🔍 **代码库级 RAG 检索引擎**：
  - 采用 `JavaParser` 实现基于 AST（抽象语法树）的类与方法级物理分块。
  - 本地 SQLite + Cosine（余弦相似度）语义搜索，构建代码依赖关系拓扑图。
- 🛡️ **主动防御安全机制**：
  - **人工在环 (HITL)**：高危及敏感操作单字符确认 (`[y/n/a/s/m]`)。
  - **路径围栏 (PathGuard)**：强力校验符号链接逃逸与目录穿越 (`..`)。
  - **命令快速拦截 (CommandGuard)**：静态过滤高危 Shell 命令。
  - **操作审计链 (AuditLog)**：每日自动生成结构化 JSONL 审计日志。
- 🖥️ **流式 TUI 交互体验**：
  - 基于 `JLine 4` 打造的命令行交互。
  - 提供行内工具卡片流式折叠 (`Ctrl+O`)、行内 Git Diff 彩色预览、以及底部双行常驻状态栏。

---

## 🛠️ 快速开始

### 1. 环境准备
- **Java 21** 或更高版本
- **Maven 3.6+**
- 至少一个兼容的 LLM API Key（支持智谱 GLM、DeepSeek、StepFun 或 Kimi）

### 2. 配置环境变量
在项目根目录下创建 `.env` 文件（或复制自 `.env.example`）：
```bash
# 默认大模型提供商 (支持 glm, deepseek, step, kimi)
DEFAULT_PROVIDER=deepseek

# 配置 API Key
DEEPSEEK_API_KEY=your_deepseek_api_key_here
GLM_API_KEY=your_glm_api_key_here

# 可选：自定义 OpenAI 兼容接口地址 (例如对接 Ollama 或公司内网网关)
STEP_BASE_URL=https://api.stepfun.com/v1
STEP_API_KEY=your_custom_key_here
STEP_MODEL=step-3.5-flash
```

### 3. 编译打包
使用 Maven 对项目进行编译和打包：
```bash
mvn clean package
```
打包成功后，可执行 JAR 包位于 `target/javacli-1.0-SNAPSHOT.jar`。

### 4. 运行 JavaCLI
```bash
java -jar target/javacli-1.0-SNAPSHOT.jar
```

---

## ⌨️ 常用 CLI 命令

JavaCLI 提供了一套丰富的终端斜杠命令，让您更轻松地控制智能体行为：

| 命令 | 描述 |
| :--- | :--- |
| `/plan <任务>` | 进入 **Plan-and-Execute 模式** 规划并执行特定任务 |
| `/team <任务>` | 进入 **Multi-Agent 协作模式** 运行任务 |
| `/model <名称>` | 运行时切换当前大模型（如 `glm-5.1`、`deepseek`、`step`） |
| `/hitl [on\|off]` | 查看、开启或关闭人工审批确认流 |
| `/browser connect`| 连接本地已开启调试端口的 Chrome 浏览器，复用登录态 |
| `/browser status` | 查看当前浏览器连接状态与 Tab 列表 |
| `/index [路径]` | 构建指定目录的代码库语义索引（RAG 前置） |
| `/search <查询>` | 在代码库中进行自然语言语义代码搜索 |
| `/graph <类名>` | 检索特定类的关系拓扑图谱（继承、实现、调用关系） |
| `/save <事实>` | 保存项目级长期记忆（例如 `/save --global 默认用中文回答`） |
| `/memory` | 查看长期记忆状态，支持 `list`, `search`, `delete`, `clear` 子命令 |
| `/policy` | 查看当前系统安全防御机制与审计状态 |
| `/audit [N]` | 查看最近 N 条危险工具审计记录 |
| `/snapshot` | 查看或管理当前项目的本地 git 快照与回滚点 |
| `/restore <N>` | 将工作区恢复到最近第 N 个 pre-turn 快照状态 |
| `/clear` | 清空当前对话的短期历史记录，不影响长期记忆 |
| `/exit` | 退出 JavaCLI 客户端 |

---

## 📐 架构设计

```
                    ┌──────────────────────────┐
                    │       CLI / TUI          │
                    └────────────┬─────────────┘
                                 │ (User Input / Commands)
                                 ▼
                    ┌──────────────────────────┐
                    │    JavaCLI Agent Core    │
                    └────────────┬─────────────┘
          ┌──────────────────────┼──────────────────────┐
          ▼                      ▼                      ▼
┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐
│   Plan / DAG     │   │      Memory      │   │    Tool Engine   │
│  Orchestration   │   │  Short & Long    │   │  Builtin & MCP   │
└──────────────────┘   └──────────────────┘   └──────────┬───────┘
                                                         │
                                                         ▼
                                              ┌──────────────────┐
                                              │   Safety Guard   │
                                              │   HITL & Policy  │
                                              └──────────────────┘
```

- **com.javacli.agent**：包含 `Agent` 核心、`PlanExecuteAgent` 规划智能体和 `SubAgent` 子代理。
- **com.javacli.mcp**：处理基于 JSON-RPC 2.0 的服务端/客户端通道。
- **com.javacli.rag**：处理 Java 源码 AST 关系提取、嵌入向量计算和本地检索。
- **com.javacli.policy**：负责 `PathGuard` 越界分析、`CommandGuard` 高危拦截与每日 JSONL 审计生成。
- **com.javacli.runtime**：负责 SQLite 后台多任务队列持久化与 localhost HTTP API 监听。

---

## 📄 开源协议

本项目基于 **MIT License** 许可协议开源。
