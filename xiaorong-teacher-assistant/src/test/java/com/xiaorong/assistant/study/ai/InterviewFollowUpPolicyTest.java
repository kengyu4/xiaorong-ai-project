package com.xiaorong.assistant.study.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewFollowUpPolicyTest {
    private final InterviewFollowUpPolicy policy = new InterviewFollowUpPolicy();

    @Test
    void appliesLengthKeywordAndDepthRules() {
        assertThat(policy.decide("太短了", List.of("Proxy"), 0).mode()).isEqualTo("fixed");
        assertThat(policy.decide("这是一段超过八十个字符的回答，用来详细描述响应式系统如何代理对象，并继续补充很多项目中的边界条件、异常处理方式、依赖收集过程、更新触发机制、调度顺序、循环依赖防护以及实际工程中的性能取舍。", List.of("Proxy"), 0).mode())
                .isEqualTo("ai");
        assertThat(policy.decide("我会通过 Redis 处理缓存", List.of("Proxy"), 0).mode()).isEqualTo("ai");
        assertThat(policy.decide("Proxy 可以拦截对象操作", List.of("Proxy"), 2).mode()).isEqualTo("none");
    }
}
