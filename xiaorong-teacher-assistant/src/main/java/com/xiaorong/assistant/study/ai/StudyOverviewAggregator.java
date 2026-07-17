package com.xiaorong.assistant.study.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class StudyOverviewAggregator {
    public Overview aggregate(List<List<String>> missedKeywordGroups, int completedCount) {
        Map<String, Integer> counts = new HashMap<>();
        if (missedKeywordGroups != null) {
            missedKeywordGroups.stream().filter(java.util.Objects::nonNull).flatMap(List::stream)
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(value -> counts.merge(value.trim(), 1, Integer::sum));
        }
        List<String> tags = new ArrayList<>(counts.keySet());
        tags.sort((left, right) -> {
            int frequency = Integer.compare(counts.get(right), counts.get(left));
            return frequency != 0 ? frequency : left.compareToIgnoreCase(right);
        });
        return new Overview(tags.isEmpty() ? null : tags.get(0), List.copyOf(tags), tags.size(), completedCount);
    }

    public record Overview(String topWeakTag, List<String> weakTags, int weakTagCount, int completedCount) {}
}