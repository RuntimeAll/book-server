-- C 线预留段 V913（来源字段字典化，2026-06-30）
-- 给 biz_question.source_type 列加专用字典 biz_question_source_type，前端来源文案/下拉统一走字典 SSOT，
-- 不再 editor.vue / attributes.vue 各硬编码一份 SOURCE_TYPE_LABELS / SOURCE_TYPE_OPTIONS。
-- 🔴 约定 = source_type 列既有约定（1中考真题/2模拟/3期末/4月考/5单元/6自编/9其他），
--    与另一个字典 biz_question_source(1教材/2质检/…) 是两套不同约定，别混。
-- dev 库（ai_lesson_prep）已于 2026-06-30 经 mysql MCP 先行 INSERT，本文件为 fresh/prod 复现。
-- DELETE→INSERT 保证幂等（重跑安全）。dict_id/dict_code 沿用本项目「显式主键」惯例（非自增）。
-- 🔴 RuoYi 字典走 Redis 缓存：本迁移直改 DB 后，须刷新字典缓存才生效（同 V912 list_class）——
--    部署重启 BE 会重载；或调 DELETE /system/dict/type/refreshCache。否则接口返旧缓存(listClass/新条目缺失)。
DELETE FROM sys_dict_data WHERE dict_type = 'biz_question_source_type';
DELETE FROM sys_dict_type WHERE dict_type = 'biz_question_source_type';

INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, remark)
VALUES (28, '000000', '题目来源(source_type)', 'biz_question_source_type', 103, 1, NOW(), 'source_type 列字典化');

INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time)
VALUES
  (197, '000000', 1, '中考真题', '1', 'biz_question_source_type', NULL, NULL, 'N', 103, 1, NOW()),
  (198, '000000', 2, '模拟',     '2', 'biz_question_source_type', NULL, NULL, 'N', 103, 1, NOW()),
  (199, '000000', 3, '期末',     '3', 'biz_question_source_type', NULL, NULL, 'N', 103, 1, NOW()),
  (200, '000000', 4, '月考',     '4', 'biz_question_source_type', NULL, NULL, 'N', 103, 1, NOW()),
  (201, '000000', 5, '单元',     '5', 'biz_question_source_type', NULL, NULL, 'N', 103, 1, NOW()),
  (202, '000000', 6, '自编',     '6', 'biz_question_source_type', NULL, NULL, 'N', 103, 1, NOW()),
  (203, '000000', 7, '其他',     '9', 'biz_question_source_type', NULL, NULL, 'N', 103, 1, NOW());
