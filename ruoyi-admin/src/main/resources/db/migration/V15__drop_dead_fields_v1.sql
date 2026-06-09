-- ============================================================================
-- V15: PRD-B-013 N1 schema 减法 v1 — 4 表 × 18 字段 DROP COLUMN
-- ----------------------------------------------------------------------------
-- 背景:
--   2026-06-05 主对话 4 波拍板 (line-b-dev 编排), 砍 misikt 复刻期残留死字段 +
--   错设计字段 (is_share 4 处同款 / options_json+correct_answer admin 错用 /
--   半数 0 数据字段)。详见 PRD-B-013 PRD.md §scope.A
--
-- 范围: 4 表 × 18 列
--   biz_question:           10 列 (short_title/video_url/question_std_knowledge_str/
--                                  dedup_kind/is_share/is_repeat/repeat_question_id/
--                                  options_json/correct_answer/score_std_json)
--   biz_paper:               4 列 (directory_name/hg_score/frame_text_content_id/is_share)
--   biz_paper_category:      1 列 (is_share)
--   biz_subject:             3 列 (knowledge_img/knowledge_video/is_share)
--
-- 不动列 (PRD §scope.A 铁则): remark / biz_subject.status / B-012 加的 10 列
-- 不动表: biz_exam_paper_import / biz_text_content 派生表 / AI 主线 biz_ts_*
--
-- DDL 策略: MySQL 8 instant DDL, 一表一句多列 DROP COLUMN, 零锁表
-- 数据影响: 不可逆 — 18 字段数据全丢
--   (PRD §scope.A 评估: 全空字段 + 31 行 mock 数据丢, 影响可控)
--
-- 部署顺序 (跟 B-012 加列方向相反, 见 PRD §备注):
--   🔴 三线 BE 先全部 deploy 新代码 (entity 已无字段)  → DB 后跑 V15 DROP
--   反序 (DB 先 BE 后) = 旧 BE SELECT 列不存在 → 全表查询 500
--
-- 回滚: 同目录 rollback.sql 仅恢复结构, 数据无法恢复
-- ============================================================================

-- biz_question: 删 10 字段
ALTER TABLE biz_question
  DROP COLUMN short_title,
  DROP COLUMN video_url,
  DROP COLUMN question_std_knowledge_str,
  DROP COLUMN dedup_kind,
  DROP COLUMN is_share,
  DROP COLUMN is_repeat,
  DROP COLUMN repeat_question_id,
  DROP COLUMN options_json,
  DROP COLUMN correct_answer,
  DROP COLUMN score_std_json;

-- biz_paper: 删 4 字段
ALTER TABLE biz_paper
  DROP COLUMN directory_name,
  DROP COLUMN hg_score,
  DROP COLUMN frame_text_content_id,
  DROP COLUMN is_share;

-- biz_paper_category: 删 1 字段
ALTER TABLE biz_paper_category
  DROP COLUMN is_share;

-- biz_subject: 删 3 字段
ALTER TABLE biz_subject
  DROP COLUMN knowledge_img,
  DROP COLUMN knowledge_video,
  DROP COLUMN is_share;

-- 验收 (执行后人工核对, 详见 PRD §gates.G1):
-- SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
-- WHERE TABLE_SCHEMA=DATABASE()
--   AND TABLE_NAME IN ('biz_question','biz_paper','biz_paper_category','biz_subject')
--   AND COLUMN_NAME IN (
--     'short_title','video_url','question_std_knowledge_str','dedup_kind','is_share',
--     'is_repeat','repeat_question_id','options_json','correct_answer','score_std_json',
--     'directory_name','hg_score','frame_text_content_id','knowledge_img','knowledge_video'
--   );
-- 期望: 0 行
--
-- SELECT COUNT(*) FROM information_schema.COLUMNS
-- WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='biz_question' AND COLUMN_NAME='base_score';
-- 期望: 1 (B-012 新加列保留)
