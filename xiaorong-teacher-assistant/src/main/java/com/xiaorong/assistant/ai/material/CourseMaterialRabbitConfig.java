package com.xiaorong.assistant.ai.material;

import com.xiaorong.assistant.config.XiaorongProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CourseMaterialRabbitConfig {
    @Bean
    DirectExchange lessonMaterialExchange(XiaorongProperties properties) {
        return new DirectExchange(properties.getRabbitmq().getLessonMaterialExchange(), true, false);
    }

    @Bean
    Queue lessonMaterialQueue(XiaorongProperties properties) {
        return new Queue(properties.getRabbitmq().getLessonMaterialQueue(), true);
    }

    @Bean
    Binding lessonMaterialBinding(Queue lessonMaterialQueue,
                                  DirectExchange lessonMaterialExchange,
                                  XiaorongProperties properties) {
        return BindingBuilder.bind(lessonMaterialQueue)
                .to(lessonMaterialExchange)
                .with(properties.getRabbitmq().getLessonMaterialRoutingKey());
    }
}
