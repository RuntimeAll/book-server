-- V10__drop_tag_knowledge_use_realtime.sql
-- PRD-B-006 收尾·修正方案 (用户 2026-06-04 拍板).
--
-- 推翻 V8/V9 的 biz_tag_knowledge 预聚合方案, 改实时算 (LIKE 前缀 JOIN).
--
-- 推翻原因 (用户 2026-06-04 实测拍板):
--   1. 实测语义等价的实时 SQL 仅 7ms (biz_question_free_tag JOIN biz_question_knowledge
--      JOIN biz_free_tag, 用 knowledge_id LIKE 'subjectId%' 前缀匹配子树), 预聚合带不来性能收益.
--   2. 预聚合带来三个代价:
--      - 数据漂移: 新挂载题目/标签后 biz_tag_knowledge 不更新, 需要额外 sync 任务.
--      - 重复存储: 54k 行可由 ~50k 题挂载实时推出, 多一份就多一份维护.
--      - 维护成本: 任意 biz_question_free_tag / biz_question_knowledge 改动都要触发回写.
--   3. 实时算还额外解锁"任意 level (1-5) 子树聚合"能力 — 预聚合只能等值精确匹配 knowledge_id.
--
-- 配套代码变更 (同一 commit):
--   - 删除 BizTagKnowledge entity + BizTagKnowledgeMapper.
--   - AdminTagKnowledgeMapper 改名为 AdminTagSubjectMapper, SQL 改实时 JOIN.
--   - 接口路径 /admin/question/tag/byKnowledge -> /admin/question/tag/bySubject, 入参 knowledgeId -> subjectId.

DROP TABLE IF EXISTS biz_tag_knowledge;
