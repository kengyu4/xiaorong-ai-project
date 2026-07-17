package com.xiaorong.assistant.study.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class StudyScoringService {

    public ScoreResult score(String answerText, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return new ScoreResult(60, List.of(), List.of());
        }
        String normalized = answerText == null ? "" : answerText.toLowerCase(Locale.ROOT);
        List<String> hits = new ArrayList<>();
        List<String> misses = new ArrayList<>();
        for (String keyword : keywords) {
            String key = keyword.toLowerCase(Locale.ROOT);
            String relaxedKey = key.replace(".", "").replace(" ", "");
            String relaxedAnswer = normalized.replace(" ", "");
            if (normalized.contains(key) || (!relaxedKey.equals(key) && relaxedAnswer.contains(relaxedKey))) {
                hits.add(keyword);
            } else {
                misses.add(keyword);
            }
        }
        int base = Math.round(hits.size() * 85.0f / keywords.size());
        int lengthBonus = normalized.length() > 90 ? 12 : normalized.length() > 42 ? 8 : normalized.length() > 18 ? 4 : 0;
        int score = Math.min(100, base + lengthBonus);
        return new ScoreResult(score, hits, misses);
    }

    public record ScoreResult(Integer score, List<String> hitKeywords, List<String> missKeywords) {
    }
}
