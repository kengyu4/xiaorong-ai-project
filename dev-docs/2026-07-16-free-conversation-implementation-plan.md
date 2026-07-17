# 自由对话与薄弱点概览实施计划

> 日期：2026-07-16  
> 依据：`2026-07-16-free-conversation-design.md`、`2026-07-16-visible-ai-integration-design.md`  
> 状态：4.1、4.2、4.4、4.5、4.6 已完成；4.3 暂缓

## 阶段 1：回归测试与领域结构

- [x] 为个人 Secret 存在但偏好缺失时的可信 Provider/default model 回退补测试。
- [x] 为自由提问最近 3 轮上下文、1000 token 上限和安全运行元数据补测试。
- [x] 为 Token 预算截断、告警和硬降级补测试。
- [x] 为低分异步讲评状态机与用户隔离补测试。
- [x] 为个性化建议、数据指纹缓存、Mock/JDBC 一致性补测试。
- [ ] 面试官完整追问页面和业务验收（按用户要求暂缓；不计入本次完成范围）。

## 阶段 2：后端实现

- [x] 扩展 Study DTO，返回 `providerCode`、`model`、`degraded`，并过滤 Secret 类元数据。
- [x] 将场景 Token 上限调整为 1000/800/600/700，默认上限调整为 600。
- [x] 实现最近 3 轮的服务端有界上下文；自由提问 Prompt 约束调整为 600 字。
- [x] 实现异步深度讲评及安全降级。
- [x] 新增 `GET /api/study/overview/advice`，按用户与学习数据指纹缓存 2～3 条建议。
- [x] 复盘 `nextActions` 使用 AI 建议解析结果，平均分排除 `free_ask`。
- [x] Mock 与 MySQL/Redis StudyService 保持 advice、review 和运行元数据一致。
- [x] 修复个人 Secret 已保存但偏好缺失时静默进入 Mock 的路由问题。

## 阶段 3：前端实现

- [x] `/admin/runtime` 保存 Secret 后同步保存当前 Provider/模型偏好，保存成功清空 Key。
- [x] 支持可信 Provider 切换、上游模型列表和手工模型名，不回显明文 Secret。
- [x] 所有课堂节点默认显示 AskBox，并按节点展示多轮问题和回答。
- [x] POST SSE 展示连接状态，必须收到 `done` 且有正文；失败时回退普通 POST。
- [x] 低分深度讲评轮询最长 60 秒，并提供失败/超时重试。
- [x] 首页展示当前用户真实薄弱点和 2～3 条建议；复盘页展示 AI `nextActions`。
- [x] 页面展示“真实 AI · Provider · 模型”或“演示/降级回复”。

## 阶段 4：验证与文档

- [x] 后端执行 `mvn -q test` 和 `mvn -q -DskipTests package`。
- [x] 前端执行 `npm test`（11/11）、`npm run type-check`、`npm run build`。
- [x] 使用最新代码重建 Docker `app/frontend`，应用和依赖容器正常启动。
- [x] 8088 回归：根页面 200；未登录 runtime/courses API 返回 401，不再返回 502。
- [x] 检查修改源码无 UTF-8 BOM、无 连续问号乱码，无浏览器明文 Key 持久化。
- [x] 更新 `待做任务清单.md`、`角色完整设计.md`、项目 README 和文档索引。

## 完成边界

本次只把 4.1、4.2、4.4、4.5、4.6 计为完成。4.3 的面试官页面、交互和完整业务未实施，文档中统一标记为 ⚪ 暂缓；仅保留已有 Controller/服务基础和 600 token 上限，不以此声明面试官功能完成。

## Follow-up refinement: real upstream SSE

Completed:

1. Added callback-oriented streaming contracts across the study service, dynamic gateway, provider routing gateway, and provider adapter.
2. Implemented OpenAI-compatible upstream SSE parsing with `stream: true`; each valid delta is forwarded as it arrives.
3. Added regression coverage for incremental forwarding without the synchronous `chat()` path, upstream SSE frame parsing, concise XiaoRong prompt constraints, nullable runtime metadata, and Nginx stream proxy settings.
4. Kept the fallback path explicit and marked as degraded; only a provider/configuration/transport failure uses fallback content.


### SSE delivery verification (2026-07-16)

- [x] Replaced completed-answer chunking with provider upstream SSE (`stream: true`) and incremental `delta` forwarding.
- [x] Added Nginx `/api/` SSE no-buffer configuration: `proxy_buffering off`, `proxy_cache off`, `proxy_read_timeout 3600s`, and `X-Accel-Buffering: no`.
- [x] Free conversation system prompt now enforces the XiaoRong teacher persona and concise conclusion-first answers (3–5 sentences, target 180 Chinese characters).
- [x] Verification passed: backend full `mvn -q test`; frontend `npm test` (12/12); frontend `npm run build`; backend package; rebuilt local Docker services.
- [x] Post-deploy check: `GET http://localhost:8088/` returns 200, and the authenticated runtime endpoint now reaches the backend (unauthenticated response is expected 401 rather than 502).


## Follow-up correction: native DashScope SSE (2026-07-16)

- [x] Added Spring AI Alibaba core integration and a per-request `DashScopeChatModel` factory for the current user's encrypted personal Key and selected model.
- [x] Routed Bailian through protocol `dashscope`; Bailian streaming now calls `DashScopeChatModel.stream(Prompt).subscribe(...)` and returns without blocking the POST SSE controller thread.
- [x] Added explicit `ready` / incremental `delta` / terminal `done` events, controller no-cache headers, Nginx no-buffer settings, and frontend `cache: 'no-store'`.
- [x] Added backend stateful Markdown-star sanitization across SSE frame boundaries and frontend defensive sanitization; XiaoRong's prompt now requests concise plain text without Markdown emphasis.
- [x] Enabled DeepSeek, Bailian, and OneAPI in the trusted Provider catalog while preserving arbitrary manual model-name input.
- [x] Security boundary retained: personal Keys stay AES-GCM encrypted at rest, are decrypted only server-side for the selected request, are never sent to the browser, and remain represented as a masked last-four value in settings.
- [x] Verification passed: targeted adapter/SSE/user-routing tests; full `mvn -q test`; `mvn -q -DskipTests package`; frontend `npm test` (12/12) and `npm run build`; rebuilt Docker app/frontend; `/` returned 200 and protected APIs returned expected 401 instead of 502.
