-- ============================================================================
-- V16: PRD-C(teacher-copilot 探针) — biz_question 加 5 层维度打标字段 + 状态机 + 向量预留
-- ----------------------------------------------------------------------------
-- 来源: codeplace-C/teacher-copilot-ready/01-DDL.sql (2026-06-05 探针施工图) §1
-- 目的: 题库 5 层维度 AI 打标(①知识点 ②题型 ③思维方法 ④难度 ⑤图形/情境结构)
-- 状态: 🟡 留档(第2周 graph 落库前置)。手动跑, 非自动迁移。
--
-- 🔴 跑此文件必须显式 utf8mb4(同 V11), 否则中文 COMMENT 落库乱码:
--   mysql --default-character-set=utf8mb4 -uroot -pXXX miskt_data2 < V16__alter_biz_question_label_dims.sql
--
-- 🔴 与 V11(PRD-B-012)已加列的对齐 —— 避免 Duplicate column / 语义重复, 维护者务必 review:
--   • 分值: 复用 V11 的 base_score(DECIMAL(5,2)), 本文件【不再加 score】(01-DDL 原 score 列作废)。
--   • 标注状态: 本文件新增 label_status 作为【AI 打标专用工作流状态机】, 与 V11 的
--     annotate_status【语义分工、并存】:
--       - annotate_status (V11) = 标了多少维度(完整度: 0未标/1已标全/2部分)
--       - label_status    (本表) = AI 打标 → 人审 工作流状态(0未标/1AI已标/2已审核/3争议)
--     探针 graph 的 load(取未标)/persist(人审落库) 按 label_status 过滤。
--     ⚠️ 待决: 若维护者决定二者合并, 改这里 + teacher-copilot/app/clients/db.py 的 list_unlabeled SQL。
--   • ability_layer: 01-DDL 原列【本期不加】(与 dim3_skill 思维方法语义重叠, 最小集; 需要再补后续 V)。
--   • aux_tags(JSON 辅标签): 与 V12 biz_question_annotation 多值标注表【分工不重复】——
--     aux_tags = 半结构化轻量辅标(错因/情境/母题), annotation 表 = 受控词表多值标注。
-- ============================================================================

ALTER TABLE biz_question
  -- 5 层维度(主)
  ADD COLUMN dim1_kp_id      VARCHAR(64)  NULL COMMENT '①知识点 ID(关联现有知识点树)',
  ADD COLUMN dim2_qtype      TINYINT      NULL COMMENT '②题型 1选择/4填空/5解答/6证明',
  ADD COLUMN dim3_skill      JSON         NULL COMMENT '③思维方法数组 ["分类讨论","数形结合"]',
  ADD COLUMN dim4_difficulty TINYINT      NULL COMMENT '④难度 1-4',
  ADD COLUMN dim5_structure  VARCHAR(255) NULL COMMENT '⑤图形/情境结构指纹',
  -- 辅标签(半结构化, 与 biz_question_annotation 表分工)
  ADD COLUMN aux_tags        JSON         NULL COMMENT '辅标签:错因/情境/考查角度/母题',
  -- 向量预留位(本期不填, 字段先在; 将来上 Hybrid 检索无痛补)
  ADD COLUMN stem_embedding       BLOB        NULL COMMENT '题干向量预留, 第一期不填',
  ADD COLUMN embedding_model      VARCHAR(64) NULL COMMENT '生成向量的模型',
  ADD COLUMN embedding_updated_at DATETIME    NULL,
  -- AI 打标状态机(与 V11 annotate_status 分工, 见文件头)
  ADD COLUMN label_status     TINYINT      DEFAULT 0 COMMENT '0未标 1AI已标 2已审核 3争议',
  ADD COLUMN label_confidence DECIMAL(4,3) NULL COMMENT 'AI 自评置信度 0-1',
  ADD COLUMN labeled_by       VARCHAR(64)  NULL COMMENT 'AI 模型名或人员',
  ADD COLUMN labeled_at       DATETIME     NULL,
  ADD COLUMN reviewed_by      VARCHAR(64)  NULL,
  ADD COLUMN reviewed_at      DATETIME     NULL;

-- 索引(按高频查询路径)
ALTER TABLE biz_question
  ADD KEY idx_label_status   (label_status),
  ADD KEY idx_dim_qtype_diff (dim1_kp_id, dim2_qtype, dim4_difficulty),
  ADD KEY idx_dim5_structure (dim5_structure);

-- 🔴 FULLTEXT 全文索引【探针第一期不做, 已删】(维护者 2026-06-06 拍板 B 案):
--   原计划 ADD FULLTEXT ft_stem (stem) 做关键词召回, 但 (1) 本库题干主列实为 stem_text、
--   且长题干多外置 biz_text_content, 直接对 biz_question 建 FULLTEXT 覆盖不全;
--   (2) 探针价值在趟通目标技术栈, 关键词召回非探针必需。
--   将来真要"关键词召回"再单独立卡(届时定 ngram 目标列 = stem_text 还是联 biz_text_content)。

-- 验收(apply 后人工核对):
-- SHOW COLUMNS FROM biz_question WHERE Field LIKE 'dim%' OR Field LIKE 'label%';
-- SHOW INDEX FROM biz_question WHERE Key_name IN ('idx_label_status','idx_dim_qtype_diff','idx_dim5_structure');
