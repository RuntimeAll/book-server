-- ============================================================================
-- V23: PRD-A-015(题目结构化网格块模型) — biz_question_block 结构化网格块存储表
-- ----------------------------------------------------------------------------
-- 目的: 把(个人)题目内容从「线性 Markdown 单字符串」升级为「结构化网格题块 JSON」。
--   一行 = 一道题的整份 block 文档(question_id 为 PK, 一题一份)。
--   block_json 结构 = PRD-A-015 §10.1 锁定 schema(A-015 ∥ C-100 双分支共享接口):
--     { v:1, rows:[ { cells:[ <text|image|option block> ] } ] }
--   渲染优先级(§10.2): 有 block_json → 用它(权威); 否则回落现有富文本/图片。旧 biz_text_content 语义不动。
--
-- 🔴 A 线顺序号 V23(V22 已用; 权威约定见 book-ai/CLAUDE.md §3.1。PRD 原文写"V19+",
--    实际 A 线顺序号已推进到 V22, 故取下一个 V23)。纯新建表(CREATE TABLE, 无 DROP),
--    prod 直接 apply, dev 由 BE 重启 flyway 自动 apply。
-- 🔴 无 tenant_id 列 → application.yml 的 tenant.excludes 必加 biz_question_block
--    (同 biz_export_record/biz_variant_upload 单租户约定), 否则多租户拦截器注入
--    AND tenant_id 致 "Unknown column 'tenant_id'" 全 500。
-- 🔴 question_id 为逻辑 FK → biz_question.id(本工程惯例不建硬 FK 约束, 与 biz_export_record 一致)。
-- 🔴 跑此文件用 utf8mb4(中文 COMMENT)。
-- ============================================================================

CREATE TABLE biz_question_block (
  question_id  BIGINT        NOT NULL                  COMMENT '题目 id(逻辑 FK→biz_question.id; 一题一份 block 文档, 作 PK)',
  block_json   JSON          DEFAULT NULL              COMMENT 'block 文档 JSON(§10.1 schema: {v,rows:[{cells:[block]}]})',
  v            INT           DEFAULT 1                 COMMENT 'schema 版本号(当前 1)',
  update_by    BIGINT        DEFAULT NULL              COMMENT '最后保存者 sys_user.id(服务端 LoginHelper 强制)',
  create_time  DATETIME      DEFAULT NULL              COMMENT '创建时间',
  update_time  DATETIME      DEFAULT NULL              COMMENT '更新时间',

  PRIMARY KEY (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目结构化网格块存储(PRD-A-015)';

-- 验收(apply 后人工核对):
-- SHOW CREATE TABLE biz_question_block;
-- SELECT version, success FROM flyway_schema_history WHERE version='23';
