-- V18__rebuild_paper_category_from_raw.sql
-- 背景: biz_paper_category 自 V1 init 起仅 11 行手搓占位（资料库→期中/期末/月考卷, 公共试卷→七/八/九年级,
--       专题→函数/几何），从未导入 misikt 真实分类树。真源 = raw_paper_category（misikt 抓包入库，97 节点真结构+真名）。
-- 现象: 卷库目录浅且与试卷脱节 —— 试卷 subject_id 用 misikt 长 ID（如 300310011002003），
--       占位树短 ID（3003001）对不上，点任何节点都查不到对应卷（导航从项目第一天就坏）。
-- 根因: V1__init_book_tables.sql:138 硬编码 11 行占位 demo；无 ETL 从 raw 导真结构（对比 biz_subject 有 V2 ETL）。
-- 作用: 清空 biz_paper_category，从 raw_paper_category 原样重建（id / parent_id / title→name / sort）。
--       BE lazyTree 用 ROOT_IDS={3001,3003,3004} 硬认根，3001 parentId='1' 由 toVo override，原样拷不散树。
-- 安全: 仅当 raw_paper_category 非空才重建（EXISTS 守卫）；prod 若无 raw staging → DELETE/INSERT 各 0 行 → no-op 保留现状。幂等可重跑。
-- 边界: 不改 schema；不碰 biz_paper（试卷靠 subject_id 前缀匹配，paper_category_id 恒 NULL）。不改历史 V 文件(§7)。
-- line-a-dev 2026-06-09 用户授权（"数据库里有源数据，直接拿来用就行"）。
SET NAMES utf8mb4;

-- 1. 清空旧占位（仅当 raw 源非空，prod 无 raw → 不删，保留现状）
DELETE FROM biz_paper_category WHERE EXISTS (SELECT 1 FROM raw_paper_category LIMIT 1);

-- 2. 从 raw 源重建（97 节点真结构）。raw 空则 INSERT 0 行 = no-op。
INSERT INTO biz_paper_category (id, parent_id, name, sort)
SELECT id, parent_id, title, COALESCE(sort, 0)
FROM raw_paper_category;
