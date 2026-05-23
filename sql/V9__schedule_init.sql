-- =====================================================================
-- V9__schedule_init.sql
-- 主线 C 老师排课 Agent · 4 表初始化（master-ai 分支）
-- =====================================================================
-- 表前缀 biz_ts_  (teacher schedule) — 与 misikt biz_question/biz_paper 隔离
-- schema 复用 miskt_data2 / user 体系复用 sys_user（teacher001 即可）
-- 版本 V9 — 跳号避撞 master 的 V1-V6 + 给 admin V7/V8 留位
-- =====================================================================

-- 1. 老师基点（全局基点 — 一个 user_id 一条记录 / 当日基点暂不持久化 MVP 可放内存）
DROP TABLE IF EXISTS `biz_ts_base`;
CREATE TABLE `biz_ts_base` (
    `id`                bigint(20)    NOT NULL                COMMENT '主键 id (snowflake 19 位 / VO 必须 Long)',
    `user_id`           bigint(20)    NOT NULL                COMMENT '老师 user_id (sys_user.user_id)',
    `address_text`      varchar(255)  NOT NULL                COMMENT '地址原文（用户输入）',
    `lng`               decimal(10,6) DEFAULT NULL            COMMENT '经度（高德 geocode）',
    `lat`               decimal(10,6) DEFAULT NULL            COMMENT '纬度（高德 geocode）',
    `formatted_address` varchar(255)  DEFAULT NULL            COMMENT '高德格式化地址',
    `address_level`     varchar(32)   DEFAULT NULL            COMMENT '高德地址级别（兴趣点/区/市等）',
    `create_by`         bigint(20)    DEFAULT NULL            COMMENT '创建人 user_id',
    `create_time`       datetime      DEFAULT NULL            COMMENT '创建时间',
    `update_time`       datetime      DEFAULT NULL            COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='老师全局基点 (C 主线 / MVP)';


-- 2. 课程节（单天 / 单节）
DROP TABLE IF EXISTS `biz_ts_lesson`;
CREATE TABLE `biz_ts_lesson` (
    `id`             bigint(20)    NOT NULL                COMMENT '主键 id',
    `user_id`        bigint(20)    NOT NULL                COMMENT '老师 user_id',
    `lesson_date`    date          NOT NULL                COMMENT '日期 YYYY-MM-DD',
    `start_time`     time          NOT NULL                COMMENT '开始时间 HH:MM:SS',
    `duration_min`   int(11)       NOT NULL                COMMENT '持续分钟数',
    `location_text`  varchar(255)  NOT NULL                COMMENT '地点原文',
    `lng`            decimal(10,6) DEFAULT NULL            COMMENT '经度（高德 geocode）',
    `lat`            decimal(10,6) DEFAULT NULL            COMMENT '纬度（高德 geocode）',
    `lesson_name`    varchar(255)  DEFAULT NULL            COMMENT '课程名称（自由文本：人/学科/时间/地点）',
    `create_by`      bigint(20)    DEFAULT NULL            COMMENT '创建人 user_id',
    `create_time`    datetime      DEFAULT NULL            COMMENT '创建时间',
    `update_time`    datetime      DEFAULT NULL            COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_date` (`user_id`, `lesson_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程节 (C 主线 / MVP 单天单节)';


-- 3. AI 对话会话（多轮追问的容器 / round_count 上限 3）
DROP TABLE IF EXISTS `biz_ts_chat_session`;
CREATE TABLE `biz_ts_chat_session` (
    `id`           bigint(20)   NOT NULL                  COMMENT '主键 id',
    `user_id`      bigint(20)   NOT NULL                  COMMENT '老师 user_id',
    `status`       varchar(16)  NOT NULL DEFAULT 'active' COMMENT '会话状态 active / finished / cancelled',
    `round_count`  int(11)      NOT NULL DEFAULT 0        COMMENT '已追问轮数（MVP 上限 3）',
    `create_by`    bigint(20)   DEFAULT NULL              COMMENT '创建人 user_id',
    `create_time`  datetime     DEFAULT NULL              COMMENT '创建时间',
    `update_time`  datetime     DEFAULT NULL              COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 对话会话 (C 主线 / MVP 多轮追问)';


-- 4. AI 对话消息（user / assistant / tool 多种 role）
DROP TABLE IF EXISTS `biz_ts_chat_message`;
CREATE TABLE `biz_ts_chat_message` (
    `id`            bigint(20)  NOT NULL                  COMMENT '主键 id',
    `session_id`    bigint(20)  NOT NULL                  COMMENT '所属会话 id',
    `role`          varchar(16) NOT NULL                  COMMENT '角色 user / assistant / tool',
    `content`       text                                  COMMENT 'user/assistant 文本内容',
    `tool_use`      text                                  COMMENT 'assistant tool_use block JSON (extract_lessons 入参)',
    `tool_result`   text                                  COMMENT 'tool result block JSON',
    `create_time`   datetime    DEFAULT NULL              COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 对话消息 (C 主线 / MVP)';

-- =====================================================================
-- 用法说明
-- =====================================================================
-- 跑法 A（用户手动）：
--   mysql -u root -p miskt_data2 < V9__schedule_init.sql
--
-- 跑法 B（dev MCP 一行一行执行）：
--   按 CREATE/DROP 分块跑 mysql_query
--
-- 验证：
--   SHOW TABLES LIKE 'biz_ts_%';   -- 应返回 4 行
--   DESC biz_ts_base;
-- =====================================================================
