# ADR-0019 Agent 中文源文件编码损坏修复

**日期**: 2026-08-04
**状态**: 已确认
**关联 ticket**: 13（Agent 预问诊与处方解读）

## 背景

ticket 13 的 commit `b0445af`（"feat(agent): ticket 13 Agent 预问诊与处方解读"）在写入含中文的 Python/JSON 源文件时发生编码损坏，导致两处致命缺陷：

### 缺陷 1：`agent/app/intents.py` 整体编码损坏

- 129 行中 **37 行** UTF-8 解码失败，中文全部变成乱码（如 `鎰忓浘` 应为 `意图`）。
- line 21 末尾中文问号 `？`（`\xef\xbc\x9f`）被截断为 `\xef\xbc`，引号丢失，触发 `SyntaxError: unterminated string literal`。
- 模块无法 import -> 意图路由失效 -> **所有意图节点（triage/registration/consultation/pharmacy/reminder/general）运行时全部加载失败**，不仅是 ticket 13，ticket 09/11/12 的运行时一并瘫。
- 验证：`b0445af^`（cbb5c19）的同文件编译通过、UTF-8 合法；损坏由 `b0445af` 引入。

### 缺陷 2：`agent/tools/tools.json` 被添加 UTF-8 BOM

- 文件首字节从 `7b`（`{`）变成 `efbbbf7b`（BOM + `{`）。
- Python `json.load` 默认用 utf-8 解码，拒绝带 BOM 的 JSON，抛 `Unexpected UTF-8 BOM (decode using utf-8-sig)`。
- tools.json 是 Agent 工具契约单一来源（ADR-0015），加载失败 -> **全部工具契约测试 + 依赖工具解析的意图测试崩溃**。
- 验证：`b0445af^` 的同文件无 BOM；BOM 由 `b0445af` 引入。

### 连带缺陷：行为变更未同步旧测试

ticket 13 合理扩展了行为，但未同步更新 ticket 09 写的骨架测试：

- `tools.json` 新增 `check_allergy`（第 12 个工具），`test_tools_contract.py` 仍断言 `EXPECTED_TOOL_COUNT == 11`。
- `consultation` 意图接入真实编排（`build_consultation_node`），`test_intent_router.py` 仍断言它返回 `MOCK_REPLIES` mock 原文。
- `consultation.py` 过敏警告文案写的是"联系您的医生"，测试断言"联系医生"（验收第 7 条原文"建议联系医生"），二者不匹配。

## 决策

### 修复 1：intents.py 回退基底 + 干净补丁（方案 A）

不就地还原 37 行乱码（工作量大、易错、无法保证语义一致），改为：

1. 从 `b0445af^`（cbb5c19）取出未损坏的 `intents.py` 作为基底（ticket 12 末态：triage/registration 已接入真实编排，consultation 走 mock）。
2. 用 Edit 在干净基底上补回 ticket 13 真正需要加的逻辑：`build_intent_node` 内的 consultation 分支（5 行，接入 `build_consultation_node(llm)`），编码正确、风格对齐 triage/registration 分支。

**排除的方案**：
- 就地逐行还原 37 行乱码：工作量与正确性都不可控。
- 整文件回退到 cbb5c19 不补 consultation：等于回退 ticket 13 的入口接入，不可取。

### 修复 2：tools.json 去 BOM，保留全部内容

读原始字节、剥掉前 3 字节 BOM、原样写回。12 个工具（含 ticket 13 新增的 `check_allergy`）全部保留，内容不动。

### 修复 3：旧测试对齐 ticket 13 新行为

- `test_tools_contract.py`：`EXPECTED_TOOL_COUNT` 11->12，测试名 `test_tool_count_is_11`->`test_tool_count_is_12`，文件头注释同步。
- `test_intent_router.py`：consultation 加真实编排特判分支，断言 `reply` 存在且 `!= MOCK_REPLIES["consultation"]`（无 LLM 时降级为框架回复，非 mock 原文）；不照搬 registration 的 `tool_calls` 断言（consultation 在 `llm is None` 时只返回 reply）。
- `consultation.py:176`：过敏警告文案"联系您的医生"->"联系医生"，对齐验收第 7 条语义与测试断言。

### 完成 口径

ticket 的 `Status: done` 必须以**本机全量测试绿**为准（用户决策 A），不接受"代码写完即 done"。本次修复后 `python -m pytest tests/` 全绿（79 passed）方算完成。

## 验收标准

- [x] `app/intents.py` 编译通过、UTF-8 合法、consultation 分支接入 `build_consultation_node`
- [x] `tools/tools.json` 无 BOM、12 个工具、`json.load` 解析成功
- [x] `consultation.py` 过敏警告含连续"联系医生"四字
- [x] `test_tools_contract.py` 断言工具数为 12
- [x] `test_intent_router.py` consultation 走真实编排特判
- [x] `python -m pytest tests/` 全绿（79 passed, 0 failed）

## 约束

- 修复只动 ticket 13 引入的损坏与遗漏，不重构既有结构。
- 含中文的 Python/JSON 源文件提交前需确认：无 BOM、UTF-8 合法、`py_compile` 通过。

### pre-commit 钩子（已落地）

为防止 b0445af 类编码损坏再次混入，已部署 git pre-commit 钩子：

- **校验脚本** `scripts/check-encoding.py`：对暂存区 `.py`/`.json` 检查无 BOM + UTF-8 合法 + `py_compile`/`json.load` 通过；排除 `.venv`/`node_modules`/`.git`/`__pycache__`/`.pytest_cache`。
- **hook 入口** `.githooks/pre-commit`：薄包装，调用上述脚本。
- **激活方式** `git config core.hooksPath .githooks`（已设置，仓库本地配置）。
- **自测**：构造 BOM json + 字节截断 py 均被拦截（commit exit 1）；修复后放行（commit exit 0）。
- **使用约定**：新克隆仓库需执行 `git config core.hooksPath .githooks` 激活（不自动生效是 git 的限制）；可用 `python scripts/check-encoding.py --paths <文件>` 手动校验。
