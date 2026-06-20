-- ============================================================================
-- V910: PRD-C-100 — biz_dna_edit_log 经验层留痕表(只累计不消费)
-- ----------------------------------------------------------------------------
-- 目的: 留痕老师对母题/变式的 DNA 维度修改 + 图修正提示词, 当前只写不消费,
--   后续做经验沉淀/再训练的原料。target_id 可为母题/变式 id 或 thread_id。
-- 🔴 C 线预留段 V901+ 顺延 = V910(权威约定见 book-ai/CLAUDE.md §3.1, 号定不改)。
--    纯新建表(CREATE TABLE, 无 DROP), prod 部署直接 apply, dev 由 BE 重启 flyway 自动 apply。
--
-- 🔴 跑此文件用 utf8mb4(中文 COMMENT)。本表无 tenant_id 列(照 biz_question_note 风格)。
-- ============================================================================

CREATE TABLE biz_dna_edit_log (
  id                  BIGINT        NOT NULL AUTO_INCREMENT,
  teacher_id          BIGINT        NOT NULL                   COMMENT '操作老师 user_id',
  target_id           VARCHAR(64)   DEFAULT NULL               COMMENT '母题/变式 id 或 thread_id',
  edit_kind           VARCHAR(16)   NOT NULL                   COMMENT '编辑类型: dna维 / 图修正',
  dim                 VARCHAR(32)   DEFAULT NULL               COMMENT '改的哪一维(dna维时)',
  before_val          TEXT          DEFAULT NULL               COMMENT '改前值',
  after_val           TEXT          DEFAULT NULL               COMMENT '改后值',
  correction_prompt   TEXT          DEFAULT NULL               COMMENT '图修正提示词',
  committed           TINYINT(1)    NOT NULL DEFAULT 0          COMMENT '是否入库(1=已入库)',
  created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  KEY idx_teacher_created (teacher_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PRD-C-100 经验层留痕(只累计不消费)';

-- 验收(apply 后人工核对):
-- SHOW CREATE TABLE biz_dna_edit_log;
-- SELECT version, success FROM flyway_schema_history WHERE version='910';
