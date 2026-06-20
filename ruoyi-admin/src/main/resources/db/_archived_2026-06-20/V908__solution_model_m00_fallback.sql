-- V908: 解题模型词库·保底条目 M00「概念直用」（PRD-C-015 批2·唯一 DB 变更）
-- 人读 SSOT = codeplace-C/claude-code-sign/26-解题模型词库-初版.md
-- 用途: 模型维永不为空（拍板）。无组合技巧的基础题 / 候选空 / LLM 全不确认 → 代码层兜底锚 M00。
--   M00 不参与反查（无 biz_solution_model_kp 绑定行），是 model_anchor.anchor_models 的代码兜底值。
-- 幂等: INSERT IGNORE（dev 已 apply V906/V907 同款纪律，可安全重跑）；纯增量、无 DROP。
-- 列序对齐 V906: (id,name,category,trigger_feature,action_conclusion,is_router,has_geo_template,sort)

INSERT IGNORE INTO biz_solution_model
  (id,name,category,trigger_feature,action_conclusion,is_router,has_geo_template,sort)
VALUES
  ('M00','概念直用','保底','无组合技巧的基础题','直接套定义/性质/运算规则作答','0','0',0);
