-- V22: 个人题库隐藏三个顶层目录（2026-06-11 用户拍板，见截图：中考一轮复习/数学解题技巧与专题/新题抢先）
-- 仅影响「我的题库」目录树（mine_visible 语义见 V21）；公共题库照常显示。幂等可重跑。

UPDATE biz_subject SET mine_visible = '0' WHERE id = '3010' AND parent_id = '0';  -- 中考一轮复习
UPDATE biz_subject SET mine_visible = '0' WHERE id = '3100' AND parent_id = '0';  -- 数学解题技巧与专题
UPDATE biz_subject SET mine_visible = '0' WHERE id = '3120' AND parent_id = '0';  -- 新题抢先
