-- ============================================================
-- V4 · freeTag 字典化抽离（X 卡 · 2026-05-22）
-- 目标库: miskt_data2
-- 背景: misikt 题目 free_tag 字段是逗号分隔字符串"X，Y，Z"，
--       原结构丢失了 tag 级别的复用 / 统计 / 颜色映射能力。
--       本卡抽离为字典表 + 关联表，按 misikt FE 真实规则保留 position
--       决定颜色（[blue, green, warning] 循环），不发明业务字典。
--
-- 改动:
--   1. 新建 biz_free_tag 字典表（unique name + use_count）
--   2. 新建 biz_question_free_tag 关联表（question_id × tag_id × position）
--   3. 原 biz_question.free_tag 字段保留（向下兼容）
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- 1. 字典表
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_free_tag (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  name          VARCHAR(64)  NOT NULL                 COMMENT 'tag 名（去重全局唯一）',
  use_count     INT          NOT NULL DEFAULT 0       COMMENT '引用次数（题数）',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目 freeTag 字典表（V4 / X 卡）';

-- ============================================================
-- 2. 关联表（题 × tag × position）
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_question_free_tag (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  question_id   BIGINT       NOT NULL                 COMMENT 'biz_question.id',
  tag_id        BIGINT       NOT NULL                 COMMENT 'biz_free_tag.id',
  position      TINYINT      NOT NULL                 COMMENT '出现位置 0/1/2/3/4 — 决定 FE 颜色',
  PRIMARY KEY (id),
  UNIQUE KEY uk_q_t (question_id, tag_id),
  KEY idx_q (question_id),
  KEY idx_t (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题 × freeTag 关联（V4 / X 卡）';
