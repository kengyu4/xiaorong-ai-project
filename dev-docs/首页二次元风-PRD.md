# 首页二次元风 PRD

> PRD = Page Component Requirement，面向前端实现、后端对接和验收的页面组件需求文档。

## 1. 基础信息

| 项 | 内容 |
| --- | --- |
| 项目名 | 小绒老师助教 |
| 页面 | 首页 / 学习方向选择页 |
| Stitch 项目 | 次元智学助手 |
| Stitch Project ID | `9562389079729096288` |
| Stitch Screen | 首页 (二次元风) - AI 智能刷题助手 |
| Stitch Screen ID | `6861703bb02349b980210c8931ead141` |
| 设计尺寸 | `2560 x 2552`, Desktop |
| 本期目标 | 先实现首页 P0 骨架，能看到二次元学习助手的视觉方向，并能进入学习会话 |

说明：当前已获取 Stitch 元数据和屏幕列表，但截图 / HTML 托管文件下载多次超时。因此本 PRD 依据 Stitch 页面标题、相关二次元风屏幕、课程封面资产描述、现有 AI 刷题重构文档和角色设定文档整理。后续如果素材下载成功，再对局部布局和尺寸做二次校准。

## 2. 产品目标

首页要让用户第一眼感受到：

- 这是一个 AI 智能刷题助手，不是普通题库列表。
- 角色有陪伴感，但页面核心仍是学习、刷题、提分。
- 用户可以快速选择课程，并用“快速模式”或“沉浸模式”进入课堂。
- P0 阶段不追求完整产品闭环，但必须能串起 `课程列表 -> 创建会话 -> 课堂页`。

## 3. 页面范围

本 PRD 只覆盖首页。

包含：

- 顶部导航和品牌识别。
- 首页首屏欢迎区。
- AI 角色状态区。
- 课程 / 专题卡片列表。
- 学习模式切换。
- 学习数据概览。
- 开始学习入口。
- 空状态、加载态、失败态。

不包含：

- 课程详情页完整实现。
- AI 刷题练习页完整实现。
- 个人中心完整实现。
- 真实 AI 流式回答。
- 角色养成、皮肤、商城等非学习功能。

## 4. 视觉方向

基于 Stitch 第二版和角色设定，首页采用“低饱和二次元学习产品”风格。

| 维度 | 规范 |
| --- | --- |
| 主风格 | soft 2.5D app illustration, matte texture, anime-style learning UI |
| 主色 | `#2563EB` |
| 辅助色 | `#0F766E`, `#8B5CF6`, `#F59E0B` |
| 背景 | `#F5F7FB` / 淡蓝灰渐层可用，但不要大面积浓紫 |
| 正文 | `#172033` |
| 弱文本 | `#667085` |
| 边框 | `#E5E7EB` |
| 圆角 | 卡片 8px 左右，角色展示或封面可略大 |
| 阴影 | 轻阴影，不能做重游戏 UI |

角色视觉权重控制在页面 15%-25%。首页可以出现小绒老师和白子同桌，但不能让角色盖过课程选择。

## 5. 首页信息架构

推荐从上到下：

1. 顶部导航：Logo / 产品名 / 当前用户入口 / 个人中心入口。
2. 首屏 Hero：标题、简短副标题、主行动按钮、角色插画。
3. 学习状态条：今日进度、平均分、薄弱点、协作值。
4. 模式切换：快速模式 / 沉浸模式。
5. 推荐课程：课程卡片列表，默认展示 3-6 个。
6. AI 角色提示：小绒老师一句学习建议，白子同桌一句请教式提示。
7. 最近学习：继续上次会话入口，P0 可用 mock。

## 6. 组件拆分

| 组件 | 说明 | P0 |
| --- | --- | --- |
| `HomeHeader.vue` | 顶部导航、品牌、用户入口 | 必做 |
| `HomeHero.vue` | 首页主视觉、标题、主 CTA、角色图 | 必做 |
| `StudyStatsBar.vue` | 今日学习数据、平均分、薄弱点、协作值 | 必做，数据可 mock |
| `ModeSelector.vue` | 快速模式 / 沉浸模式切换 | 必做 |
| `CourseCard.vue` | 课程标题、说明、难度、标签、题数、角色头像、开始按钮 | 必做 |
| `DifficultyBadge.vue` | easy / medium / hard 显示 | 必做 |
| `PersonaHint.vue` | 小绒老师 / 白子同桌短台词 | 必做 |
| `ContinueStudyCard.vue` | 继续学习入口 | P0 可 mock |
| `HomeSkeleton.vue` | 加载骨架屏 | 建议做 |
| `HomeErrorState.vue` | 接口失败重试 | 建议做 |

## 7. 核心交互

### 7.1 进入首页

页面加载时请求课程列表：

```http
GET /api/study/courses?subjectId=1
```

成功后渲染推荐课程。失败时显示错误态和“重新加载”按钮。

### 7.2 切换学习模式

模式值：

```ts
type StudyMode = 'quick' | 'immersive'
```

- `quick`：快速模式，只保留小绒老师讲课、课堂提问、作业讲评。
- `immersive`：沉浸模式，启用白子同桌、协作值、课间互助。

模式仅影响创建会话参数和首页文案，不在首页实时请求 AI。

### 7.3 点击课程卡片

P0 可以有两种处理：

- 点击卡片主体：进入课程详情页占位。
- 点击“开始学习”：直接创建学习会话并跳转课堂页。

创建会话：

```http
POST /api/study/sessions
Content-Type: application/json

{
  "courseId": 1,
  "mode": "immersive"
}
```

成功后跳转：

```text
/study/classroom?sessionId={sessionId}
```

如果前端路由未完成，P0 可先跳到课堂占位页或打印 sessionId。

## 8. 数据契约

### 8.1 课程列表响应

后端当前骨架已支持：

```http
GET /api/study/courses?subjectId=1
```

首页需要字段：

```ts
interface CourseSummary {
  courseId: number
  title: string
  description: string
  difficulty: 'easy' | 'medium' | 'hard'
  tags: string[]
  lessonCount: number
  homeworkCount: number
  teacherAvatar: string
  classmateAvatar: string
}
```

前端展示规则：

- `title` 最多两行。
- `description` 最多两行。
- `tags` 最多展示 3 个，多余显示 `+N`。
- `difficulty` 映射为：`easy` 入门，`medium` 进阶，`hard` 挑战。
- 图片为空时使用本地默认角色头像。

### 8.2 创建会话响应

```ts
interface SessionCreateResponse {
  sessionId: number
  courseId: number
  status: 'learning' | 'homework' | 'finished'
  currentNodeIndex: number
}
```

创建成功后，首页不继续请求脚本，脚本由课堂页请求：

```http
GET /api/study/sessions/{sessionId}/script
```

## 9. 页面状态

```ts
interface HomeState {
  loading: boolean
  errorMessage: string
  selectedMode: 'quick' | 'immersive'
  selectedSubjectId: number
  courses: CourseSummary[]
  startingCourseId?: number
}
```

按钮状态：

- 课程列表加载中：课程区显示骨架屏。
- 创建会话中：对应课程按钮显示 loading，其他课程按钮禁用。
- 创建失败：toast 提示“创建学习会话失败，请稍后再试”。
- 无课程：显示空状态“暂时没有可学习课程”。

## 10. 文案规范

首页文案要短、具体，避免营销化。

推荐主标题：

```text
小绒老师助教
```

推荐副标题：

```text
把题库变成会讲课、会提问、会复盘的 AI 学习流程。
```

主按钮：

```text
开始学习
```

模式文案：

```text
快速模式：少互动，直接刷重点。
沉浸模式：小绒老师讲课，白子同桌一起复盘。
```

角色提示：

```text
小绒老师：先选一个专题，我会把题目拆成几个容易理解的小节点。
白子同桌：我会在课间向你请教一个问题，我们一起把知识点讲清楚。
```

禁用文案：

- 不用“好感度”“心动值”“亲密度”。
- 不用“老婆”“陪你到深夜”等暧昧表达。
- 不用“秒杀全网”“吊打题库”等夸张营销语。

## 11. 角色落地

首页只启用两个角色：

| 角色 | 首页用途 | 展示方式 |
| --- | --- | --- |
| 小绒老师 | 说明课程学习方式、引导开始学习 | Hero 主角色或提示气泡 |
| 白子同桌 | 说明沉浸模式、互助学习 | 小头像 / 次级角色气泡 |

角色图片优先级：

1. Stitch 下载成功的角色素材。
2. `AI角色设定与生图提示词.md` 生成的 WebP / PNG。
3. 本地占位图。

首页不要启用岚川面试官。面试官只放到后续“模拟面试模式”。

## 12. 前端实现建议

推荐目录：

```text
src/pages/study/home/index.vue
src/components/study/HomeHeader.vue
src/components/study/HomeHero.vue
src/components/study/StudyStatsBar.vue
src/components/study/ModeSelector.vue
src/components/study/CourseCard.vue
src/components/study/DifficultyBadge.vue
src/components/study/PersonaHint.vue
src/api/study.ts
src/types/study.ts
```

`study.ts` 建议方法：

```ts
export function getStudyCourses(subjectId: number) {}
export function createStudySession(payload: { courseId: number; mode: 'quick' | 'immersive' }) {}
```

P0 不需要 Pinia。若已有全局 store，可只存 `selectedMode` 和 `lastSessionId`。

## 13. 与后端骨架对齐

当前后端骨架可以先支撑首页：

| 前端动作 | 后端接口 | 状态 |
| --- | --- | --- |
| 拉课程 | `GET /api/study/courses?subjectId=1` | 已有 mock |
| 开始学习 | `POST /api/study/sessions` | 已有 mock |
| 课堂脚本 | `GET /api/study/sessions/{id}/script` | 已有 mock，课堂页使用 |
| 自由提问 | `POST /api/study/sessions/{id}/ask` | 首页不调用 |
| 学习复盘 | `GET /api/study/sessions/{id}/review` | 首页不调用 |

后端后续建议补充但不阻塞 P0：

- 课程封面字段 `coverImage`。
- 课程推荐理由 `recommendReason`。
- 最近学习会话 `GET /api/study/recent-session`。
- 首页统计 `GET /api/study/overview`。

## 14. P0 验收标准

功能验收：

- 首页能正常加载课程列表。
- 能切换快速模式 / 沉浸模式。
- 点击某个课程的开始学习，可以创建 session。
- 创建成功后能拿到 `sessionId` 并跳转课堂入口。
- 接口失败时有错误提示和重试入口。

视觉验收：

- 首页明显是二次元学习助手风格。
- 页面首先像学习工具，不像游戏活动页。
- 课程卡片信息可扫读，标题、难度、标签、题数清楚。
- 角色不遮挡课程和按钮。
- 移动端宽度下按钮文字不溢出。

业务验收：

- 快速模式不展示协作值增长暗示。
- 沉浸模式可以展示白子同桌和协作值，但文案必须是“协作值 / 一起复盘”等学习表达。
- 不出现恋爱化、暧昧化、过度卖萌文案。

## 15. 后续迭代

P1：

- 接入真实课程封面。
- 首页接入最近学习和学习统计。
- 课程卡片支持“继续学习”和“重新开始”。
- 根据薄弱标签推荐课程。

P2：

- 接入个人中心成就徽章。
- 根据用户学习记录动态排序课程。
- 首页角色台词由预生成模板驱动。
- Stitch 课程详情页、AI 刷题练习页、个人中心页统一视觉落地。

## 16. 风险与处理

| 风险 | 处理 |
| --- | --- |
| Stitch 托管截图 / HTML 暂时无法下载 | 先依据元数据和相关设计描述落 PRD，下载成功后修正细节 |
| 二次元风抢走学习注意力 | 角色权重限制在 15%-25%，课程卡片和 CTA 保持最高优先级 |
| token 消耗过高 | 首页不实时请求 AI，角色台词使用固定模板或后端预生成字段 |
| 后端字段不够 | P0 使用现有字段，封面 / 推荐理由 / 统计接口放到 P1 |
| 前端路由未完成 | 创建 session 后先进入课堂占位页，保证流程可感知 |

