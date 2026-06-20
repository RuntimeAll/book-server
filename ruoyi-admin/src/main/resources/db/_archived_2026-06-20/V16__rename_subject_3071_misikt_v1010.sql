-- V16: 把 biz_subject 中 3071 这棵子树的 1-2 层节点名字对齐 misikt 新版 v1010 接口
--
-- 背景：
--   biz_subject 3071 整棵的 level 3/4/5 叶子节点 id + 名字本来就跟 misikt v1010 完全一致（共 354 节点 / 4524 题挂载）；
--   只有顶层 3071 + 8 个二级节点的名字是 V1__init_book_tables.sql:42-52 自家 seed 硬编码的（'浙教版数学' / '七年级上册~中考二轮复习' 旧概念分层），
--   misikt 后来把 3071 直接重命名为「七年级上册」并把下面的 3071001~3071006 重命名为「第N章」，dev 库一直没同步。
--   本次按 misikt v1010 接口实测结果做名字对齐，不动结构、不动 id、不动题挂载。
--
-- 操作 = 9 条：
--   8 条 UPDATE 改名（3071 + 3071001~3071006 + 3071008）
--   1 条软删（3071007 中考一轮复习，misikt v1010 已从七上下移除，dev 库下挂 0 题）

UPDATE biz_subject SET name='七年级上册',           update_by='misikt-sync-v1010', update_time=NOW() WHERE id='3071'     AND status='0';
UPDATE biz_subject SET name='第一章 有理数',         update_by='misikt-sync-v1010', update_time=NOW() WHERE id='3071001'  AND status='0';
UPDATE biz_subject SET name='第二章 有理数的运算',    update_by='misikt-sync-v1010', update_time=NOW() WHERE id='3071002'  AND status='0';
UPDATE biz_subject SET name='第三章 实数',           update_by='misikt-sync-v1010', update_time=NOW() WHERE id='3071003'  AND status='0';
UPDATE biz_subject SET name='第四章 代数式',         update_by='misikt-sync-v1010', update_time=NOW() WHERE id='3071004'  AND status='0';
UPDATE biz_subject SET name='第五章 一元一次方程',    update_by='misikt-sync-v1010', update_time=NOW() WHERE id='3071005'  AND status='0';
UPDATE biz_subject SET name='第六章 图形的初步认识',  update_by='misikt-sync-v1010', update_time=NOW() WHERE id='3071006'  AND status='0';
UPDATE biz_subject SET name='期末专题',              update_by='misikt-sync-v1010', update_time=NOW() WHERE id='3071008'  AND status='0';

-- 3071007 中考一轮复习：misikt v1010 七上已无此节点，dev 库下挂 0 题，软删归档
UPDATE biz_subject SET status='9', update_by='misikt-sync-v1010', update_time=NOW() WHERE id='3071007' AND status='0';
