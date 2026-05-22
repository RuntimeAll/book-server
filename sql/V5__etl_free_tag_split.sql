-- ============================================================
-- V5 · freeTag ETL split（X 卡 · 2026-05-22）
-- 源: miskt_data2.biz_question.free_tag（"X，Y，Z" 中文逗号分隔，最多 5 项）
-- 目标:
--   1. biz_free_tag — 全局唯一 tag 字典（INSERT IGNORE 幂等）
--   2. biz_question_free_tag — 题 × tag × position 关联（INSERT IGNORE 幂等）
--   3. biz_free_tag.use_count — 回写引用次数（UPDATE）
--
-- 幂等说明: 重跑安全 — uk_name + uk_q_t 拦截重复
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- 1. 填字典表 — DISTINCT 后 INSERT IGNORE
-- ============================================================
INSERT IGNORE INTO biz_free_tag (name)
SELECT DISTINCT TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(q.free_tag, '，', n.idx), '，', -1)) AS name
FROM biz_question q
JOIN (
  SELECT 1 AS idx UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
) n
  ON n.idx <= CHAR_LENGTH(q.free_tag) - CHAR_LENGTH(REPLACE(q.free_tag, '，', '')) + 1
WHERE q.free_tag IS NOT NULL AND q.free_tag != ''
HAVING name != '' AND name IS NOT NULL;

-- ============================================================
-- 2. 填关联表 — JOIN 字典 + 还原 position
-- ============================================================
INSERT IGNORE INTO biz_question_free_tag (question_id, tag_id, position)
SELECT
  q.id                            AS question_id,
  t.id                            AS tag_id,
  n.idx - 1                       AS position
FROM biz_question q
JOIN (
  SELECT 1 AS idx UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
) n
  ON n.idx <= CHAR_LENGTH(q.free_tag) - CHAR_LENGTH(REPLACE(q.free_tag, '，', '')) + 1
JOIN biz_free_tag t
  ON t.name = TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(q.free_tag, '，', n.idx), '，', -1))
WHERE q.free_tag IS NOT NULL AND q.free_tag != '';

-- ============================================================
-- 3. 回写 biz_free_tag.use_count（覆盖式，重跑安全）
-- ============================================================
UPDATE biz_free_tag t
LEFT JOIN (
  SELECT tag_id, COUNT(*) AS cnt FROM biz_question_free_tag GROUP BY tag_id
) c ON c.tag_id = t.id
SET t.use_count = COALESCE(c.cnt, 0);
