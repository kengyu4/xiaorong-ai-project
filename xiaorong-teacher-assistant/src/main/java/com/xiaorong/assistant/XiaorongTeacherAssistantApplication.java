package com.xiaorong.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class XiaorongTeacherAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(XiaorongTeacherAssistantApplication.class, args);
    }
}
