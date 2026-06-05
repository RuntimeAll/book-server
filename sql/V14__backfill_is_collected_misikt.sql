-- ============================================================================
-- V14: 回填 is_collected=0 / import_source='misikt' 给 29422 老题
-- ----------------------------------------------------------------------------
-- 背景: V11 加 is_collected + import_source 两列时 NOT NULL DEFAULT (1, 'manual')
--       但 create_user=2 的 29422 条是 misikt 历史导入题, 默认值语义反了
-- 范围: create_user=2 的所有题目 (老题导入用户, 见 PRD-B-012)
-- 期望影响行数 = 29422 (= SELECT COUNT(*) FROM biz_question WHERE create_user=2)
-- 其余 16 条 create_user<>2 的真实"我的收藏"保持 (1, 'manual') 不动
-- ============================================================================

UPDATE biz_question
SET is_collected = 0,
    import_source = 'misikt'
WHERE create_user = 2;

-- 验收 (执行后人工核对):
-- SELECT COUNT(*) FROM biz_question WHERE create_user=2 AND (is_collected<>0 OR import_source<>'misikt');  -- 期望 0
-- SELECT COUNT(*) FROM biz_question WHERE is_collected IS NULL;  -- 期望 0
