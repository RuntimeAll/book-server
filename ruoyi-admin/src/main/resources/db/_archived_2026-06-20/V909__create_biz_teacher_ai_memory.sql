-- ============================================================================
-- V909: PRD-C-100 — biz_teacher_ai_memory 老师全局 AI 记忆表
-- ----------------------------------------------------------------------------
-- 目的: 沉淀每个老师的偏好/纠正/习惯(常教年级、教材版本等), 供 toolkit 注入 prompt。
--   enabled=0 的记忆停用不注入; source 区分 自动(Python 写)/手填(老师维护页)。
-- 🔴 C 线预留段 V901+ 顺延 = V909(权威约定见 book-ai/CLAUDE.md §3.1, 号定不改)。
--    纯新建表(CREATE TABLE, 无 DROP), prod 部署直接 apply, dev 由 BE 重启 flyway 自动 apply。
--
-- 🔴 跑此文件用 utf8mb4(中文 COMMENT)。本表无 tenant_id 列(照 biz_question_note 风格)。
-- ============================================================================

CREATE TABLE biz_teacher_ai_memory (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  teacher_id   BIGINT        NOT NULL                          COMMENT '归属老师 user_id',
  mem_type     VARCHAR(16)   NOT NULL                          COMMENT '记忆类型: 偏好 / 纠正 / 习惯',
  mem_key      VARCHAR(64)   NOT NULL                          COMMENT '记忆键(如「常教年级」「教材版本」)',
  mem_value    VARCHAR(512)  NOT NULL                          COMMENT '记忆值',
  source       VARCHAR(8)    NOT NULL DEFAULT '手填'           COMMENT '来源: 自动 / 手填',
  enabled      TINYINT(1)    NOT NULL DEFAULT 1                 COMMENT '启停(0=停用, 停用不注入 prompt)',
  confidence   DECIMAL(4,3)  DEFAULT NULL                      COMMENT '置信度(自动写入时可带)',
  remark       VARCHAR(255)  DEFAULT NULL                      COMMENT '备注',
  create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  KEY idx_teacher_type (teacher_id, mem_type),
  KEY idx_teacher_enabled (teacher_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PRD-C-100 老师全局AI记忆';

-- 验收(apply 后人工核对):
-- SHOW CREATE TABLE biz_teacher_ai_memory;
-- SELECT version, success FROM flyway_schema_history WHERE version='909';
