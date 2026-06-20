-- ============================================================
-- V905 · 题库 schema 收敛 DDL 定稿（题目为核心 · 12→8 表）
-- 2026-06-12  库：miskt_data2 @ cuoti-mysql:3307
-- 上游：claude-code-sign/25-题库schema-收敛DDL.sql（北极星 + 决策）
-- 注：原计划号 V904 已被 V904__create_biz_variant_upload.sql 占用，本迁移落到 C 线预留段下一空号 V905。
-- ============================================================
-- 统一铁律：考点 = 知识点 = 同一套体系(biz_subject)，主/副只是 is_primary 标记，无独立"考点"字段。
--           考察类型(assessment_type=怎么考) != 考点(考什么)，前者住 ai 表，别混。
-- ============================================================
-- 数据处置：
--   保数据 ALTER ── biz_question / biz_question_knowledge（资产，不能重建）
--   不动    ────── biz_text_content / biz_question_free_tag / biz_free_tag / biz_subject（资产）
--   DROP重建 ──── biz_question_ai（412 行打标实验，举一反三写 0，可弃）
--   退役 DROP ──── biz_q_kp_tag(341 实验) / biz_question_secondary_kp(189,先并入) / biz_question_annotation(空)
-- ============================================================


-- ───────────── 1 · 主表瘦身（删 3 冗余列）─────────────
-- 同步改 ruoyi-book BizQuestion 实体/mapper/XML，删这 3 字段映射，本 clone mvn install 后合并 master
ALTER TABLE biz_question
  DROP COLUMN free_tag,      -- 标签逗号串 → 挂接表 biz_question_free_tag 才是真相
  DROP COLUMN dim3_skill,    -- 思维方法 → 砍维（A5，后期需要 V90x 加回）
  DROP COLUMN aux_tags;      -- 血缘审计 → 并入 ai 表 reasoning / 不单存
-- 保留：subject_id(科目锚 level1)、dim1_kp_id(主kp快照 level5)、dim2_qtype、dim4_difficulty、
--       dim5_structure、difficult、question_type、血缘(mother/variant)、打标元(label_*)、题面指针(*_content_id)


-- ───────────── 2 · 知识点关联加主/副标记 + 并入副 kp ─────────────
ALTER TABLE biz_question_knowledge
  ADD COLUMN is_primary TINYINT(1) NOT NULL DEFAULT 1
    COMMENT '1主考点/0副考点（同一知识体系 biz_subject，主副只此标记区分）';

-- 副 kp(secondary_kp 189) 并入 knowledge（is_primary=0），去重防撞
INSERT INTO biz_question_knowledge (question_id, knowledge_id, source, is_primary, create_time)
SELECT s.question_id, s.kp_id, 'U', 0, COALESCE(s.create_time, NOW())
FROM biz_question_secondary_kp s
WHERE NOT EXISTS (
  SELECT 1 FROM biz_question_knowledge k
  WHERE k.question_id = s.question_id AND k.knowledge_id = s.kp_id
);

DROP TABLE biz_question_secondary_kp;


-- ───────────── 3 · 退役标签实验表 + 空 EAV ─────────────
-- 标签 canonical = biz_question_free_tag(题级 29k live 资产)；q_kp_tag 是 341 题稀疏 AI 实验，
-- 「标签↔知识点」改用 free_tag join knowledge 派生，不留稀疏表。
DROP TABLE biz_q_kp_tag;
DROP TABLE biz_question_annotation;   -- 0 行空表；维度砍到几个、单值，不建 EAV，后期长起来再立


-- ───────────── 4 · 重建 biz_question_ai（48→16 列，只留 AI 专属）─────────────
-- 412 行打标实验弃（举一反三写 0 行，功能未上线）。
DROP TABLE biz_question_ai;
CREATE TABLE biz_question_ai (
  id                  BIGINT       PRIMARY KEY AUTO_INCREMENT,
  question_id         BIGINT       NOT NULL              COMMENT '源 biz_question.id',
  annotate_version    INT          NOT NULL DEFAULT 1    COMMENT '标注 schema 版本',

  -- 变式基因（生成用，主表装不下的重文本）
  solution_skeleton   MEDIUMTEXT   NULL                  COMMENT '解法骨架(运算序列,【】标最难步)',
  scenario            VARCHAR(64)  NULL                  COMMENT '场景(半开放,变式表皮必换项)',
  assessment_type     VARCHAR(32)  NULL                  COMMENT '考察类型(怎么考:计算/证明/应用…闭集10,!=考点)',

  -- 难点（难度 rubric 输入，代码重算个数，克制"宁空不凑"）
  hard_point_count    TINYINT      NULL                  COMMENT '难点个数(0=基础题)',
  breakthrough_points JSON         NULL                  COMMENT '突破点/难点(半开放)',

  -- 锚定溯源审计（debug 变式质量抓手）
  anchor_id           VARCHAR(20)  NULL                  COMMENT '锚定用的 subject 节点',
  confidence          DECIMAL(4,3) NULL                  COMMENT '锚定置信 0~1',
  need_anchor_review  TINYINT(1)   DEFAULT 0             COMMENT '锚定存疑待人审',
  reasoning           TEXT         NULL                  COMMENT 'agent 抽取/生成依据',

  -- 打标元
  label_status        TINYINT      DEFAULT 1             COMMENT '1AI已标 2已审 3争议',
  labeled_by          VARCHAR(64)  NULL,
  labeled_at          DATETIME     NULL,
  create_time         DATETIME     NULL,

  UNIQUE KEY uk_q_ver (question_id, annotate_version),
  INDEX idx_qid (question_id)
) COMMENT 'AI 派生(变式基因 + 难点 + 锚定审计；纯 AI 专属，主表已有的不重复)';

-- 砍掉的 ~32 列（后期需要纯增量 V90x 加回）：
--   主表已有重复：dim1_kp_id/dim2_qtype/dim4_difficulty/mother_question_id/variant_relation/base_score
--   题面三存：stem_text/answer_text/explain_text(→biz_text_content)
--   学术维(砍)：cognitive_level/core_competency/dim3_idea/dim3_method/content_domain/key_objects/
--             structure_fingerprint/key_points/exam_points/error_points/breakthrough_difficulty/kp_extend
--   副kp：dim1_secondary_kp_ids(→knowledge is_primary=0)
--   缓/审核流程：aux_tags/question_no/figure_img_url/conflict_flags/whitelist_size/reviewed_*/review_note/has_figure/figure_note
