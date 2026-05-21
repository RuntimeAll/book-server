-- ============================================================
-- V2 · ETL from raw_* to biz_* (B 卡子 PRD §2.2 阶段 2)
-- 目标库: miskt_data2 (本机 127.0.0.1:3307)
-- 数据流: raw_question_page / raw_question_knowledge / raw_paper / raw_paper_question → biz_*
-- 重入策略: INSERT IGNORE + 段 D/F 派生段先 DELETE 自己产物（不动 raw_* 不动 V1 seed）
--
-- 段 A: biz_question        ← raw_question_page              (~29422)
-- 段 B: biz_question_knowledge ← raw_question_knowledge      (~29529)
-- 段 C: biz_paper           ← raw_paper                      (~1592)
-- 段 D: biz_paper_section   派生 from raw_paper_question DISTINCT
-- 段 E: biz_paper_question  ← raw_paper_question (join 段 D)  (~33837)
-- 段 F: biz_subject 叶子    ← raw_question_knowledge DISTINCT (knowledge_id)
--
-- 用法（dev 本机）:
--   $env:MYSQL_PWD='123456'; cmd /c '"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" `
--     -h 127.0.0.1 -P 3307 -u root --default-character-set=utf8mb4 miskt_data2 `
--     < D:\workplace\book-ai\codeSpace\book-server\sql\V2__etl_from_raw.sql' 2>&1
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
SET SESSION group_concat_max_len = 1000000;

-- ============================================================
-- 段 A · biz_question  ← raw_question_page
-- 映射规则:
--   raw.id              → biz.id                (直传)
--   raw.question_type   → biz.question_type     (INT → TINYINT, 1/4/5)
--   raw.difficult       → biz.difficult         (INT → TINYINT, 1-4)
--   raw.subject_id      → biz.subject_id        (NULL 兜底 '3071' 浙教数学)
--   raw.short_title     → biz.short_title
--   raw.stem_text       → biz.stem_text
--   raw.stem_img        → biz.stem_img_url      (raw 无 _url 后缀)
--   raw.answer_img      → biz.answer_img_url
--   raw.explain_img     → biz.explain_img_url
--   raw.file_bin        → biz.file_bin_url
--   raw.correct         → biz.correct_answer
--   raw.score_std       → biz.score_std_json    (raw LONGTEXT, biz JSON, 直接接收, 若非合法 JSON 入库会报错 → 用 NULLIF + JSON 验证)
--   raw.exam_year       → biz.exam_year
--   raw.exam_paper_id   → biz.exam_paper_id
--   raw.exam_paper_name → biz.exam_paper_name
--   raw.free_tag        → biz.free_tag
--   raw.is_share        → biz.is_share          (INT → TINYINT, 0/1)
--   raw.is_repeat       → biz.is_repeat         (TINYINT → TINYINT)
--   raw.repeat_question_id → biz.repeat_question_id
--   raw.status (INT=1)  → biz.status '1' (CHAR(1) 发布)
--   raw.create_user     → biz.create_user       (BIGINT 透传)
--   raw.create_time     → biz.create_time       (VARCHAR 'YYYY-MM-DD' → DATETIME via STR_TO_DATE)
-- raw 无 biz 字段: video_url / analyze_text_content_id / question_std_knowledge_str / dedup_kind
--   → INSERT 列表里跳过，让 biz 默认值兜底 (NULL)
-- ============================================================

INSERT IGNORE INTO biz_question (
  id, question_type, difficult, subject_id,
  short_title, stem_text, stem_img_url, answer_img_url, explain_img_url, file_bin_url,
  correct_answer, score_std_json,
  exam_year, exam_paper_id, exam_paper_name,
  free_tag,
  is_share, is_repeat, repeat_question_id,
  status,
  create_by, create_user, create_time
)
SELECT
  id,
  CAST(question_type AS UNSIGNED),
  CAST(difficult AS UNSIGNED),
  COALESCE(NULLIF(subject_id, ''), '3071'),
  short_title, stem_text, stem_img, answer_img, explain_img, file_bin,
  correct,
  -- score_std 可能为 NULL / 空串 / 非 JSON 文本 → NULL 化兜底，JSON_VALID 校验
  CASE
    WHEN score_std IS NULL OR score_std = '' THEN NULL
    WHEN JSON_VALID(score_std) THEN score_std
    ELSE NULL
  END,
  exam_year, exam_paper_id, exam_paper_name,
  free_tag,
  CAST(COALESCE(is_share, 0) AS UNSIGNED),
  CAST(COALESCE(is_repeat, 0) AS UNSIGNED),
  repeat_question_id,
  '1',  -- raw.status INT 全 1 → biz CHAR(1) '1' 发布
  CAST(create_user AS CHAR),  -- biz.create_by VARCHAR(64) 走若依风格
  create_user,
  STR_TO_DATE(create_time, '%Y-%m-%d')
FROM raw_question_page;

-- ============================================================
-- 段 B · biz_question_knowledge  ← raw_question_knowledge
-- 映射规则:
--   raw.question_id    → biz.question_id
--   raw.knowledge_id   → biz.knowledge_id
--   raw.source         → biz.source           (CHAR(1) 'U'/'S')
--   raw 无 create_time → biz.create_time = NOW()
-- raw.id 不传 (biz.id auto_increment 重新生成, 复合 UNIQUE 防重 uk_qk_src)
-- ============================================================

INSERT IGNORE INTO biz_question_knowledge (question_id, knowledge_id, source, create_time)
SELECT question_id, knowledge_id, source, NOW()
FROM raw_question_knowledge;

-- ============================================================
-- 段 C · biz_paper  ← raw_paper
-- 映射规则:
--   raw.id              → biz.id
--   raw.name            → biz.name
--   raw.subject_id      → biz.subject_id
--   raw.directory_name  → biz.directory_name
--   raw.question_count  → biz.question_count
--   raw.score (INT)     → biz.score (DECIMAL(6,2), 直接收, MySQL 隐式转)
--   raw.suggest_time (VARCHAR) → biz.suggest_time INT (CAST UNSIGNED)
--   raw.hg_score (INT)  → biz.hg_score DECIMAL
--   raw.paper_type (INT)→ biz.paper_type TINYINT
--   raw.frame_text_content_id → biz.frame_text_content_id
--   raw.exam_year       → biz.exam_year
--   raw.status (INT=1)  → biz.status '1' 发布
--   raw.sort (BIGINT)   → biz.sort INT (CAST SIGNED, 注意溢出 — 看实样在 INT 范围)
--   raw.create_user (BIGINT) → biz.create_by VARCHAR(64) (转字符串)
--   raw.create_time (VARCHAR YYYY-MM-DD) → biz.create_time DATETIME (STR_TO_DATE)
-- raw 无 biz 字段: paper_category_id (V1 seed 11 行分类树, 暂不分配 → NULL 兜底)
--                  update_by / update_time / remark / is_share (default 0 兜底)
-- ============================================================

INSERT IGNORE INTO biz_paper (
  id, name, subject_id, directory_name, question_count,
  score, suggest_time, hg_score, paper_type,
  frame_text_content_id, exam_year,
  status, sort,
  create_by, create_time
)
SELECT
  id, name, COALESCE(NULLIF(subject_id, ''), '3071'), directory_name, COALESCE(question_count, 0),
  COALESCE(score, 0),
  CAST(NULLIF(suggest_time, '') AS UNSIGNED),
  hg_score,
  CAST(COALESCE(paper_type, 1) AS UNSIGNED),
  frame_text_content_id, exam_year,
  '1',
  CAST(COALESCE(sort, 0) AS SIGNED),
  CAST(create_user AS CHAR),
  STR_TO_DATE(create_time, '%Y-%m-%d')
FROM raw_paper;

-- ============================================================
-- 段 D · biz_paper_section  派生 from raw_paper_question
-- 派生逻辑:
--   raw_paper_question(paper_id, section_sort, section_title) DISTINCT
--   → biz_paper_section(paper_id, sort=section_sort, title=section_title)
--   id 自增, biz_paper_section UNIQUE(paper_id, sort) 防重
--
-- 重入策略: 先清自己产物 (V2 可重跑)
--   注意: V2 的 DELETE 是合法（脚本一致性）— 不属 sql-migration §7 "MCP DELETE 锁死" 范畴
-- ============================================================

DELETE FROM biz_paper_section;

INSERT IGNORE INTO biz_paper_section (paper_id, title, sort)
SELECT paper_id, COALESCE(NULLIF(section_title, ''), CONCAT('分组', section_sort)), section_sort
FROM raw_paper_question
GROUP BY paper_id, section_sort, section_title;

-- ============================================================
-- 段 E · biz_paper_question  ← raw_paper_question
-- 映射规则:
--   raw.paper_id        → biz.paper_id
--   raw.section_sort    → JOIN biz_paper_section ON (paper_id, sort) → 取 section_id
--   raw.question_id     → biz.question_id
--   raw.sort            → biz.sort
--   raw.score (INT)     → biz.score DECIMAL(5,2)
-- raw.id 不传 (biz.id auto_increment)
-- biz_paper_question UNIQUE(section_id, sort) 防重 → INSERT IGNORE
-- ============================================================

INSERT IGNORE INTO biz_paper_question (paper_id, section_id, question_id, sort, score)
SELECT
  rpq.paper_id,
  bps.id,
  rpq.question_id,
  rpq.sort,
  COALESCE(rpq.score, 0)
FROM raw_paper_question rpq
JOIN biz_paper_section bps
  ON bps.paper_id = rpq.paper_id
 AND bps.sort = rpq.section_sort;

-- ============================================================
-- 段 F · biz_subject 叶子  ← raw_question_knowledge.knowledge_id DISTINCT
-- 反推规则:
--   knowledge_id 长度分布: 4=学科(L1)/7=教材(L2)/10=章(L3)/13=节(L4)/16=知识点(L5)
--   parent_id = SUBSTRING(id, 1, LENGTH(id)-3)  (除非 L1 学段, parent='0')
--   name      = knowledge_name (raw 只对叶子有真名)
--   level     = LENGTH(id) DIV 3 - 1 → 1/2/3/4/5
--
-- 重入策略: 用 INSERT IGNORE (PK=id, V1 seed 9 行 4/7 位的浙教数学保留)
-- ============================================================

INSERT IGNORE INTO biz_subject (id, parent_id, name, level, sort, status, create_by, create_time)
SELECT
  knowledge_id AS id,
  CASE
    WHEN LENGTH(knowledge_id) <= 4 THEN '0'
    ELSE SUBSTRING(knowledge_id, 1, LENGTH(knowledge_id) - 3)
  END AS parent_id,
  COALESCE(NULLIF(MAX(knowledge_name), ''), CONCAT('节点 ', knowledge_id)) AS name,
  -- LENGTH 4→L1, 7→L2, 10→L3, 13→L4, 16→L5
  CASE LENGTH(knowledge_id)
    WHEN 4  THEN 1
    WHEN 7  THEN 2
    WHEN 10 THEN 3
    WHEN 13 THEN 4
    WHEN 16 THEN 5
    ELSE 5
  END AS level,
  0 AS sort,
  '0' AS status,
  'etl' AS create_by,
  NOW() AS create_time
FROM raw_question_knowledge
GROUP BY knowledge_id;

-- ============================================================
-- 段 F.2 · biz_subject 中间层骨架补全
-- 问题: 上面 INSERT 只灌了 raw_question_knowledge 实际出现的 knowledge_id
--      如果 13/16 位的叶子存在但 10 位的 parent 不在 raw 里 → 树断层
-- 兜底: 对每个已落的叶子, 反推父链, 缺失则补占位节点 (name = '节点 ' + id)
-- 实现: 递归 4 层 (16→13→10→7→4), 每层 INSERT IGNORE 父 id (从 child id 截短)
-- ============================================================

-- L5 (16位) 推 L4 (13位)
INSERT IGNORE INTO biz_subject (id, parent_id, name, level, sort, status, create_by, create_time)
SELECT DISTINCT
  SUBSTRING(id, 1, 13) AS id,
  SUBSTRING(id, 1, 10) AS parent_id,
  CONCAT('节点 ', SUBSTRING(id, 1, 13)) AS name,
  4 AS level,
  0, '0', 'etl-skeleton', NOW()
FROM biz_subject
WHERE LENGTH(id) = 16;

-- L4 (13位) 推 L3 (10位)
INSERT IGNORE INTO biz_subject (id, parent_id, name, level, sort, status, create_by, create_time)
SELECT DISTINCT
  SUBSTRING(id, 1, 10) AS id,
  SUBSTRING(id, 1, 7) AS parent_id,
  CONCAT('节点 ', SUBSTRING(id, 1, 10)) AS name,
  3 AS level,
  0, '0', 'etl-skeleton', NOW()
FROM biz_subject
WHERE LENGTH(id) = 13;

-- L3 (10位) 推 L2 (7位)
INSERT IGNORE INTO biz_subject (id, parent_id, name, level, sort, status, create_by, create_time)
SELECT DISTINCT
  SUBSTRING(id, 1, 7) AS id,
  SUBSTRING(id, 1, 4) AS parent_id,
  CONCAT('节点 ', SUBSTRING(id, 1, 7)) AS name,
  2 AS level,
  0, '0', 'etl-skeleton', NOW()
FROM biz_subject
WHERE LENGTH(id) = 10;

-- L2 (7位) 推 L1 (4位) — V1 seed 已经包含 3071 浙教数学, INSERT IGNORE 防覆盖
INSERT IGNORE INTO biz_subject (id, parent_id, name, level, sort, status, create_by, create_time)
SELECT DISTINCT
  SUBSTRING(id, 1, 4) AS id,
  '0' AS parent_id,
  CONCAT('节点 ', SUBSTRING(id, 1, 4)) AS name,
  1 AS level,
  0, '0', 'etl-skeleton', NOW()
FROM biz_subject
WHERE LENGTH(id) = 7;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 验数 (跑完后用 mysql MCP 验, 不在本脚本里):
--   SELECT COUNT(*) FROM biz_question;            -- 期望 ≥ 23000 (实际 29422)
--   SELECT COUNT(*) FROM biz_question_knowledge;  -- 期望 ≥ 29000 (实际 29529)
--   SELECT COUNT(*) FROM biz_paper;               -- 期望 ≥ 1500  (实际 1592)
--   SELECT COUNT(*) FROM biz_paper_question;      -- 期望 ≥ 34000 (实际 33837 — 略低, 注: raw 真数据 33837)
--   SELECT COUNT(*) FROM biz_paper_section;       -- 期望 > 0
--   SELECT COUNT(*) FROM biz_subject;             -- 期望 ≥ 9 (V1 seed) + L1-L5 叶子 + 中间层骨架
-- ============================================================
