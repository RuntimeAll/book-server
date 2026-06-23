-- PRD-A-023 B10: 举一反三入库题泄漏公共题库 + 缺审核闸 —— 治理「入库 ≠ 公开」。
--
-- 背景/根因:
--   公共题库查询（QuestionServiceImpl.buildPageWrapper 的 mine 非 true 分支）此前与「我的题库」
--   一样只过滤 status='1'，无 owner/来源/审核过滤 → 任何老师把举一反三题入库（status=1）即泄漏进公共库。
--
-- 治理模型（用户拍板）:
--   ① 公共题库只留「超级管理员」公开的题，排除所有老师的题；
--   ② 超管也不能「入库即公开」—— 入库=进我的题库(私有)，公开需超管审核置 is_public=1 才发布。
--   ③ 即「入库 ≠ 公开」：status='1' 仅代表已入库（我的题库可见），is_public=1 才进公共库。
--
-- 本迁移 = 纯增量（ADD COLUMN + 存量 UPDATE 回填，无 DROP）。A 线顺序号，接 V25 → V26。
--
-- ============ 1. 加列 ============
-- is_public TINYINT(1) NOT NULL DEFAULT 0：0=私有/未公开（默认，入库即此态），1=超管审核通过公开。
ALTER TABLE `biz_question`
    ADD COLUMN `is_public` TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '是否公开 0私有(默认/入库态) 1超管审核通过公开（PRD-A-023 B10 公共题库审核闸）';

-- ============ 2. 存量回填 ============
-- 回填规则（据 dev 库只读调查 2026-06-22，status='1' 共 29430 行）:
--   - import_source='misikt'  种子题 29422 行 → is_public=1（该公开的导入种子题，保住公共题库不空）
--   - import_source='举一反三' 8 行（create_user=1 超管，"入库即公开"的旧泄漏题）→ 保持 is_public=0（撤出公共库）
--   - import_source='AI-Orchestrator' / 手工 / 其余 → 保持 is_public=0（默认即此，撤出公共库）
-- 仅把 misikt 种子题置公开，其余维持 DEFAULT 0。所有 status<>'1' 的题（草稿/软删）天然 is_public=0 不受影响。
UPDATE `biz_question`
    SET `is_public` = 1
    WHERE `import_source` = 'misikt';
