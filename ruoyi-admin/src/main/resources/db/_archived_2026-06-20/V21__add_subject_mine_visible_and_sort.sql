-- V21: 题库目录（biz_subject）个人题库显隐 + 顶层排序回填（2026-06-11 用户拍板）
-- ① 新列 mine_visible：仅控制「我的题库」页目录树是否展示（公共题库页不受影响），全局生效
-- ② 顶层目录 sort 回填年级顺序（原全 0 → 按 id 乱序），排序两页共用（BE sortRecursive 已生效）
-- ③ 既有 status='9'（停用）节点映射为个人题库隐藏，保留历史意图

ALTER TABLE biz_subject
    ADD COLUMN mine_visible CHAR(1) NOT NULL DEFAULT '1' COMMENT '个人题库(我的题库)目录是否展示(1展示 0隐藏)';

-- 顶层排序：七上→七下→八上→八下→九上→九下→中考一轮→解题专题→新题抢先
UPDATE biz_subject SET sort = 1 WHERE id = '3071' AND parent_id = '0';
UPDATE biz_subject SET sort = 2 WHERE id = '3072' AND parent_id = '0';
UPDATE biz_subject SET sort = 3 WHERE id = '3081' AND parent_id = '0';
UPDATE biz_subject SET sort = 4 WHERE id = '3082' AND parent_id = '0';
UPDATE biz_subject SET sort = 5 WHERE id = '3091' AND parent_id = '0';
UPDATE biz_subject SET sort = 6 WHERE id = '3092' AND parent_id = '0';
UPDATE biz_subject SET sort = 7 WHERE id = '3010' AND parent_id = '0';
UPDATE biz_subject SET sort = 8 WHERE id = '3100' AND parent_id = '0';
UPDATE biz_subject SET sort = 9 WHERE id = '3120' AND parent_id = '0';

-- 历史停用节点（status='9'）→ 个人题库隐藏
UPDATE biz_subject SET mine_visible = '0' WHERE status = '9';
