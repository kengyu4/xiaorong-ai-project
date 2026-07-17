create database if not exists xiaorong_teacher
  default character set utf8mb4
  collate utf8mb4_unicode_ci;

use xiaorong_teacher;

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
);

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
);

create table if not exists ai_persona (
  id bigint primary key auto_increment,
  name varchar(50) not null,
  role varchar(30) not null,
  avatar_url varchar(500),
  tone varchar(200),
  persona_prompt text,
  boundary_rule text,
  enabled tinyint default 1,
  create_time datetime,
  update_time datetime
);

create table if not exists ai_user (
  id bigint primary key auto_increment,
  username varchar(50) not null,
  nickname varchar(50),
  password_hash varchar(120) not null,
  roles json not null,
  status varchar(20) not null default 'active',
  last_login_time datetime,
  create_time datetime,
  update_time datetime,
  unique key uk_ai_user_username (username)
);

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
);

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
);

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
);

create table if not exists ai_user_provider_secret (
  user_id bigint not null,
  provider_code varchar(64) not null,
  encrypted_api_key text not null,
  encryption_iv varchar(64) not null,
  key_version varchar(20) not null,
  api_key_last_four varchar(16) not null,
  create_time datetime not null,
  update_time datetime not null,
  primary key (user_id, provider_code),
  key idx_ai_user_provider_secret_provider (provider_code)
);

create table if not exists ai_user_ai_preference (
  user_id bigint not null primary key,
  provider_code varchar(64) not null,
  model varchar(200) not null,
  update_time datetime not null,
  key idx_ai_user_ai_preference_provider (provider_code)
);