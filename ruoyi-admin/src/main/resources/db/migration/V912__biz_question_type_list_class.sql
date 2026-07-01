-- C 线预留段 V901+（题型/难度字典魔法值收敛，2026-06-30）
-- 给字典 biz_question_type 补 list_class（= el-tag 类型），让前端题型徽标颜色统一走字典 SSOT，
-- 不再各组件硬编码 {1:'primary',4:'success',...}。超管后续可在「字典管理」里改 list_class 即生效。
-- 颜色取自迁移前各组件既有约定（保持视觉不变）；纯 UPDATE 增量、幂等。
-- dev 库（ai_lesson_prep）已于 2026-06-30 经 mysql MCP 先行 UPDATE，本文件为 fresh/prod 复现用。
UPDATE sys_dict_data SET list_class = CASE dict_value
    WHEN '1' THEN 'primary'   -- 选择题
    WHEN '2' THEN 'info'      -- 判断题
    WHEN '3' THEN 'info'      -- 应用题
    WHEN '4' THEN 'success'   -- 填空题
    WHEN '5' THEN 'warning'   -- 解答题
    WHEN '6' THEN 'info'      -- 作图题
    WHEN '7' THEN 'warning'   -- 计算题
    WHEN '8' THEN 'info'      -- 证明题
    ELSE list_class END
WHERE dict_type = 'biz_question_type';
