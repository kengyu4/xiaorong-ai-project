package com.xiaorong.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class XiaorongGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(XiaorongGatewayApplication.class, args);
    }
}
