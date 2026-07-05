-- ============================================================
-- PRD-C-213 修复轮 R1a：档案建模（BUG-001）+ 计划归属（S1）—— dev 存量库迁移
-- 目标库 = ai_lesson_prep @ :3307（dev 手工执行；prod 走全量覆盖，以 sql/收敛DDL-2026-07.sql 为准）
-- 顺序铁律：加列 → 回填 → 删旧列（删列放最后，回填失败可回退）
-- 🔴 执行后必刷字典缓存（DELETE /system/dict/type/refreshCache 或重启 BE），否则 FE 拿旧缓存
-- ============================================================

-- ───────── 1. 加列 ─────────

ALTER TABLE `biz_student`
  ADD COLUMN `grade_no` int DEFAULT NULL COMMENT '年级(1-12,字典biz_edu_grade;=grade_year学年就读年级,当前年级按9/1进位推导)' AFTER `name`,
  ADD COLUMN `grade_year` int DEFAULT NULL COMMENT 'grade_no生效学年起始年(如2026=2026-09-01起学年)' AFTER `grade_no`,
  ADD COLUMN `textbook_edition` varchar(20) DEFAULT NULL COMMENT '教材版本字典码(biz_edu_edition:1浙教/2人教/3北师大/4苏教)' AFTER `grade_year`;

ALTER TABLE `biz_class`
  ADD COLUMN `grade_no` int DEFAULT NULL COMMENT '年级(1-12,字典biz_edu_grade;允许NULL)' AFTER `name`,
  ADD COLUMN `grade_year` int DEFAULT NULL COMMENT 'grade_no生效学年起始年(允许NULL)' AFTER `grade_no`,
  ADD COLUMN `textbook_edition` varchar(20) DEFAULT NULL COMMENT '教材版本字典码(biz_edu_edition)' AFTER `grade_year`;

ALTER TABLE `biz_course_plan`
  ADD COLUMN `target_id` bigint DEFAULT NULL COMMENT 'S1 计划归属对象id(biz_student.id 或 biz_class.id,与target_type联合;建计划必传)' AFTER `target_type`,
  ADD KEY `idx_target` (`target_type`,`target_id`);

-- ───────── 2. 数据回填 ─────────
-- dev 现量（2026-07-05 查库）：biz_student 6 行，grade ∈ {升三,升四,升五,升六,初一,初二}，
-- subject ∈ {数学,科学}，textbook 人教版/浙教版开头；biz_class 空表。
-- 暑期录入语义：「升X/初X(带X年级上册教材)」= 2026 学年就读该年级 → grade_no=升入年级, grade_year=2026。

UPDATE `biz_student` SET
  `grade_no` = CASE `grade`
      WHEN '升一' THEN 1 WHEN '升二' THEN 2 WHEN '升三' THEN 3
      WHEN '升四' THEN 4 WHEN '升五' THEN 5 WHEN '升六' THEN 6
      WHEN '初一' THEN 7 WHEN '初二' THEN 8 WHEN '初三' THEN 9
      WHEN '一年级' THEN 1 WHEN '二年级' THEN 2 WHEN '三年级' THEN 3
      WHEN '四年级' THEN 4 WHEN '五年级' THEN 5 WHEN '六年级' THEN 6
      WHEN '七年级' THEN 7 WHEN '八年级' THEN 8 WHEN '九年级' THEN 9
      ELSE NULL END,
  `grade_year` = CASE WHEN `grade` IS NULL THEN NULL ELSE 2026 END,
  `textbook_edition` = CASE
      WHEN `textbook` LIKE '人教%'   THEN '2'
      WHEN `textbook` LIKE '浙教%'   THEN '1'
      WHEN `textbook` LIKE '北师大%' THEN '3'
      WHEN `textbook` LIKE '苏教%'   THEN '4'
      ELSE NULL END,
  `subject` = CASE `subject`
      WHEN '数学' THEN '1' WHEN '科学' THEN '2'
      WHEN '语文' THEN '3' WHEN '英语' THEN '4'
      ELSE `subject` END;

UPDATE `biz_class` SET
  `grade_no` = CASE `grade`
      WHEN '升一' THEN 1 WHEN '升二' THEN 2 WHEN '升三' THEN 3
      WHEN '升四' THEN 4 WHEN '升五' THEN 5 WHEN '升六' THEN 6
      WHEN '初一' THEN 7 WHEN '初二' THEN 8 WHEN '初三' THEN 9
      WHEN '一年级' THEN 1 WHEN '二年级' THEN 2 WHEN '三年级' THEN 3
      WHEN '四年级' THEN 4 WHEN '五年级' THEN 5 WHEN '六年级' THEN 6
      WHEN '七年级' THEN 7 WHEN '八年级' THEN 8 WHEN '九年级' THEN 9
      ELSE NULL END,
  `grade_year` = CASE WHEN `grade` IS NULL THEN NULL ELSE 2026 END,
  `subject` = CASE `subject`
      WHEN '数学' THEN '1' WHEN '科学' THEN '2'
      WHEN '语文' THEN '3' WHEN '英语' THEN '4'
      ELSE `subject` END;

-- S1 回填：计划归属对象从 biz_schedule_session 按 plan_id 反推
-- （dev 现量：plan 1 张，13 个场次全部 target_id=1/target_type='0' → 回填 target_id=1）
UPDATE `biz_course_plan` p SET p.`target_id` = (
    SELECT MIN(s.`target_id`) FROM `biz_schedule_session` s
    WHERE s.`plan_id` = p.`id` AND s.`target_type` = p.`target_type`
) WHERE p.`target_id` IS NULL;

-- ───────── 3. 删旧列（回填核对无误后再执行）─────────

ALTER TABLE `biz_student`
  MODIFY COLUMN `subject` varchar(30) DEFAULT NULL COMMENT '学科字典码(biz_edu_subject:1数学/2科学/3语文/4英语)',
  DROP COLUMN `grade`,
  DROP COLUMN `textbook`;

ALTER TABLE `biz_class`
  MODIFY COLUMN `subject` varchar(30) DEFAULT NULL COMMENT '学科字典码(biz_edu_subject)',
  DROP COLUMN `grade`;

-- ───────── 4. 字典 seed ─────────
-- 查库结论（2026-07-05 dev ai_lesson_prep）：
--   · biz_edu_grade  已有 1~12（一年级~高三）→ 全量复用，零新增
--   · biz_term_tag   已有 暑假/上学期/寒假/下学期（值=中文文本）→ 全量复用，零新增
--   · biz_edu_subject 已有 1数学/2科学 → 补 3语文/4英语
--   · biz_edu_edition 已有 1浙教/2人教 → 补 3北师大/4苏教
-- dict_code 延续 biz_term_tag seed 的取号段（prod 插入时按该库雪花习惯取号或超管 UI 录入等价数据）。

INSERT INTO `sys_dict_data` (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, remark) VALUES
(1826050689283077, '000000', 3, '语文',   '3', 'biz_edu_subject', '', 'primary', 'N', 103, 1, NOW(), 'PRD-C-213 R1a 档案建模补码'),
(1826050689283078, '000000', 4, '英语',   '4', 'biz_edu_subject', '', 'primary', 'N', 103, 1, NOW(), 'PRD-C-213 R1a 档案建模补码'),
(1826050689283079, '000000', 3, '北师大', '3', 'biz_edu_edition', '', 'primary', 'N', 103, 1, NOW(), 'PRD-C-213 R1a 档案建模补码'),
(1826050689283080, '000000', 4, '苏教',   '4', 'biz_edu_edition', '', 'primary', 'N', 103, 1, NOW(), 'PRD-C-213 R1a 档案建模补码');

-- ───────── 5. 验证（只读）─────────
-- SELECT id, name, grade_no, grade_year, textbook_edition, subject FROM biz_student;
-- SELECT id, name, target_type, target_id FROM biz_course_plan;
-- SELECT dict_type, dict_value, dict_label FROM sys_dict_data WHERE dict_type IN ('biz_edu_subject','biz_edu_edition') ORDER BY dict_type, dict_sort;
