import '../../../../../apikey-runtime-helper';
const api = {
  async get(path) {
    const res = await fetch(path);
    return unwrap(res);
  },
  async post(path, body) {
    const res = await fetch(path, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    return unwrap(res);
  }
};

const assets = {
  teacher: "/assets/characters/teacher-teaching-transparent@2x.png",
  baiziAsk: "/assets/characters/baizi-ask-transparent@2x.png",
  baiziComfort: "/assets/characters/baizi-comfort-transparent@2x.png",
  baiziHappy: "/assets/characters/baizi-happy-transparent@2x.png"
};

const state = {
  loading: true,
  error: "",
  mode: "immersive",
  courses: [],
  startingCourseId: null,
  session: null,
  script: null,
  nodeIndex: 0,
  nodeResult: null,
  askAnswer: "",
  homework: [],
  homeworkIndex: 0,
  homeworkResult: null,
  review: null,
  records: []
};

const app = document.getElementById("app");

init();

async function init() {
  await loadCourses();
  render();
}

async function unwrap(res) {
  if (!res.ok) {
    throw new Error(`请求失败：${res.status}`);
  }
  const json = await res.json();
  if (json.code !== 200) {
    throw new Error(json.message || "接口返回异常");
  }
  return json.data;
}

async function loadCourses() {
  state.loading = true;
  state.error = "";
  try {
    state.courses = await api.get("/api/study/courses?subjectId=1");
  } catch (error) {
    state.error = error.message;
  } finally {
    state.loading = false;
  }
}

function render() {
  if (!state.session) {
    renderHome();
    return;
  }
  if (state.review) {
    renderReview();
    return;
  }
  if (state.homework.length) {
    renderHomework();
    return;
  }
  renderClassroom();
}

function topbar(subtitle = "AI 智能刷题助手") {
  return `
    <header class="topbar">
      <div class="brand">
        <button class="brand-mark" data-action="home">AI</button>
        <div class="brand-copy">
          <strong>小绒老师助教</strong>
          <span>${escapeHtml(subtitle)}</span>
        </div>
      </div>
      <div class="top-actions">
        <span class="chip mint">后端 8088</span>
        <span class="chip sand">${state.mode === "immersive" ? "沉浸模式" : "快速模式"}</span>
        ${state.session ? `<button class="btn secondary" data-action="home">回到首页</button>` : `<button class="btn secondary" data-action="reload">重新加载</button>`}
      </div>
    </header>
  `;
}

function renderHome() {
  app.innerHTML = `
    <div class="container">
      ${topbar("课程列表来自后端服务，支持固定题库 / MySQL / Redis")}
      <section class="hero">
        <div class="hero-copy">
          <div>
            <div class="eyebrow">Stitch Cozy Tech × 本地角色素材</div>
            <h1 class="hero-title">把题库变成会讲课、会提问、会复盘的 AI 课堂</h1>
            <p class="hero-desc">小绒老师先讲知识点，白子同桌在课间向你请教。答完后直接进入作业，提交一题就讲评一题。</p>
          </div>
          <div class="hero-actions">
            <button class="btn" data-action="start-first">开始学习</button>
            <button class="btn ghost" data-action="toggle-mode">${state.mode === "immersive" ? "切换快速模式" : "切换沉浸模式"}</button>
          </div>
        </div>

        <div class="hero-visual">
          <div class="visual-top">
            <span class="chip">首页 P0 骨架</span>
            <span class="chip mint">可连后端</span>
          </div>
          <div class="character-stage">
            <div class="character-card">
              <img src="${assets.teacher}" alt="小绒老师">
              <div class="character-label">
                <strong>小绒老师</strong>
                <span>讲课、提问、纠错、作业讲评</span>
              </div>
            </div>
            <div class="character-card baizi">
              <img src="${assets.baiziAsk}" alt="白子同桌">
              <div class="character-label">
                <strong>白子同桌</strong>
                <span>请教你、鼓励你、一起复盘</span>
              </div>
            </div>
          </div>
          <div class="mini-dialogs">
            <p>小绒老师：先选一个专题，我会把题目拆成几个容易理解的小节点。</p>
            <p>白子：我会在沉浸模式里向你请教问题，我们一起把知识点讲清楚。</p>
          </div>
        </div>
      </section>

      <section class="stats-grid">
        <div class="stat-card"><span>课程数量</span><strong>${state.courses.length || "--"}</strong></div>
        <div class="stat-card"><span>今日进度</span><strong>3 / 5</strong></div>
        <div class="stat-card"><span>薄弱点</span><strong>Proxy</strong></div>
        <div class="stat-card"><span>协作值</span><strong>${state.mode === "immersive" ? "12" : "--"}</strong></div>
      </section>

      <section class="section">
        <div class="section-head">
          <div>
            <h2>选择学习模式</h2>
            <p>快速模式少互动；沉浸模式会启用白子同桌和协作值。</p>
          </div>
        </div>
        <div class="mode-grid">
          ${modeCard("quick", "快", "快速模式", "少互动，直接刷重点，适合复习。")}
          ${modeCard("immersive", "伴", "沉浸模式", "小绒老师讲课，白子同桌一起复盘。")}
        </div>
      </section>

      <section class="section">
        <div class="section-head">
          <div>
            <h2>推荐课程</h2>
            <p>点击开始学习会调用后端创建 session，然后拉取课程脚本。</p>
          </div>
          <span class="chip">GET /api/study/courses</span>
        </div>
        ${homeCourseArea()}
      </section>

      <section class="section persona-grid">
        <div class="persona-card">
          <div class="avatar-mini"><img src="${assets.teacher}" alt="小绒老师"></div>
          <div>
            <strong>小绒老师</strong>
            <p>课堂节点来自后端脚本，讲完后会进入作业。</p>
          </div>
        </div>
        <div class="persona-card">
          <div class="avatar-mini"><img src="${assets.baiziHappy}" alt="白子同桌"></div>
          <div>
            <strong>白子同桌</strong>
            <p>沉浸模式下会出现同桌互助，答对会增加协作值。</p>
          </div>
        </div>
      </section>
    </div>
  `;
  bindCommonActions();
}

function modeCard(mode, icon, title, text) {
  return `
    <button class="mode-card ${state.mode === mode ? "active" : ""}" data-mode="${mode}">
      <div class="mode-icon">${icon}</div>
      <div class="mode-copy">
        <strong>${title}</strong>
        <span>${text}</span>
      </div>
    </button>
  `;
}

function homeCourseArea() {
  if (state.loading) {
    return `<div class="panel"><p class="empty-state">课程加载中...</p></div>`;
  }
  if (state.error) {
    return `<div class="panel"><p class="empty-state">${escapeHtml(state.error)}</p><button class="btn" data-action="reload">重新加载</button></div>`;
  }
  if (!state.courses.length) {
    return `<div class="panel"><p class="empty-state">暂时没有可学习课程。</p></div>`;
  }
  return `<div class="course-grid">${state.courses.map(courseCard).join("")}</div>`;
}

function courseCard(course, index) {
  const tags = (course.tags || []).slice(0, 3).map((tag, tagIndex) => {
    const cls = tagIndex === 1 ? "tag mint" : tagIndex === 2 ? "tag sand" : "tag";
    return `<span class="${cls}">${escapeHtml(tag)}</span>`;
  }).join("");
  const disabled = course.lessonCount === 0 || state.startingCourseId;
  const label = state.startingCourseId === course.courseId ? "创建中..." : course.lessonCount === 0 ? "即将开放" : "开始学习";
  return `
    <article class="course-card">
      <div class="course-cover">
        <span class="chip">${difficultyText(course.difficulty)}</span>
        <div class="cover-icon">${index + 1}</div>
      </div>
      <div>
        <h3>${escapeHtml(course.title)}</h3>
        <p>${escapeHtml(course.description)}</p>
        <div class="course-meta">
          ${tags}
          <span class="pill">${course.lessonCount} 节点</span>
          <span class="pill">${course.homeworkCount} 题</span>
        </div>
      </div>
      <button class="btn" data-course="${course.courseId}" ${disabled ? "disabled" : ""}>${label}</button>
    </article>
  `;
}

function bindCommonActions() {
  document.querySelectorAll("[data-action='reload']").forEach(btn => {
    btn.addEventListener("click", async () => {
      await loadCourses();
      render();
    });
  });
  document.querySelectorAll("[data-action='toggle-mode']").forEach(btn => {
    btn.addEventListener("click", () => {
      state.mode = state.mode === "immersive" ? "quick" : "immersive";
      render();
    });
  });
  document.querySelectorAll("[data-action='start-first']").forEach(btn => {
    btn.addEventListener("click", () => {
      const first = state.courses.find(course => course.lessonCount > 0);
      if (first) startSession(first.courseId);
    });
  });
  document.querySelectorAll("[data-action='home']").forEach(btn => {
    btn.addEventListener("click", resetToHome);
  });
  document.querySelectorAll("[data-mode]").forEach(btn => {
    btn.addEventListener("click", () => {
      state.mode = btn.dataset.mode;
      render();
    });
  });
  document.querySelectorAll("[data-course]").forEach(btn => {
    btn.addEventListener("click", () => startSession(Number(btn.dataset.course)));
  });
}

async function startSession(courseId) {
  state.startingCourseId = courseId;
  render();
  try {
    state.session = await api.post("/api/study/sessions", { courseId, mode: state.mode });
    state.script = await api.get(`/api/study/sessions/${state.session.sessionId}/script`);
    state.nodeIndex = state.session.currentNodeIndex || 0;
    state.nodeResult = null;
    state.homework = [];
    state.homeworkIndex = 0;
    state.homeworkResult = null;
    state.review = null;
    state.records = [`创建学习会话：${state.session.sessionId}`];
  } catch (error) {
    showToast(error.message);
    state.session = null;
  } finally {
    state.startingCourseId = null;
    render();
  }
}

function renderClassroom() {
  const nodes = state.script?.nodes || [];
  const node = nodes[state.nodeIndex] || nodes[0];
  const subtitle = `${state.script?.title || "AI 课堂"} · session ${state.session.sessionId}`;
  app.innerHTML = `
    <div class="container">
      ${topbar(subtitle)}
      <section class="study-layout">
        <main>
          ${node ? renderNode(node) : `<div class="lesson-card"><p class="empty-state">课程脚本为空。</p></div>`}
        </main>
        ${renderSidePanel()}
      </section>
    </div>
  `;
  bindCommonActions();
  bindNodeActions(node);
}

function renderNode(node) {
  if (node.type === "checkpoint") return renderCheckpoint(node);
  if (node.type === "classmate") return renderClassmate(node);
  if (node.type === "homework_intro") return renderLecture(node, true);
  return renderLecture(node, false);
}

function renderLecture(node, homeworkIntro) {
  return `
    <section class="lesson-card">
      <div class="lesson-head">
        <div>
          <h1 class="lesson-title">${escapeHtml(node.title)}</h1>
          <p class="lesson-desc">${escapeHtml(node.knowledgePoint || "AI 讲课节点")}</p>
        </div>
        <span class="chip mint">${state.nodeIndex + 1} / ${state.script.nodes.length}</span>
      </div>
      <div class="dialogue-layout">
        ${speakerCard("teacher")}
        <div class="dialogue-box">
          <div class="dialogue-meta">
            <span>小绒老师</span>
            <span>${homeworkIntro ? "准备作业" : "讲课中"}</span>
          </div>
          <div class="dialogue-text">${escapeHtml(node.text || "")}</div>
          <div class="dialogue-tip">当前节点来自后端课程脚本。</div>
        </div>
      </div>
      ${renderAskBox(node.nodeId)}
      <div class="action-row">
        <button class="btn secondary" data-step="prev" ${state.nodeIndex === 0 ? "disabled" : ""}>上一句</button>
        ${homeworkIntro ? `<button class="btn" data-action="load-homework">进入作业</button>` : `<button class="btn" data-step="next">继续讲</button>`}
      </div>
    </section>
  `;
}

function renderCheckpoint(node) {
  return `
    <section class="lesson-card">
      <div class="lesson-head">
        <div>
          <h1 class="lesson-title">${escapeHtml(node.title)}</h1>
          <p class="lesson-desc">${escapeHtml(node.knowledgePoint || "课堂提问")}</p>
        </div>
        <span class="chip mint">${state.nodeIndex + 1} / ${state.script.nodes.length}</span>
      </div>
      <div class="dialogue-layout">
        ${speakerCard("teacher")}
        <div class="dialogue-box">
          <div class="dialogue-meta">
            <span>小绒老师 · 课堂提问</span>
            <span>实时反馈</span>
          </div>
          <div class="dialogue-text">${escapeHtml(node.question || "")}</div>
          <div class="dialogue-tip">先用自己的话回答。后端会按关键词给即时反馈。</div>
        </div>
      </div>
      ${renderAnswerBox(node, "submit-node")}
      ${renderNodeResult()}
      ${renderAskBox(node.nodeId)}
      <div class="action-row">
        <button class="btn secondary" data-step="prev">上一句</button>
        <button class="btn" data-step="next" ${!state.nodeResult ? "disabled" : ""}>继续讲</button>
      </div>
    </section>
  `;
}

function renderClassmate(node) {
  if (state.mode !== "immersive") {
    return `
      <section class="lesson-card">
        <div class="lesson-head">
          <div>
            <h1 class="lesson-title">快速模式已跳过同桌互助</h1>
            <p class="lesson-desc">切换沉浸模式后，白子会在课间向你请教。</p>
          </div>
          <span class="chip">快速模式</span>
        </div>
        <div class="action-row">
          <button class="btn secondary" data-step="prev">上一句</button>
          <button class="btn" data-step="next">继续讲</button>
        </div>
      </section>
    `;
  }
  return `
    <section class="lesson-card">
      <div class="lesson-head">
        <div>
          <h1 class="lesson-title">${escapeHtml(node.title)}</h1>
          <p class="lesson-desc">${escapeHtml(node.knowledgePoint || "课间互助")}</p>
        </div>
        <span class="chip sand">协作值 ${state.script.classmate?.bondValue || 0}</span>
      </div>
      <div class="dialogue-layout">
        ${speakerCard("baizi", state.nodeResult?.score >= 70 ? "happy" : "ask")}
        <div class="dialogue-box">
          <div class="dialogue-meta">
            <span>白子同桌 · 请教你</span>
            <span>课间互助</span>
          </div>
          <div class="dialogue-text">${escapeHtml(node.question || "")}</div>
          <div class="dialogue-tip">讲给白子听，会倒逼你整理表达。答错也会一起复盘。</div>
        </div>
      </div>
      ${renderAnswerBox(node, "submit-classmate")}
      ${renderNodeResult()}
      <div class="action-row">
        <button class="btn secondary" data-step="prev">上一句</button>
        <button class="btn" data-step="next" ${!state.nodeResult ? "disabled" : ""}>继续讲</button>
      </div>
    </section>
  `;
}

function speakerCard(type, mood = "default") {
  if (type === "baizi") {
    const img = mood === "happy" ? assets.baiziHappy : mood === "comfort" ? assets.baiziComfort : assets.baiziAsk;
    return `
      <div class="speaker-card baizi">
        <img src="${img}" alt="白子同桌">
        <div class="speaker-name"><strong>白子同桌</strong><span>学习伙伴</span></div>
      </div>
    `;
  }
  return `
    <div class="speaker-card">
      <img src="${assets.teacher}" alt="小绒老师">
      <div class="speaker-name"><strong>小绒老师</strong><span>AI 老师</span></div>
    </div>
  `;
}

function renderAnswerBox(node, action) {
  return `
    <div class="answer-box">
      <strong>你的回答</strong>
      <textarea id="nodeAnswer" placeholder="写下你的理解，提交后会得到即时反馈。"></textarea>
      <div class="keyword-row">
        ${(node.answerKeywords || []).map(keyword => `<span class="keyword">${escapeHtml(keyword)}</span>`).join("")}
      </div>
      <div class="action-row">
        <button class="btn" data-action="${action}" data-node="${node.nodeId}">提交回答</button>
      </div>
    </div>
  `;
}

function renderNodeResult() {
  if (!state.nodeResult) return "";
  const result = state.nodeResult;
  const hits = result.hitKeywords || [];
  const misses = result.missKeywords || [];
  return `
    <div class="result-box">
      <div class="dialogue-meta">
        <span>即时反馈</span>
        <span class="score">${result.score || 0}</span>
      </div>
      <p>${escapeHtml(result.teacherReply || result.classmateReply || result.feedback || "")}</p>
      <p>${escapeHtml(result.feedback || result.teacherSupplement || "")}</p>
      <div class="keyword-row">
        ${hits.map(keyword => `<span class="keyword hit">${escapeHtml(keyword)}</span>`).join("")}
        ${misses.map(keyword => `<span class="keyword">${escapeHtml(keyword)}</span>`).join("")}
      </div>
    </div>
  `;
}

function renderAskBox(nodeId) {
  return `
    <div class="ask-box">
      <strong>举手提问</strong>
      <div class="action-row">
        <input id="askInput" placeholder="例如：ref 在模板里为什么不用 .value？">
        <button class="btn warn" data-action="ask" data-node="${nodeId}">提问</button>
      </div>
      ${state.askAnswer ? `<p>${escapeHtml(state.askAnswer)}</p>` : ""}
    </div>
  `;
}

function bindNodeActions(node) {
  if (!node) return;
  document.querySelectorAll("[data-step]").forEach(btn => {
    btn.addEventListener("click", () => {
      const step = btn.dataset.step;
      state.nodeResult = null;
      state.askAnswer = "";
      if (step === "prev") state.nodeIndex = Math.max(0, state.nodeIndex - 1);
      if (step === "next") state.nodeIndex = Math.min((state.script.nodes || []).length - 1, state.nodeIndex + 1);
      render();
    });
  });
  document.querySelectorAll("[data-action='submit-node']").forEach(btn => {
    btn.addEventListener("click", () => submitNode(btn.dataset.node));
  });
  document.querySelectorAll("[data-action='submit-classmate']").forEach(btn => {
    btn.addEventListener("click", () => submitClassmate(btn.dataset.node));
  });
  document.querySelectorAll("[data-action='ask']").forEach(btn => {
    btn.addEventListener("click", () => askQuestion(btn.dataset.node));
  });
  document.querySelectorAll("[data-action='load-homework']").forEach(btn => {
    btn.addEventListener("click", loadHomework);
  });
}

async function submitNode(nodeId) {
  const answer = document.getElementById("nodeAnswer")?.value.trim();
  if (!answer) return showToast("先写一点你的理解");
  try {
    state.nodeResult = await api.post(`/api/study/sessions/${state.session.sessionId}/nodes/${nodeId}/submit`, { answerText: answer });
    state.records.push(`课堂提问 ${nodeId}：${state.nodeResult.score} 分`);
    render();
  } catch (error) {
    showToast(error.message);
  }
}

async function submitClassmate(nodeId) {
  const answer = document.getElementById("nodeAnswer")?.value.trim();
  if (!answer) return showToast("先给白子讲一下你的理解");
  try {
    state.nodeResult = await api.post(`/api/study/sessions/${state.session.sessionId}/classmate/${nodeId}/submit`, { answerText: answer });
    if (state.script?.classmate) state.script.classmate.bondValue = state.nodeResult.bondValue;
    state.records.push(`同桌互助 ${nodeId}：协作值 +${state.nodeResult.bondDelta}`);
    render();
  } catch (error) {
    showToast(error.message);
  }
}

async function askQuestion(nodeId) {
  const question = document.getElementById("askInput")?.value.trim();
  if (!question) return showToast("请输入你想问的问题");
  try {
    const result = await api.post(`/api/study/sessions/${state.session.sessionId}/ask`, { nodeId, question });
    state.askAnswer = result.answer;
    state.records.push("举手提问：已获得补讲");
    render();
  } catch (error) {
    showToast(error.message);
  }
}

async function loadHomework() {
  try {
    const result = await api.get(`/api/study/sessions/${state.session.sessionId}/homework`);
    state.homework = result.items || [];
    state.homeworkIndex = 0;
    state.homeworkResult = null;
    state.records.push("进入课后作业");
    render();
  } catch (error) {
    showToast(error.message);
  }
}

function renderHomework() {
  const item = state.homework[state.homeworkIndex];
  app.innerHTML = `
    <div class="container">
      ${topbar(`课后作业 · session ${state.session.sessionId}`)}
      <section class="study-layout">
        <main>
          <section class="lesson-card homework-card">
            <div class="lesson-head">
              <div>
                <h1 class="lesson-title">课后作业</h1>
                <p class="lesson-desc">提交一题，小绒老师就讲评一题。</p>
              </div>
              <span class="chip mint">${state.homeworkIndex + 1} / ${state.homework.length}</span>
            </div>
            ${item ? renderHomeworkItem(item) : `<p class="empty-state">暂无作业题。</p>`}
          </section>
        </main>
        ${renderSidePanel()}
      </section>
    </div>
  `;
  bindCommonActions();
  bindHomeworkActions(item);
}

function renderHomeworkItem(item) {
  return `
    <div>
      <div class="keyword-row">
        ${(item.tags || []).map(tag => `<span class="tag">${escapeHtml(tag)}</span>`).join("")}
        <span class="tag sand">${difficultyText(item.difficulty)}</span>
      </div>
      <h2 class="question-title">${escapeHtml(item.title)}</h2>
      <div class="question-body">${escapeHtml(item.body)}</div>
      <div class="homework-answer">
        <textarea id="homeworkAnswer" placeholder="先自己回答，再提交给小绒老师讲评。"></textarea>
      </div>
    </div>
    <div class="action-row">
      <button class="btn" data-action="submit-homework" data-topic="${item.topicId}">提交本题</button>
      <button class="btn secondary" data-action="prev-homework" ${state.homeworkIndex === 0 ? "disabled" : ""}>上一题</button>
      <button class="btn secondary" data-action="next-homework">${state.homeworkIndex === state.homework.length - 1 ? "完成复盘" : "下一题"}</button>
    </div>
    ${renderHomeworkResult()}
  `;
}

function renderHomeworkResult() {
  if (!state.homeworkResult) return "";
  const result = state.homeworkResult;
  return `
    <div class="result-box">
      <div class="dialogue-meta">
        <span>小绒老师逐题讲评</span>
        <span class="score">${result.score}</span>
      </div>
      <p>${escapeHtml(result.feedback)}</p>
      <p>${escapeHtml(result.aiReview)}</p>
      <div class="keyword-row">
        ${(result.hitKeywords || []).map(keyword => `<span class="keyword hit">${escapeHtml(keyword)}</span>`).join("")}
        ${(result.missKeywords || []).map(keyword => `<span class="keyword">${escapeHtml(keyword)}</span>`).join("")}
      </div>
      <p><strong>参考答案：</strong>${escapeHtml(result.standardAnswer)}</p>
    </div>
  `;
}

function bindHomeworkActions(item) {
  if (!item) return;
  document.querySelectorAll("[data-action='submit-homework']").forEach(btn => {
    btn.addEventListener("click", () => submitHomework(Number(btn.dataset.topic)));
  });
  document.querySelectorAll("[data-action='prev-homework']").forEach(btn => {
    btn.addEventListener("click", () => {
      state.homeworkResult = null;
      state.homeworkIndex = Math.max(0, state.homeworkIndex - 1);
      render();
    });
  });
  document.querySelectorAll("[data-action='next-homework']").forEach(btn => {
    btn.addEventListener("click", async () => {
      if (state.homeworkIndex === state.homework.length - 1) {
        await loadReview();
      } else {
        state.homeworkResult = null;
        state.homeworkIndex += 1;
        render();
      }
    });
  });
}

async function submitHomework(topicId) {
  const answer = document.getElementById("homeworkAnswer")?.value.trim();
  if (!answer) return showToast("先写下你的作答");
  try {
    state.homeworkResult = await api.post(`/api/study/sessions/${state.session.sessionId}/homework/${topicId}/submit`, { answerText: answer });
    state.records.push(`作业 ${topicId}：${state.homeworkResult.score} 分`);
    render();
  } catch (error) {
    showToast(error.message);
  }
}

async function loadReview() {
  try {
    state.review = await api.get(`/api/study/sessions/${state.session.sessionId}/review`);
    state.records.push("生成学习复盘");
    render();
  } catch (error) {
    showToast(error.message);
  }
}

function renderReview() {
  const review = state.review;
  app.innerHTML = `
    <div class="container">
      ${topbar(`学习复盘 · session ${state.session.sessionId}`)}
      <section class="lesson-card">
        <div class="lesson-head">
          <div>
            <h1 class="lesson-title">本轮学习复盘</h1>
            <p class="lesson-desc">${escapeHtml(review.summary)}</p>
          </div>
          <span class="chip mint">均分 ${review.averageScore}</span>
        </div>
        <div class="review-grid">
          <article class="course-card">
            <div class="course-cover"><span class="chip mint">掌握度</span><div class="cover-icon">${review.averageScore}</div></div>
            <div>
              <h3>小绒老师总结</h3>
              <p>${escapeHtml(review.teacherSummary)}</p>
            </div>
            <button class="btn" data-action="restart">重新学习</button>
          </article>
          <article class="course-card">
            <div class="course-cover"><span class="chip sand">白子</span><div class="cover-icon">白</div></div>
            <div>
              <h3>白子同桌</h3>
              <p>${escapeHtml(review.classmateReply)}</p>
              <p>协作值：${review.bondValue}</p>
            </div>
            <button class="btn ghost" data-action="home">返回首页</button>
          </article>
          <article class="course-card">
            <div class="course-cover"><span class="chip">下一课</span><div class="cover-icon">下</div></div>
            <div>
              <h3>${escapeHtml(review.nextCourse?.title || "下一节推荐")}</h3>
              <p>薄弱点：${(review.weakTags || []).map(escapeHtml).join("、")}</p>
            </div>
            <button class="btn secondary" data-action="home">选择课程</button>
          </article>
        </div>
        <div class="panel">
          <h2>下一步建议</h2>
          <div class="record-list">${(review.nextActions || []).map(item => `<div class="record-item">${escapeHtml(item)}</div>`).join("")}</div>
        </div>
      </section>
    </div>
  `;
  bindCommonActions();
  document.querySelectorAll("[data-action='restart']").forEach(btn => {
    btn.addEventListener("click", () => startSession(state.session.courseId));
  });
}

function renderSidePanel() {
  const nodes = state.script?.nodes || [];
  return `
    <aside class="side-panel">
      <section class="panel">
        <h2>课堂地图</h2>
        <div class="timeline">
          ${nodes.map((node, index) => `
            <div class="timeline-item ${index === state.nodeIndex && !state.homework.length ? "active" : ""}">
              <strong>${index + 1}. ${escapeHtml(node.title)}</strong><br>
              ${escapeHtml(typeText(node.type))}
            </div>
          `).join("")}
        </div>
      </section>
      <section class="panel">
        <h2>学习记录</h2>
        <div class="record-list">
          ${state.records.length ? state.records.slice(-6).map(item => `<div class="record-item">${escapeHtml(item)}</div>`).join("") : `<div class="record-item">还没有记录。</div>`}
        </div>
      </section>
      <section class="panel">
        <h2>角色状态</h2>
        <div class="persona-card">
          <div class="avatar-mini"><img src="${assets.baiziHappy}" alt="白子"></div>
          <div><strong>白子同桌</strong><p>协作值 ${state.script?.classmate?.bondValue || state.review?.bondValue || 0}</p></div>
        </div>
      </section>
    </aside>
  `;
}

function resetToHome() {
  state.session = null;
  state.script = null;
  state.nodeIndex = 0;
  state.nodeResult = null;
  state.askAnswer = "";
  state.homework = [];
  state.homeworkIndex = 0;
  state.homeworkResult = null;
  state.review = null;
  state.records = [];
  render();
}

function difficultyText(value) {
  return ({ easy: "入门", medium: "进阶", hard: "挑战" })[value] || value || "入门";
}

function typeText(value) {
  return ({
    lecture: "讲课",
    checkpoint: "课堂提问",
    classmate: "白子互助",
    homework_intro: "作业说明"
  })[value] || value;
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, char => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    "\"": "&quot;",
    "'": "&#39;"
  })[char]);
}

function showToast(message) {
  const old = document.querySelector(".toast");
  if (old) old.remove();
  const toast = document.createElement("div");
  toast.className = "toast";
  toast.textContent = message;
  document.body.appendChild(toast);
  setTimeout(() => toast.remove(), 2200);
}
