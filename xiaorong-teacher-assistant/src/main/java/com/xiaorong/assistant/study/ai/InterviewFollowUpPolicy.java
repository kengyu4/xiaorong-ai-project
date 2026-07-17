package com.xiaorong.assistant.study.ai;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class InterviewFollowUpPolicy {
    private static final Set<String> STOP_WORDS = Set.of("我", "会", "的", "了", "是", "在", "通过", "可以", "处理", "进行", "一个", "这个");

    public Decision decide(String answer, List<String> expectedKeywords, int depth) {
        String text = answer == null ? "" : answer.trim();
        if (depth >= 2) return new Decision("none", "NO_FOLLOW_UP");
        if (containsUnexpectedKeyword(text, expectedKeywords)) return new Decision("ai", null);
        if (text.length() < 15) return new Decision("fixed", "你能结合一个实际场景再具体说明吗？");
        if (text.length() > 80) return new Decision("ai", null);
        return new Decision("fixed", "为什么这个机制能够解决当前问题？");
    }

    private boolean containsUnexpectedKeyword(String answer, List<String> expectedKeywords) {
        String lower = answer.toLowerCase(Locale.ROOT);
        if (expectedKeywords != null && expectedKeywords.stream().filter(java.util.Objects::nonNull)
                .anyMatch(keyword -> lower.contains(keyword.toLowerCase(Locale.ROOT)))) {
            String withoutExpected = lower;
            for (String keyword : expectedKeywords) withoutExpected = withoutExpected.replace(keyword.toLowerCase(Locale.ROOT), " ");
            return java.util.Arrays.stream(withoutExpected.split("[^a-z0-9\u4e00-\u9fa5]+"))
                    .anyMatch(word -> word.length() >= 5 && !STOP_WORDS.contains(word));
        }
        return answer.matches(".*[A-Za-z]{3,}.*") || answer.contains("缓存") || answer.contains("数据库") || answer.contains("线程");
    }

    public record Decision(String mode, String fixedQuestion) {}
}