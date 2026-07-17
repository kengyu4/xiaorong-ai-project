package com.xiaorong.assistant.study.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudyOverviewAggregatorTest {
    @Test
    void ranksWeakTagsByFrequencyInsteadOfReturningAPlaceholder() {
        StudyOverviewAggregator aggregator = new StudyOverviewAggregator();

        StudyOverviewAggregator.Overview overview = aggregator.aggregate(List.of(
                List.of("Proxy", "Reflect"),
                List.of("Proxy"),
                List.of("toRefs", "Proxy")
        ), 3);

        assertThat(overview.topWeakTag()).isEqualTo("Proxy");
        assertThat(overview.weakTags()).containsExactly("Proxy", "Reflect", "toRefs");
        assertThat(overview.weakTagCount()).isEqualTo(3);
        assertThat(overview.completedCount()).isEqualTo(3);
    }
}
