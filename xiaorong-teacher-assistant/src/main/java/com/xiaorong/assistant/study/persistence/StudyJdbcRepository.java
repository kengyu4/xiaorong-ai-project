package com.xiaorong.assistant.study.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaorong.assistant.study.content.StudyMaterial;
import com.xiaorong.assistant.study.dto.StudyDtos.CourseSummary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Repository
@ConditionalOnProperty(prefix = "xiaorong.persistence", name = "enabled", havingValue = "true")
public class StudyJdbcRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public StudyJdbcRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void initSchema() {
        jdbcTemplate.execute("""
                create table if not exists ai_course (
                  id bigint primary key auto_increment,
                  subject_id bigint not null,
                  title varchar(100) not null,
                  description varchar(500),
                  difficulty varchar(20),
                  tags json,
                  teacher_persona_id bigint,
                  classmate_persona_id bigint,
                  topic_ids json,
                  enabled tinyint default 1,
                  create_time datetime,
                  update_time datetime
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists ai_lesson_material (
                  id bigint primary key auto_increment,
                  course_id bigint not null,
                  content_hash varchar(64) not null,
                  prompt_version varchar(50) not null,
                  script_json json,
                  status varchar(20) default 'pending',
                  error_msg varchar(1000),
                  create_time datetime,
                  update_time datetime,
                  unique key uk_course_hash (course_id, content_hash, prompt_version)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists ai_study_session (
                  id bigint primary key auto_increment,
                  user_id bigint not null,
                  course_id bigint not null,
                  status varchar(20) not null,
                  current_node_index int default 0,
                  current_homework_index int default 0,
                  total_score int default 0,
                  bond_value int default 0,
                  weak_tags json,
                  start_time datetime,
                  finish_time datetime,
                  create_time datetime,
                  update_time datetime
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists ai_study_record (
                  id bigint primary key auto_increment,
                  session_id bigint not null,
                  user_id bigint not null,
                  course_id bigint not null,
                  node_id varchar(80),
                  node_type varchar(30),
                  topic_id bigint,
                  user_answer text,
                  score int,
                  hit_keywords json,
                  miss_keywords json,
                  feedback text,
                  bond_delta int default 0,
                  duration int,
                  create_time datetime
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists ai_provider_config (
                  id bigint primary key auto_increment,
                  provider_code varchar(50) not null,
                  provider_name varchar(80) not null,
                  protocol varchar(30) not null,
                  base_url varchar(500),
                  api_key_encrypted varchar(1000),
                  default_model varchar(100),
                  support_stream tinyint default 1,
                  support_json tinyint default 1,
                  priority int default 100,
                  enabled tinyint default 1,
                  remark varchar(500),
                  create_time datetime,
                  update_time datetime
                )
                """);
    }

    public void upsertMaterial(StudyMaterial material, String contentHash, String promptVersion) {
        upsertCourse(material);
        jdbcTemplate.update("""
                insert into ai_lesson_material(course_id, content_hash, prompt_version, script_json, status, create_time, update_time)
                values (?, ?, ?, ?, 'generated', now(), now())
                on duplicate key update
                  script_json = values(script_json),
                  status = 'generated',
                  error_msg = null,
                  update_time = now()
                """,
                material.course().courseId(),
                contentHash,
                promptVersion,
                toJson(material)
        );
    }

    public Long createPendingMaterial(Long courseId, String promptVersion, String taskId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into ai_lesson_material(course_id, content_hash, prompt_version, script_json, status, error_msg, create_time, update_time)
                    values (?, ?, ?, null, 'pending', null, now(), now())
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, courseId);
            ps.setString(2, taskId);
            ps.setString(3, promptVersion);
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public void markMaterialGenerated(Long materialId, StudyMaterial material, String contentHash, String promptVersion) {
        upsertCourse(material);
        jdbcTemplate.update("""
                delete from ai_lesson_material
                where course_id = ? and content_hash = ? and prompt_version = ? and id <> ?
                """,
                material.course().courseId(),
                contentHash,
                promptVersion,
                materialId
        );
        jdbcTemplate.update("""
                update ai_lesson_material
                set course_id = ?, content_hash = ?, prompt_version = ?, script_json = ?, status = 'generated', error_msg = null, update_time = now()
                where id = ?
                """,
                material.course().courseId(),
                contentHash,
                promptVersion,
                toJson(material),
                materialId
        );
    }

    public void markMaterialFailed(Long materialId, String errorMsg) {
        jdbcTemplate.update("""
                update ai_lesson_material
                set status = 'failed', error_msg = ?, update_time = now()
                where id = ?
                """, truncateError(errorMsg), materialId);
    }

    public MaterialStatusRow getMaterialStatus(Long materialId) {
        try {
            return jdbcTemplate.queryForObject("""
                    select id, course_id, status, prompt_version, content_hash, error_msg
                    from ai_lesson_material
                    where id = ?
                    """, (rs, rowNum) -> new MaterialStatusRow(
                    rs.getLong("id"),
                    rs.getLong("course_id"),
                    rs.getString("status"),
                    rs.getString("prompt_version"),
                    rs.getString("content_hash"),
                    rs.getString("error_msg")
            ), materialId);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("课程材料状态不存在");
        }
    }

    private void upsertCourse(StudyMaterial material) {
        CourseSummary course = material.course();
        String tagsJson = toJson(course.tags());
        jdbcTemplate.update("""
                insert into ai_course(id, subject_id, title, description, difficulty, tags, topic_ids, enabled, create_time, update_time)
                values (?, 1, ?, ?, ?, ?, ?, 1, now(), now())
                on duplicate key update
                  title = values(title),
                  description = values(description),
                  difficulty = values(difficulty),
                  tags = values(tags),
                  topic_ids = values(topic_ids),
                  enabled = 1,
                  update_time = now()
                """,
                course.courseId(),
                course.title(),
                course.description(),
                course.difficulty(),
                tagsJson,
                toJson(material.homework().stream().map(StudyMaterial.HomeworkSeed::topicId).toList())
        );
    }

    public List<CourseSummary> listCourses() {
        return jdbcTemplate.query("""
                select c.id,
                       c.title,
                       c.description,
                       c.difficulty,
                       c.tags,
                       m.script_json
                from ai_course c
                left join ai_lesson_material m on m.id = (
                    select max(id) from ai_lesson_material mm where mm.course_id = c.id and mm.status = 'generated'
                )
                where c.enabled = 1
                order by c.id
                """, (rs, rowNum) -> {
            StudyMaterial material = readMaterial(rs.getString("script_json"));
            if (material != null) {
                return material.course();
            }
            return new CourseSummary(
                    rs.getLong("id"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("difficulty"),
                    readStringList(rs.getString("tags")),
                    0,
                    0,
                    "/assets/characters/teacher-teaching-transparent@2x.png",
                    "/assets/characters/baizi-happy-transparent@2x.png"
            );
        });
    }

    public StudyMaterial getMaterial(Long courseId) {
        try {
            String json = jdbcTemplate.queryForObject("""
                    select script_json
                    from ai_lesson_material
                    where course_id = ? and status = 'generated'
                    order by id desc
                    limit 1
                    """, String.class, courseId);
            StudyMaterial material = readMaterial(json);
            if (material == null) {
                throw new IllegalArgumentException("课程材料为空");
            }
            return material;
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("课程材料不存在，请先生成或导入题库");
        }
    }

    public Long createSession(Long userId, Long courseId, String mode) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into ai_study_session(user_id, course_id, status, current_node_index, current_homework_index, total_score, bond_value, weak_tags, start_time, create_time, update_time)
                    values (?, ?, 'learning', 0, 0, 0, 0, '[]', now(), now(), now())
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setLong(2, courseId);
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public SessionRow getSession(Long sessionId) {
        try {
            return jdbcTemplate.queryForObject("""
                    select id, user_id, course_id, status, current_node_index, current_homework_index, total_score, bond_value, start_time
                    from ai_study_session
                    where id = ?
                    """, (rs, rowNum) -> new SessionRow(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    rs.getLong("course_id"),
                    rs.getString("status"),
                    rs.getInt("current_node_index"),
                    rs.getInt("current_homework_index"),
                    rs.getInt("total_score"),
                    rs.getInt("bond_value"),
                    rs.getTimestamp("start_time").toLocalDateTime()
            ), sessionId);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("学习会话不存在，请先创建 session");
        }
    }

    public void updateCurrentNode(Long sessionId, int nextNodeIndex) {
        jdbcTemplate.update("update ai_study_session set current_node_index = ?, update_time = now() where id = ?", nextNodeIndex, sessionId);
    }

    public void addBond(Long sessionId, int bondDelta) {
        jdbcTemplate.update("update ai_study_session set bond_value = bond_value + ?, update_time = now() where id = ?", bondDelta, sessionId);
    }

    public void insertRecord(Long sessionId, Long userId, Long courseId, String nodeId, String nodeType, Long topicId,
                             String answer, int score, List<String> hits, List<String> misses, String feedback, int bondDelta) {
        jdbcTemplate.update("""
                insert into ai_study_record(session_id, user_id, course_id, node_id, node_type, topic_id, user_answer, score, hit_keywords, miss_keywords, feedback, bond_delta, create_time)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """,
                sessionId,
                userId,
                courseId,
                nodeId,
                nodeType,
                topicId,
                answer,
                score,
                toJson(hits),
                toJson(misses),
                feedback,
                bondDelta
        );
    }

    public List<RecordRow> listRecords(Long sessionId) {
        return jdbcTemplate.query("""
                select node_id, node_type, topic_id, score, hit_keywords, miss_keywords, feedback, bond_delta, create_time
                from ai_study_record
                where session_id = ?
                order by id
                """, (rs, rowNum) -> new RecordRow(
                rs.getString("node_id"),
                rs.getString("node_type"),
                rs.getLong("topic_id"),
                rs.getInt("score"),
                readStringList(rs.getString("hit_keywords")),
                readStringList(rs.getString("miss_keywords")),
                rs.getString("feedback"),
                rs.getInt("bond_delta"),
                rs.getTimestamp("create_time").toLocalDateTime()
        ), sessionId);
    }

    public List<RecordRow> listRecordsByUser(Long userId) {
        return jdbcTemplate.query("""
                select node_id, node_type, topic_id, score, hit_keywords, miss_keywords, feedback, bond_delta, create_time
                from ai_study_record
                where user_id = ?
                order by id desc
                """, (rs, rowNum) -> new RecordRow(
                rs.getString("node_id"),
                rs.getString("node_type"),
                rs.getLong("topic_id"),
                rs.getInt("score"),
                readStringList(rs.getString("hit_keywords")),
                readStringList(rs.getString("miss_keywords")),
                rs.getString("feedback"),
                rs.getInt("bond_delta"),
                rs.getTimestamp("create_time").toLocalDateTime()
        ), userId);
    }

    public int countRecordsByUser(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from ai_study_record where user_id = ? and node_type <> 'free_ask'",
                Integer.class,
                userId
        );
        return count == null ? 0 : count;
    }
    private StudyMaterial readMaterial(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, StudyMaterial.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("课程材料 JSON 解析失败", ex);
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(String.class).readValue(json);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON 序列化失败", ex);
        }
    }

    private String truncateError(String errorMsg) {
        if (errorMsg == null || errorMsg.isBlank()) {
            return "课程材料生成失败";
        }
        return errorMsg.length() > 1000 ? errorMsg.substring(0, 1000) : errorMsg;
    }

    public record SessionRow(
            Long sessionId,
            Long userId,
            Long courseId,
            String status,
            Integer currentNodeIndex,
            Integer currentHomeworkIndex,
            Integer totalScore,
            Integer bondValue,
            LocalDateTime startTime
    ) {
    }

    public record RecordRow(
            String nodeId,
            String nodeType,
            Long topicId,
            Integer score,
            List<String> hitKeywords,
            List<String> missKeywords,
            String feedback,
            Integer bondDelta,
            LocalDateTime createTime
    ) {
    }

    public record MaterialStatusRow(
            Long materialId,
            Long courseId,
            String status,
            String promptVersion,
            String contentHash,
            String errorMsg
    ) {
    }
}
