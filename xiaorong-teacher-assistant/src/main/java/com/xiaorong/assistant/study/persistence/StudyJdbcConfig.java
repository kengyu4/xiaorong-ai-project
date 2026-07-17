package com.xiaorong.assistant.study.persistence;

import com.xiaorong.assistant.config.XiaorongProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Configuration
@ConditionalOnProperty(prefix = "xiaorong.persistence", name = "enabled", havingValue = "true")
public class StudyJdbcConfig {

    @Bean
    DataSource studyDataSource(XiaorongProperties properties) {
        ensureDatabaseExists(properties);
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(properties.getPersistence().getJdbcUrl());
        dataSource.setUsername(properties.getPersistence().getUsername());
        dataSource.setPassword(properties.getPersistence().getPassword());
        return dataSource;
    }

    @Bean
    JdbcTemplate studyJdbcTemplate(DataSource studyDataSource) {
        return new JdbcTemplate(studyDataSource);
    }

    private void ensureDatabaseExists(XiaorongProperties properties) {
        JdbcUrlParts parts = parseJdbcUrl(properties.getPersistence().getJdbcUrl());
        if (parts == null) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(
                parts.serverUrl(),
                properties.getPersistence().getUsername(),
                properties.getPersistence().getPassword()
        );
             Statement statement = connection.createStatement()) {
            statement.execute("create database if not exists `" + escapeIdentifier(parts.databaseName()) + "` default character set utf8mb4 collate utf8mb4_unicode_ci");
        } catch (SQLException ex) {
            throw new IllegalStateException("MySQL 数据库初始化失败：" + ex.getMessage(), ex);
        }
    }

    private JdbcUrlParts parseJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql://")) {
            return null;
        }
        int schemeEnd = "jdbc:mysql://".length();
        int slashIndex = jdbcUrl.indexOf('/', schemeEnd);
        if (slashIndex < 0 || slashIndex == jdbcUrl.length() - 1) {
            return null;
        }
        int queryIndex = jdbcUrl.indexOf('?', slashIndex);
        int databaseEnd = queryIndex < 0 ? jdbcUrl.length() : queryIndex;
        String databaseName = jdbcUrl.substring(slashIndex + 1, databaseEnd);
        if (databaseName.isBlank()) {
            return null;
        }
        String serverUrl = jdbcUrl.substring(0, slashIndex + 1) + (queryIndex < 0 ? "" : jdbcUrl.substring(queryIndex));
        return new JdbcUrlParts(serverUrl, databaseName);
    }

    private String escapeIdentifier(String value) {
        return value.replace("`", "``");
    }

    private record JdbcUrlParts(String serverUrl, String databaseName) {
    }
}
