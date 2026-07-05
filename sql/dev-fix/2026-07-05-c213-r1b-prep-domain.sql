-- ============================================================
-- PRD-C-213 修复轮 R1b：备课域数据结构矫正（S2-S5）—— dev 存量库迁移
-- 目标库 = ai_lesson_prep @ :3307（dev 手工执行；prod 走全量覆盖，以 sql/收敛DDL-2026-07.sql 为准）
-- 台账 = workplace/.prd_ccw/PRD-C/PRD-C-213/bug/PRD-bug.md BUG-003/BUG-013
-- 拍板：
--   · pack.status 为备课状态唯一权威 → 删 biz_course_plan_lesson.prep_state
--     （课次 VO prepState 改为按 plan_lesson_id join biz_prep_pack 推导；session.prep_status 保留作日历缓存）；
--   · 家长消息改即时生成不落库 → 删 biz_session_review.parent_msg；
--   · 历史双包脏数据（同课次 lesson 包 + session 包并存）不写自动合并，清库重灌后自然消解。
-- ============================================================

ALTER TABLE `biz_course_plan_lesson` DROP COLUMN `prep_state`;

ALTER TABLE `biz_session_review` DROP COLUMN `parent_msg`;

-- ───────── 验证（只读）─────────
-- 两列应均已不存在（期望 0 行）：
-- SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = 'ai_lesson_prep'
--    AND ((TABLE_NAME = 'biz_course_plan_lesson' AND COLUMN_NAME = 'prep_state')
--      OR (TABLE_NAME = 'biz_session_review'     AND COLUMN_NAME = 'parent_msg'));
-- 双包脏数据体检（期望 0 行；有行=历史双包，等清库重灌消解，BE 已按 lesson 包优先兜底）：
-- SELECT s.id AS session_id, s.plan_lesson_id, pl.id AS lesson_pack_id, ps.id AS session_pack_id
--   FROM biz_schedule_session s
--   JOIN biz_prep_pack pl ON pl.plan_lesson_id = s.plan_lesson_id
--   JOIN biz_prep_pack ps ON ps.session_id = s.id
--  WHERE s.plan_lesson_id IS NOT NULL;
