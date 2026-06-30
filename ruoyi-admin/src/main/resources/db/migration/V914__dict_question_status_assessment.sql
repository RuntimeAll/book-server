-- C 线预留段 V914（题目状态/考察类型魔法值字典化，2026-06-30）
-- 把 label_status / annotate_status / assessment_type(dim2) 三组前端硬编码枚举收进字典 SSOT，
-- attributes.vue / question/index.vue 改读 useDictStore，不再各写一份。
-- 🔴 RuoYi 字典走 Redis 缓存：直改 DB 后须刷新缓存才生效（部署重启 BE，或调
--    DELETE /system/dict/type/refreshCache）。dev 库已经 mysql MCP 先行 INSERT + refreshCache。
-- 幂等 DELETE→INSERT；dict_id/dict_code 沿用本项目「显式主键」惯例。
DELETE FROM sys_dict_data WHERE dict_type IN ('biz_question_label_status','biz_question_annotate_status','biz_question_assessment_type');
DELETE FROM sys_dict_type WHERE dict_type IN ('biz_question_label_status','biz_question_annotate_status','biz_question_assessment_type');

INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, remark) VALUES
  (29, '000000', '题目打标状态(label_status)',    'biz_question_label_status',    103, 1, NOW(), '魔法值字典化'),
  (30, '000000', '题目标注完成度(annotate_status)','biz_question_annotate_status', 103, 1, NOW(), '魔法值字典化'),
  (31, '000000', '题目考察类型(dim2)',            'biz_question_assessment_type', 103, 1, NOW(), '魔法值字典化');

-- label_status（0未标/1AI已标/2已审核/3争议）+ list_class 徽标色
INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time)
VALUES
  (204, '000000', 1, '未标',   '0', 'biz_question_label_status', NULL, 'info',    'N', 103, 1, NOW()),
  (205, '000000', 2, 'AI已标', '1', 'biz_question_label_status', NULL, 'primary', 'N', 103, 1, NOW()),
  (206, '000000', 3, '已审核', '2', 'biz_question_label_status', NULL, 'success', 'N', 103, 1, NOW()),
  (207, '000000', 4, '争议',   '3', 'biz_question_label_status', NULL, 'warning', 'N', 103, 1, NOW()),
-- annotate_status（0未标/1已标全/2部分）
  (208, '000000', 1, '未标',   '0', 'biz_question_annotate_status', NULL, 'info',    'N', 103, 1, NOW()),
  (209, '000000', 2, '已标全', '1', 'biz_question_annotate_status', NULL, 'success', 'N', 103, 1, NOW()),
  (210, '000000', 3, '部分',   '2', 'biz_question_annotate_status', NULL, 'warning', 'N', 103, 1, NOW()),
-- assessment_type / dim2 考察类型（string 码 = label）
  (211, '000000', 1,  '概念辨析',     '概念辨析',     'biz_question_assessment_type', NULL, NULL, 'N', 103, 1, NOW()),
  (212, '000000', 2,  '直接计算',     '直接计算',     'biz_question_assessment_type', NULL, NULL, 'N', 103, 1, NOW()),
  (213, '000000', 3,  '公式套用',     '公式套用',     'biz_question_assessment_type', NULL, NULL, 'N', 103, 1, NOW()),
  (214, '000000', 4,  '性质判定',     '性质判定',     'biz_question_assessment_type', NULL, NULL, 'N', 103, 1, NOW()),
  (215, '000000', 5,  '证明推理',     '证明推理',     'biz_question_assessment_type', NULL, NULL, 'N', 103, 1, NOW()),
  (216, '000000', 6,  '应用建模',     '应用建模',     'biz_question_assessment_type', NULL, NULL, 'N', 103, 1, NOW()),
  (217, '000000', 7,  '作图',         '作图',         'biz_question_assessment_type', NULL, NULL, 'N', 103, 1, NOW()),
  (218, '000000', 8,  '探究归纳',     '探究归纳',     'biz_question_assessment_type', NULL, NULL, 'N', 103, 1, NOW()),
  (219, '000000', 9,  '阅读理解迁移', '阅读理解迁移', 'biz_question_assessment_type', NULL, NULL, 'N', 103, 1, NOW()),
  (220, '000000', 10, '纠错',         '纠错',         'biz_question_assessment_type', NULL, NULL, 'N', 103, 1, NOW());
