-- PRD-B-012 T1: biz_question 加 11 列(实为 10 列) + 5 索引
-- 🔴 部署提示: 跑此文件必须显式 utf8mb4, 否则中文 COMMENT 落库会乱码
--   docker exec -i cuoti-mysql mysql -uroot -pXXX --default-character-set=utf8mb4 miskt_data2 < V11__alter_biz_question_schema_v1.sql
--   或 mysql --default-character-set=utf8mb4 -uroot -pXXX miskt_data2 < V11__alter_biz_question_schema_v1.sql

-- 维度: 分值/采集标记/导入溯源(source/batch/region/source_type)/命题血缘(mother/variant)/标注版本与状态
-- 与已有列分工:
--   base_score 与 biz_paper_question.score 精度对齐 (DECIMAL(5,2)), 卷内 score 仍优先 override
--   is_collected: 区分 misikt 老题(0, 可删) vs 自有化新题(1, 默认)
--   mother_question_id: 命题血缘(母题→变式), 与已有 repeat_question_id(完全去重) 语义分工
--   annotate_version + annotate_status: 标注维度扩展时 bump, 状态机 0未标 / 1已标全 / 2部分

ALTER TABLE biz_question
  ADD COLUMN base_score          DECIMAL(5,2) NULL     COMMENT '题自身标准分值, 与 biz_paper_question.score 精度对齐, 卷内 override 优先',
  ADD COLUMN is_collected        TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '0=misikt老题(可删) 1=自有化新题',
  ADD COLUMN import_source       VARCHAR(32)  NULL     DEFAULT 'manual' COMMENT 'manual/textin/misikt/my-clone',
  ADD COLUMN import_batch_id     VARCHAR(64)  NULL     COMMENT '录入批次(按卷)',
  ADD COLUMN region_code         VARCHAR(12)  NULL     COMMENT '国标行政区划码 330100=杭州 NULL=通用',
  ADD COLUMN source_type         TINYINT      NULL     COMMENT '1中考2模拟3期末4月考5单元6自编9其他',
  ADD COLUMN mother_question_id  BIGINT       NULL     COMMENT '母题指针 命题血缘自关联 (与 repeat_question_id 完全去重链语义分工)',
  ADD COLUMN variant_relation    VARCHAR(16)  NULL     COMMENT '数值变式/情境变式/结构变式/同源',
  ADD COLUMN annotate_version    INT          NOT NULL DEFAULT 0 COMMENT '标注 schema 版本(维度扩展时 bump)',
  ADD COLUMN annotate_status     TINYINT      NOT NULL DEFAULT 0 COMMENT '0未标 1已标全 2部分';

ALTER TABLE biz_question
  ADD KEY idx_is_collected (is_collected),
  ADD KEY idx_import_batch (import_batch_id),
  ADD KEY idx_region (region_code),
  ADD KEY idx_source_type (source_type),
  ADD KEY idx_mother (mother_question_id);
