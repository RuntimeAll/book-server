-- ============================================================================
-- V6 · H1 卡 段② BE 波 1 — book-admin 后台题目菜单 sys_menu seed
-- ----------------------------------------------------------------------------
-- 目的：让 FE book-admin（plus-ui）调 BE /system/menu/getRouters 时能拉到
--      「题库管理」目录 + 「题目列表」菜单 + admin:question:list 权限标识。
--
-- 结构（参考 RuoYi sys_menu 标准）：
--   menu_id=1700 · 题库管理（M 目录）       parent=0     path=/question
--   menu_id=1701 · 题目列表（C 菜单/页面）  parent=1700  path=list  perms=admin:question:list
--
-- 角色挂载（sys_role_menu）：超级管理员 role_id=1 自动有 *::* 权限（RuoYi 默认行为：
--   superadmin 角色不校验 sys_role_menu，所有菜单可见 + 所有权限通过），本表插入
--   仅为「FE 菜单树渲染」识别该菜单条目；其他角色后续按需挂。
--
-- 当前 max(menu_id) = 1623（实测），本卡用 1700 段 (1700/1701) 避让，
--   给未来 RuoYi 默认菜单的扩展留余量。
--
-- 加新不改老（sql-migration skill §4 铁则）：本文件命名 V6__，编号单调递增
--   （前置 V1~V5 已落库）；如需撤销，起 V7__rollback_admin_question_menu.sql。
-- ============================================================================

-- 1. 一级目录：题库管理（菜单类型 M）
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    create_dept, create_by, create_time, update_by, update_time, remark
) VALUES (
    1700, '题库管理', 0, 10, '/question', NULL, NULL,
    1, 0, 'M', '0', '0', '', 'star',
    NULL, 1, NOW(), NULL, NULL, 'H1 卡 V6 seed · book-admin 后台题目管理入口'
);

-- 2. 二级菜单：题目列表（菜单类型 C，对应前端 views/question/list.vue）
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    create_dept, create_by, create_time, update_by, update_time, remark
) VALUES (
    1701, '题目列表', 1700, 1, 'list', 'question/list', NULL,
    1, 0, 'C', '0', '0', 'admin:question:list', 'list',
    NULL, 1, NOW(), NULL, NULL, 'H1 卡 V6 seed · 题目列表页（FE views/question/list.vue 待 FE 段③ 落地）'
);

-- 3. 挂到超级管理员角色（role_id=1）— 让菜单树能渲染条目
--    注：superadmin 在 RuoYi 标准行为下不校验权限，本插入仅为前端菜单树识别条目用。
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 1700);
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 1701);
