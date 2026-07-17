# AI 老师刷题 Demo

这个 demo 用一个静态 `index.html` 演示新的 AI 智能刷题交互：AI 不再只是最后批改答案，而是从一开始就像老师一样介入学习。

## 新的学习闭环

1. 选方向：用户先选择一个学习方向，例如 Vue3 响应式、AI 刷题产品设计、高效学习交互。
2. AI 讲课：AI 根据题库标签和知识点，用游戏对话框一句一句讲课。
3. 举手提问：听课中用户可以点击“举手提问”，选择快捷问题或输入自己的问题。
4. 布置作业：知识点讲完后，AI 根据当前方向的题库布置练习题。
5. 逐题讲评：用户每提交一题，AI 立即讲评这一题，指出命中关键词、缺失关键词、参考表达。
6. 复盘推荐：作业结束后沉淀均分、薄弱关键词，并推荐下一节学习方向。

## Demo 中模拟了什么

- 题库方向：`courses`
- 知识点讲课脚本：`course.lesson`
- 作业题库：`course.questions`
- AI 举手问答：`answerStudentQuestion`
- AI 本地评分：`scoreAnswer`
- AI 逐题讲评：`buildAiExplanation`
- 学习记录：`state.logs`、`state.answers`

目前没有真实调用后端或大模型，重点是先把产品体验跑通。迁回真实项目时，可以把这些本地模拟函数替换成 SpringAI 服务接口。

## 迁回 uni-app 的建议

优先改：

`hao-topic-master/uni-app-vue3-brushtopic/src/pages/database/topic/topic.vue`

建议把原来的“题目页”拆成几个学习阶段：

- `choose`：选择学习方向。
- `lesson`：AI 老师讲解知识点。
- `questioning`：课堂中举手提问。
- `homework`：课后作业答题。
- `review`：作业复盘和推荐。

建议新增前端状态：

- `studyPhase`：当前学习阶段。
- `courseId`：当前学习方向。
- `lessonIndex`：当前讲到第几个知识点。
- `dialogueList`：AI 课堂对话记录。
- `studentQuestion`：用户举手提问内容。
- `homeworkList`：当前方向下的作业题。
- `currentHomeworkIndex`：当前作业题下标。
- `userAnswer`：用户当前作答内容。
- `answerScore`：本题评分。
- `hitKeywords`：命中的关键词。
- `missKeywords`：缺失的关键词。
- `studyRecord`：本轮学习记录。

建议新增后端接口：

- `POST /ai/study/course/recommend`
  - 入参：`userId`、`subjectId`、`weakTags`
  - 出参：推荐学习方向和知识点列表。

- `POST /ai/study/lesson`
  - 入参：`courseId`、`topicIds`
  - 出参：AI 讲课脚本，包含多段对话、知识点、例子。

- `POST /ai/study/ask`
  - 入参：`courseId`、`lessonIndex`、`question`
  - 出参：AI 针对当前课堂上下文的补充讲解。

- `POST /topic/topic/submitAnswer`
  - 入参：`topicId`、`subjectId`、`answerText`
  - 出参：`score`、`hitKeywords`、`missKeywords`、`feedback`、`suggestion`。

- `POST /ai/study/homework/review`
  - 入参：`courseId`、`topicId`、`answerText`、`score`
  - 出参：AI 逐题讲评、参考答案、下一步建议。

- `POST /topic/topic/studyRecord`
  - 入参：`courseId`、`topicId`、`score`、`status`、`duration`、`weakTags`
  - 用途：记录错题、掌握度、薄弱知识点、复习计划。

## 如何预览

直接用浏览器打开：

`interactive-quiz-demo/index.html`
