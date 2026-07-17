package com.xiaorong.assistant.study.content;

import com.xiaorong.assistant.study.content.StudyMaterial.HomeworkSeed;
import com.xiaorong.assistant.study.dto.StudyDtos.ClassmateView;
import com.xiaorong.assistant.study.dto.StudyDtos.CourseSummary;
import com.xiaorong.assistant.study.dto.StudyDtos.LessonNode;
import com.xiaorong.assistant.study.dto.StudyDtos.PersonaView;
import com.xiaorong.assistant.study.dto.StudyDtos.Reward;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StudyTemplateProvider {
    private static final Pattern TOPIC_HEADING = Pattern.compile("^###\\s+(\\d+)\\.\\s+(.+)$");
    private static final List<String> DOMAIN_KEYWORDS = List.of(
            "ACID", "事务", "隔离", "MVCC", "ReadView", "undo log", "redo log", "binlog",
            "索引", "B+ 树", "聚集索引", "回表", "覆盖索引", "最左前缀", "Explain",
            "分库分表", "外键", "SQL", "NoSQL", "InnoDB", "MyISAM",
            "IoC", "AOP", "反射", "动态代理", "三级缓存", "循环依赖", "事务失效", "Bean",
            "SpringBoot", "自动装配", "Starter", "Mybatis", "#{}", "${}", "ResultMap", "动态 SQL"
    );

    private final String templatePath;

    public StudyTemplateProvider(@Value("${xiaorong.study.template-path:}") String templatePath) {
        this.templatePath = templatePath;
    }

    public List<StudyMaterial> loadMaterials() {
        Path path = resolveTemplatePath();
        if (path != null && Files.exists(path)) {
            try {
                return List.of(parseBackendInterviewTemplate(path));
            } catch (IOException ex) {
                return List.of(fallbackVueMaterial());
            }
        }
        return List.of(fallbackVueMaterial());
    }

    public StudyMaterial firstMaterial() {
        return loadMaterials().get(0);
    }

    private Path resolveTemplatePath() {
        if (templatePath == null || templatePath.isBlank()) {
            return null;
        }
        Path path = Path.of(templatePath);
        if (path.isAbsolute()) {
            return path;
        }
        return Path.of(System.getProperty("user.dir")).resolve(path).normalize();
    }

    private StudyMaterial parseBackendInterviewTemplate(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<TopicBlock> topics = parseTopics(lines);
        List<LessonNode> nodes = new ArrayList<>();
        for (TopicBlock topic : topics) {
            List<String> keywords = extractKeywords(topic.title + " " + topic.lecture + " " + topic.question);
            nodes.add(new LessonNode(
                    topic.nodePrefix() + "-lecture",
                    "lecture",
                    "teacher",
                    topic.index + ". " + topic.title,
                    "小绒老师：" + topic.lecture,
                    topic.title,
                    null,
                    null,
                    List.of(),
                    null,
                    null,
                    null
            ));
            nodes.add(new LessonNode(
                    topic.nodePrefix() + "-checkpoint",
                    "checkpoint",
                    "teacher",
                    "课堂提问：" + topic.title,
                    null,
                    topic.title,
                    topic.question,
                    "short_text",
                    keywords,
                    topic.lecture,
                    "答题时先说核心概念，再补一两个工程场景或面试表达。",
                    new Reward(0, 0)
            ));
            nodes.add(new LessonNode(
                    topic.nodePrefix() + "-classmate",
                    "classmate",
                    "classmate",
                    "白子来请教：" + topic.title,
                    null,
                    topic.title,
                    topic.classmateQuestion,
                    "short_text",
                    keywords,
                    topic.lecture,
                    "你可以把老师刚讲过的关键词换成自己的话讲给白子听。",
                    new Reward(3, 1)
            ));
        }
        nodes.add(new LessonNode(
                "homework-intro",
                "homework_intro",
                "teacher",
                "进入课后作业",
                "小绒老师：今天的固定题库已经讲完啦。接下来进入作业环节，你先用自己的话答题，提交后我会逐题讲评，白子也会陪你一起复盘错题。",
                "主动回忆",
                null,
                null,
                List.of(),
                null,
                null,
                null
        ));

        CourseSummary course = new CourseSummary(
                1L,
                "后端面试固定题库",
                "按题库顺序完成 Mysql、Spring、SpringBoot、Mybatis 的讲解、课堂提问与白子同桌互助。",
                "medium",
                List.of("后端面试", "Mysql", "Spring", "Mybatis"),
                nodes.size(),
                8,
                "/assets/characters/teacher-teaching-transparent@2x.png",
                "/assets/characters/baizi-happy-transparent@2x.png"
        );
        return new StudyMaterial(
                course,
                new PersonaView("小绒老师", "/assets/characters/teacher-teaching-transparent@2x.png", "teacher"),
                new ClassmateView("白子同桌", "/assets/characters/baizi-happy-transparent@2x.png", 0),
                nodes,
                buildHomework(topics)
        );
    }

    private List<TopicBlock> parseTopics(List<String> lines) {
        List<TopicBlock> topics = new ArrayList<>();
        TopicBlock current = null;
        Section section = Section.NONE;
        for (String line : lines) {
            Matcher matcher = TOPIC_HEADING.matcher(line);
            if (matcher.matches()) {
                if (current != null && current.isComplete()) {
                    topics.add(current);
                }
                current = new TopicBlock(Integer.parseInt(matcher.group(1)), matcher.group(2).trim());
                section = Section.NONE;
                continue;
            }
            if (current == null) {
                continue;
            }
            if (line.startsWith("**小绒老师 → 知识讲解**")) {
                section = Section.LECTURE;
                continue;
            }
            if (line.startsWith("**小绒老师 → 课堂提问**")) {
                section = Section.QUESTION;
                continue;
            }
            if (line.startsWith("**白子同桌 → 课间请教**")) {
                section = Section.CLASSMATE;
                continue;
            }
            if (line.startsWith("---") || line.startsWith("## ")) {
                section = Section.NONE;
                continue;
            }
            if (line.startsWith(">")) {
                current.append(section, line.substring(1).trim());
            }
        }
        if (current != null && current.isComplete()) {
            topics.add(current);
        }
        return topics;
    }

    private List<HomeworkSeed> buildHomework(List<TopicBlock> topics) {
        List<TopicBlock> selected = new ArrayList<>();
        for (String keyword : List.of("事务隔离", "MVCC", "索引", "循环依赖", "Bean", "自动装配", "Starter", "#{}")) {
            topics.stream()
                    .filter(topic -> topic.title.contains(keyword))
                    .findFirst()
                    .ifPresent(selected::add);
        }
        if (selected.size() < 8) {
            topics.stream()
                    .filter(topic -> !selected.contains(topic))
                    .limit(8 - selected.size())
                    .forEach(selected::add);
        }
        List<HomeworkSeed> homework = new ArrayList<>();
        long topicId = 1001L;
        for (TopicBlock topic : selected.stream().limit(8).toList()) {
            homework.add(new HomeworkSeed(
                    topicId++,
                    topic.title,
                    topic.question,
                    guessTags(topic.title),
                    "medium",
                    extractKeywords(topic.title + " " + topic.lecture),
                    topic.lecture
            ));
        }
        return homework;
    }

    private List<String> extractKeywords(String text) {
        Set<String> keywords = new LinkedHashSet<>();
        String normalized = text == null ? "" : text;
        for (String keyword : DOMAIN_KEYWORDS) {
            if (normalized.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) {
                keywords.add(keyword);
            }
        }
        for (String part : normalized.split("[，。；、：\\s/()（）,.;:]+")) {
            String clean = part.replace("“", "").replace("”", "").replace("\"", "").trim();
            if (clean.length() >= 2 && clean.length() <= 12 && keywords.size() < 6) {
                keywords.add(clean);
            }
        }
        if (keywords.isEmpty()) {
            keywords.add("核心概念");
        }
        return keywords.stream().limit(6).toList();
    }

    private List<String> guessTags(String title) {
        if (title.contains("SpringBoot")) {
            return List.of("SpringBoot");
        }
        if (title.contains("Spring") || title.contains("Bean") || title.contains("AOP") || title.contains("IoC")) {
            return List.of("Spring");
        }
        if (title.contains("Mybatis") || title.contains("#{}") || title.contains("SQL")) {
            return List.of("Mybatis");
        }
        return List.of("Mysql");
    }

    private StudyMaterial fallbackVueMaterial() {
        List<LessonNode> nodes = List.of(
                new LessonNode("n1", "lecture", "teacher", "先建立地图", "小绒老师：Vue3 响应式不是单纯背 API，而是理解数据变化后视图为什么会更新。今天先抓住 ref 和 reactive 的边界。", "响应式整体认知", null, null, List.of(), null, null, null),
                new LessonNode("n2", "checkpoint", "teacher", "课堂提问", null, "ref 使用场景", "number 类型为什么更适合用 ref？", "short_text", List.of("基本类型", "Proxy", ".value"), "基本类型不能直接被 Proxy 代理，ref 用 .value 做了一层包装。", "script 中访问 ref 要用 .value，模板里会自动解包。", new Reward(0, 0)),
                new LessonNode("n3", "lecture", "teacher", "再看 reactive", "小绒老师：reactive 更适合对象或数组。它像是给对象套上一层响应式代理，但解构时要特别小心。", "reactive 使用边界", null, null, List.of(), null, null, null),
                new LessonNode("n4", "classmate", "classmate", "课间互助", null, "reactive 解构", "我刚才有点没听懂，reactive 解构后为什么会丢响应式呀？你能用自己的话给我讲一下吗？", "short_text", List.of("解构", "响应式", "toRefs"), "解构后拿到的是普通值，可能断开响应式连接；需要保留时可用 toRefs。", "实际项目里可以用 toRefs 保留解构后的响应式。", new Reward(3, 1)),
                new LessonNode("n5", "homework_intro", "teacher", "进入作业", "小绒老师：接下来进入作业。每题先自己说一遍，再看讲评，这样比直接看答案更有效。", "主动回忆", null, null, List.of(), null, null, null)
        );
        CourseSummary course = new CourseSummary(1L, "Vue3 响应式入门", "跟着小绒老师理解 ref、reactive、computed 与 watch。", "easy", List.of("Vue3", "响应式"), nodes.size(), 1, "/assets/characters/teacher-teaching-transparent@2x.png", "/assets/characters/baizi-happy-transparent@2x.png");
        return new StudyMaterial(
                course,
                new PersonaView("小绒老师", "/assets/characters/teacher-teaching-transparent@2x.png", "teacher"),
                new ClassmateView("白子同桌", "/assets/characters/baizi-happy-transparent@2x.png", 0),
                nodes,
                List.of(new HomeworkSeed(101L, "Vue3 中 ref 和 reactive 的核心区别是什么？", "请从使用场景、访问方式、响应式边界三个角度回答。", List.of("Vue3", "响应式"), "easy", List.of("基本类型", "对象", ".value", "Proxy", "toRefs"), "ref 常用于基本类型或需要整体替换的值，script 中通过 .value 访问；reactive 常用于对象或数组，基于 Proxy 代理。reactive 解构会丢响应式，通常配合 toRefs。"))
        );
    }

    private enum Section {
        NONE,
        LECTURE,
        QUESTION,
        CLASSMATE
    }

    private static class TopicBlock {
        private final int index;
        private final String title;
        private String lecture = "";
        private String question = "";
        private String classmateQuestion = "";

        private TopicBlock(int index, String title) {
            this.index = index;
            this.title = title;
        }

        private String nodePrefix() {
            return "q" + index;
        }

        private boolean isComplete() {
            return !lecture.isBlank() && !question.isBlank() && !classmateQuestion.isBlank();
        }

        private void append(Section section, String text) {
            if (text.isBlank()) {
                return;
            }
            if (section == Section.LECTURE) {
                lecture = join(lecture, text);
            } else if (section == Section.QUESTION) {
                question = join(question, text);
            } else if (section == Section.CLASSMATE) {
                classmateQuestion = join(classmateQuestion, text);
            }
        }

        private String join(String oldText, String text) {
            return oldText.isBlank() ? text : oldText + "\n" + text;
        }
    }
}
