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

推荐使用根目录下的一键启动脚本（自动配置 UTF-8 编码与 JDK 21+ JLine 原生终端支持）：

```bash
# macOS / Linux
./run.sh

# Windows (CMD / PowerShell)
run.bat
```

也可以直接使用 `java` 运行：
```bash
java --enable-native-access=ALL-UNNAMED -jar target/javacli-1.0-SNAPSHOT.jar
```

#### 命令行启动参数
```bash
javacli -h, --help                 # 显示命令行帮助与用法
javacli -v, --version              # 显示当前产品版本信息
javacli "解释 pom.xml 结构"         # 直接以初始任务启动并进入交互式会话
javacli /plan "重构工具执行器"      # 直接以 Plan-and-Execute 模式规划执行
javacli /team "编写并验证登录模块"   # 直接以 Multi-Agent 协作模式执行
```

---

## ⌨️ 常用 CLI 命令

JavaCLI 提供了一套完整的终端斜杠命令与快捷键系统，支持 `Tab` 智能补全与实时语法高亮：

| 分组 | 命令 | 描述 |
| :--- | :--- | :--- |
| **🎯 执行模式** | `/plan [任务]` | 进入 **Plan-and-Execute 模式** 进行有向无环图 (DAG) 多步任务规划与执行 |
| | `/team [任务]` | 进入 **Multi-Agent 协作模式**（编排器-执行者-审查者自动分工与复审） |
| | `/hitl [on\|off]` | 查看、开启或关闭危险操作单字符人工审批确认流 (`[y/n/a/s/m]`) |
| **🧠 上下文与记忆** | `/context` (或 `/ctx`) | 查看当前上下文 Token、缓存占比与预算状态 |
| | `/memory` (或 `/mem`) | 查看跨会话记忆状态，支持 `list`、`search <词>`、`delete <id>`、`clear` |
| | `/save [--global] <事实>` | 手动持久化项目级（默认）或跨项目全局的长期事实记忆 |
| | `/clear` | 清空当前对话的短期历史记录（长期记忆保持不变） |
| | `/history clear` | 清空本地终端历史输入记录 |
| **🔍 代码检索与探索** | `/index [路径]` | 构建指定目录代码库的 AST 关系拓扑与语义向量索引（RAG 前置） |
| | `/search <查询>` | 基于自然语言语义在代码库中进行混合代码检索（AST + 向量） |
| | `/graph <类名>` | 检索特定类的依赖拓扑图谱（包含 `extends`、`implements`、`calls` 等关系） |
| **🛡️ 安全与快照** | `/snapshot [status\|clean]` | 查看或清理当前项目的 Side-Git 本地快照点 |
| | `/restore <N>` | 将工作区恢复到最近第 N 个 pre-turn 快照状态 |
| | `/policy` | 查看当前路径沙箱（PathGuard）与系统安全防护策略 |
| | `/audit [N]` | 查看最近 N 条危险工具执行审计记录（取自每日结构化 JSONL 审计链） |
| **🔌 生态与模型配置** | `/model` (或 `/models`) | OpenCode 风格可搜索模型弹窗与管理（支持 Type-to-Search 拼写过滤、`[active]` 状态徽章、`provider/model` 规范、`/model show` 概况、`/model key [provider] <key>` 设置密钥、`/model url [provider] <url>` 设置端点、`/model config` 多参数配置与快捷切换） |
| | `/mcp` | 查看 MCP 服务端状态与工具清单（支持 `restart` / `logs` / `disable` / `enable` / `resources` / `prompts`） |
| | `/skill` | 管理智能体技能（支持 `list` / `show <name>` / `on <name>` / `off <name>` / `reload`） |
| | `/browser` | 管理 Chrome DevTools 浏览器会话（支持 `status` / `connect` / `tabs` / `disconnect`） |
| | `/task` | 管理持久化异步后台任务（支持 `list` / `add <任务>` / `cancel <id>` / `log <id>`） |
| | `/config` | 打开配置交互面板（只读视图与配置切换快捷指引） |
| **🚪 快捷命令** | `/help` (或 `/?`) | 查看可用命令分组指引与详细说明 |
| | `/exit` (或 `/quit`) | 退出程序（终端内亦支持连按两次 `Ctrl+C` 退出） |

> 💡 **交互小贴士**：
> - 键入以 `/` 起始的命令时，底部原地呈现 OpenCode / Claude Code 风格的建议卡片（高度恒定无滚屏跳跃），支持 `↑` / `↓` 切换选中项、`Tab` 自动补全、`Enter` 执行选中命令；
> - 任务输出过程中，可按 `Ctrl+O` 展开或收起上一个折叠工具调用块；
> - 正在运行任务时，按 `Esc` 即可取消当前流式执行；
> - 输入 `@` 可联想补全本地文件路径（自动读取内容）或 MCP Resource 资源；输入 `@image:` 可引入图片路径。

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
