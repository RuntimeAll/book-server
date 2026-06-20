-- PRD-B-012 T3: seed 5 个 biz_anno_* 字典 type + 32 条 dict_data
-- 受控词表, 约束 LLM 取值; 复用 RuoYi sys_dict_type + sys_dict_data
-- 维度分工:
--   biz_anno_COG       认知层级(课标四层 4 条)
--   biz_anno_LITERACY  新课标九素养(9 条)
--   biz_anno_METHOD    数学思想方法(8 条)
--   biz_anno_SCENE     情境/场景(4 条)
--   biz_anno_ERROR     易错点/陷阱(7 条)
-- dict_id 段 20-24, dict_code 段 100-176 (留 200 起步给后续维度)
-- create_dept=NULL / create_by=NULL (PRD T3 指示, 标识为 seed 数据)

-- ============================================================
-- 1) sys_dict_type 5 行
-- ============================================================
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, remark) VALUES
  (20, '000000', '题目标注-认知层级',     'biz_anno_COG',      NULL, NULL, NOW(), '课标四层认知层级'),
  (21, '000000', '题目标注-核心素养',     'biz_anno_LITERACY', NULL, NULL, NOW(), '新课标九大数学核心素养'),
  (22, '000000', '题目标注-思想方法',     'biz_anno_METHOD',   NULL, NULL, NOW(), '八大数学思想方法'),
  (23, '000000', '题目标注-情境场景',     'biz_anno_SCENE',    NULL, NULL, NOW(), '题目情境/场景四分'),
  (24, '000000', '题目标注-易错点陷阱',   'biz_anno_ERROR',    NULL, NULL, NOW(), '常见易错点/陷阱分类');

-- ============================================================
-- 2) sys_dict_data 32 行
-- ============================================================

-- 2.1 biz_anno_COG 认知层级 4 条
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, remark) VALUES
  (100, '000000', 1, '了解',     'UNDERSTAND', 'biz_anno_COG', '', 'default', 'N', NULL, NULL, NOW(), '课标认知层级-了解'),
  (101, '000000', 2, '理解',     'COMPREHEND', 'biz_anno_COG', '', 'default', 'N', NULL, NULL, NOW(), '课标认知层级-理解'),
  (102, '000000', 3, '掌握',     'MASTER',     'biz_anno_COG', '', 'default', 'N', NULL, NULL, NOW(), '课标认知层级-掌握'),
  (103, '000000', 4, '灵活运用', 'APPLY',      'biz_anno_COG', '', 'default', 'N', NULL, NULL, NOW(), '课标认知层级-灵活运用');

-- 2.2 biz_anno_LITERACY 数学核心素养 9 条
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, remark) VALUES
  (110, '000000', 1, '抽象',     'ABSTRACT',       'biz_anno_LITERACY', '', 'default', 'N', NULL, NULL, NOW(), '核心素养-抽象'),
  (111, '000000', 2, '运算',     'OPERATION',      'biz_anno_LITERACY', '', 'default', 'N', NULL, NULL, NOW(), '核心素养-运算'),
  (112, '000000', 3, '几何直观', 'GEOMETRIC_VIEW', 'biz_anno_LITERACY', '', 'default', 'N', NULL, NULL, NOW(), '核心素养-几何直观'),
  (113, '000000', 4, '空间观念', 'SPATIAL_VIEW',   'biz_anno_LITERACY', '', 'default', 'N', NULL, NULL, NOW(), '核心素养-空间观念'),
  (114, '000000', 5, '推理',     'REASONING',      'biz_anno_LITERACY', '', 'default', 'N', NULL, NULL, NOW(), '核心素养-推理'),
  (115, '000000', 6, '模型观念', 'MODEL_VIEW',     'biz_anno_LITERACY', '', 'default', 'N', NULL, NULL, NOW(), '核心素养-模型观念'),
  (116, '000000', 7, '数据观念', 'DATA_VIEW',      'biz_anno_LITERACY', '', 'default', 'N', NULL, NULL, NOW(), '核心素养-数据观念'),
  (117, '000000', 8, '应用意识', 'APPLICATION',    'biz_anno_LITERACY', '', 'default', 'N', NULL, NULL, NOW(), '核心素养-应用意识'),
  (118, '000000', 9, '创新意识', 'INNOVATION',     'biz_anno_LITERACY', '', 'default', 'N', NULL, NULL, NOW(), '核心素养-创新意识');

-- 2.3 biz_anno_METHOD 数学思想方法 8 条
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, remark) VALUES
  (130, '000000', 1, '数形结合',     'SHU_XING',  'biz_anno_METHOD', '', 'default', 'N', NULL, NULL, NOW(), '思想方法-数形结合'),
  (131, '000000', 2, '分类讨论',     'FEN_LEI',   'biz_anno_METHOD', '', 'default', 'N', NULL, NULL, NOW(), '思想方法-分类讨论'),
  (132, '000000', 3, '化归转化',     'HUA_GUI',   'biz_anno_METHOD', '', 'default', 'N', NULL, NULL, NOW(), '思想方法-化归转化'),
  (133, '000000', 4, '方程函数',     'FANG_HAN',  'biz_anno_METHOD', '', 'default', 'N', NULL, NULL, NOW(), '思想方法-方程函数'),
  (134, '000000', 5, '数学建模',     'JIAN_MO',   'biz_anno_METHOD', '', 'default', 'N', NULL, NULL, NOW(), '思想方法-数学建模'),
  (135, '000000', 6, '特殊与一般',   'TE_SHU',    'biz_anno_METHOD', '', 'default', 'N', NULL, NULL, NOW(), '思想方法-特殊与一般'),
  (136, '000000', 7, '待定系数',     'DAI_DING',  'biz_anno_METHOD', '', 'default', 'N', NULL, NULL, NOW(), '思想方法-待定系数'),
  (137, '000000', 8, '数学归纳',     'GUI_NA',    'biz_anno_METHOD', '', 'default', 'N', NULL, NULL, NOW(), '思想方法-数学归纳');

-- 2.4 biz_anno_SCENE 情境/场景 4 条
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, remark) VALUES
  (150, '000000', 1, '纯数学',       'PURE_MATH', 'biz_anno_SCENE', '', 'default', 'N', NULL, NULL, NOW(), '情境-纯数学'),
  (151, '000000', 2, '现实生活',     'REAL_LIFE', 'biz_anno_SCENE', '', 'default', 'N', NULL, NULL, NOW(), '情境-现实生活'),
  (152, '000000', 3, '科学跨学科',   'CROSS_SCI', 'biz_anno_SCENE', '', 'default', 'N', NULL, NULL, NOW(), '情境-科学跨学科'),
  (153, '000000', 4, '数学文化',     'CULTURE',   'biz_anno_SCENE', '', 'default', 'N', NULL, NULL, NOW(), '情境-数学文化');

-- 2.5 biz_anno_ERROR 易错点/陷阱 7 条
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, remark) VALUES
  (170, '000000', 1, '概念混淆',     'CONCEPT',     'biz_anno_ERROR', '', 'default', 'N', NULL, NULL, NOW(), '易错-概念混淆'),
  (171, '000000', 2, '计算失误',     'CALCULATION', 'biz_anno_ERROR', '', 'default', 'N', NULL, NULL, NOW(), '易错-计算失误'),
  (172, '000000', 3, '审题偏差',     'READING',     'biz_anno_ERROR', '', 'default', 'N', NULL, NULL, NOW(), '易错-审题偏差'),
  (173, '000000', 4, '隐含遗漏',     'IMPLICIT',    'biz_anno_ERROR', '', 'default', 'N', NULL, NULL, NOW(), '易错-隐含遗漏'),
  (174, '000000', 5, '分类不全',     'CLASSIFY',    'biz_anno_ERROR', '', 'default', 'N', NULL, NULL, NOW(), '易错-分类不全'),
  (175, '000000', 6, '表达不规范',   'EXPRESS',     'biz_anno_ERROR', '', 'default', 'N', NULL, NULL, NOW(), '易错-表达不规范'),
  (176, '000000', 7, '思路缺失',     'THINKING',    'biz_anno_ERROR', '', 'default', 'N', NULL, NULL, NOW(), '易错-思路缺失');
