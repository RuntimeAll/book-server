-- V8__add_text_content_and_tag_knowledge.sql
-- PRD-B-006 收尾增量(用户 2026-06-04 拍板).
--
-- 两件事:
--   1. 题目三要素长文本"外置"到独立表 biz_text_content (题干/答案/解析对称, 一行一类型).
--      老 biz_question.stem_text 字段双写过渡保留, 本 V 不 DROP; biz_question 加 stem_text_content_id /
--      answer_text_content_id 两列做 FK; analyze_text_content_id 已存在(misikt 抓包字段)
--      直接复用当 explain(解析) 引用.
--   2. 标签 ↔ 知识点 关联表 biz_tag_knowledge: 基于共现关系(同一题被多挂哪些标签 + 哪些知识点)
--      逆推, 给 AI 编排/反向推荐用. 本 V 只建表, 数据走 V9 ETL 一次性灌库.

-- ============================================================
-- 1. 题目长文本外置表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_text_content (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    question_id  BIGINT       NOT NULL                              COMMENT '反查 biz_question.id',
    content_type CHAR(1)      NOT NULL                              COMMENT 'S=题干 / A=答案 / E=解析',
    content      MEDIUMTEXT                                         COMMENT '长文本内容(LaTeX 源 / 纯文本)',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP             COMMENT '创建时间',
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_question_type (question_id, content_type),
    KEY idx_question (question_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '题目三要素长文本外置(题干 S / 答案 A / 解析 E)';

-- ============================================================
-- 2. biz_question 加两列(analyze_text_content_id 已存在 当 explain 引用)
--    保留旧 stem_text 字段不 DROP, 双写过渡; 现有 29k 行 stem_text 在 V9 迁出.
--    注意: MySQL ALTER TABLE ADD COLUMN 没有 "IF NOT EXISTS" 的标准方言,
--    本 V 假设干净执行(每个 V 文件只跑一次, 跑过的不重跑 — 见 sql/README.md 朴素 Flyway 流程).
-- ============================================================
ALTER TABLE biz_question
    ADD COLUMN stem_text_content_id   BIGINT NULL COMMENT '题干文本外置 FK -> biz_text_content.id' AFTER stem_text,
    ADD COLUMN answer_text_content_id BIGINT NULL COMMENT '答案文本外置 FK -> biz_text_content.id' AFTER correct_answer;

-- ============================================================
-- 3. 标签 ↔ 知识点 共现关联表(数据走 V9 ETL)
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_tag_knowledge (
    tag_id       BIGINT       NOT NULL                              COMMENT '关联 biz_free_tag.id',
    knowledge_id VARCHAR(20)  NOT NULL                              COMMENT '关联 biz_subject.id(任意层级)',
    co_count     INT          NOT NULL DEFAULT 0                    COMMENT '该 tag 与该 knowledge 同时挂在多少道题上',
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (tag_id, knowledge_id),
    KEY idx_knowledge_count (knowledge_id, co_count DESC)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '标签↔知识点 共现关联(逆推自现有题目挂载, V9 ETL 灌)';
