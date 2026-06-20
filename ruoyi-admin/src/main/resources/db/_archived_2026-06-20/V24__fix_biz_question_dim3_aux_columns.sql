-- ============================================================================
-- V24: 修复 dev 库漂移 — biz_question 幂等补回 dim3_skill / aux_tags 两个 JSON 列
-- ----------------------------------------------------------------------------
-- 背景: V901(C 线打标维度) 在 flyway_schema_history 记为 success(NULL checksum baseline 行),
--   但本 dev 库手工应用 V901 时漏了 dim3_skill + aux_tags 两列 → flyway 不会重跑 V901 →
--   属性编辑页 / update-label 读写报 Unknown column 'dim3_skill'(PRD-A-015 批1 实测踩)。
-- 方案: A 线顺序号 V24, 幂等补列 —— 仅当列不存在才 ADD(top-level PREPARE 条件 DDL,
--   不用存储过程/DELIMITER, 规避 Flyway 解析坑)。dev(缺)→补齐; prod/B/C(V901 已真应用,列在)→ 跳过(DO 0)。
-- 纯增量, 无 DROP。列定义对齐 V901 原始(JSON NULL)。
-- ============================================================================

SET @ddl_dim3 := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_question' AND COLUMN_NAME = 'dim3_skill') = 0,
  'ALTER TABLE biz_question ADD COLUMN dim3_skill JSON NULL COMMENT ''③思维方法数组 ["分类讨论","数形结合"]''',
  'DO 0');
PREPARE s_dim3 FROM @ddl_dim3;
EXECUTE s_dim3;
DEALLOCATE PREPARE s_dim3;

SET @ddl_aux := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_question' AND COLUMN_NAME = 'aux_tags') = 0,
  'ALTER TABLE biz_question ADD COLUMN aux_tags JSON NULL COMMENT ''辅标签:错因/情境/考查角度/母题''',
  'DO 0');
PREPARE s_aux FROM @ddl_aux;
EXECUTE s_aux;
DEALLOCATE PREPARE s_aux;
