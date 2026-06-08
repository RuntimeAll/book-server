-- V9__etl_text_content_and_tag_knowledge.sql
-- PRD-B-006 收尾增量(用户 2026-06-04 拍板) — V8 建表后的一次性 ETL.
-- 跑前提: V8 已应用.

-- ============================================================
-- 1. biz_question.stem_text(MEDIUMTEXT) 迁出 biz_text_content content_type='S'
--    跳过 NULL / 空串(dev 库实测约 29233 行非空).
--    UNIQUE KEY uk_question_type 保证幂等: 重跑会撞 unique, 但本 V 一次性, 不重跑.
-- ============================================================
INSERT INTO biz_text_content (question_id, content_type, content, create_time)
SELECT
    q.id,
    'S' AS content_type,
    q.stem_text,
    IFNULL(q.create_time, NOW()) AS create_time
FROM biz_question q
WHERE q.stem_text IS NOT NULL
  AND q.stem_text <> '';

-- ============================================================
-- 2. 回填 biz_question.stem_text_content_id (FK -> biz_text_content.id)
--    教师端查 detail 时优先用外置文本; biz_question.stem_text 保留双写兼容.
-- ============================================================
UPDATE biz_question q
JOIN biz_text_content tc
  ON tc.question_id = q.id
 AND tc.content_type = 'S'
SET q.stem_text_content_id = tc.id;

-- ============================================================
-- 3. 标签↔知识点 共现 ETL
--    ⚠️ 任务清单原本写 WHERE qk.source='S'(标准轨), 但 dev 库实测 biz_question_knowledge
--       全部为 source='U'(共 29545 行, S 轨 0 行).
--    => 改 source IN ('S','U') 兜底覆盖, 让 ETL 在 dev/prod 都能跑出有效数据.
--    => 灌库后行数 ≈ DISTINCT (tag_id, knowledge_id) 共现组合数.
-- ============================================================
INSERT INTO biz_tag_knowledge (tag_id, knowledge_id, co_count)
SELECT
    qft.tag_id,
    qk.knowledge_id,
    COUNT(DISTINCT qft.question_id) AS co_count
FROM biz_question_free_tag qft
JOIN biz_question_knowledge qk
  ON qk.question_id = qft.question_id
WHERE qk.source IN ('S', 'U')
GROUP BY qft.tag_id, qk.knowledge_id;
