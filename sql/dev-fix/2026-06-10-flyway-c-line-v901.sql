-- ============================================================================
-- 2026-06-10 dev 库 Flyway 对账：C 线 label 迁移 V16-18 → V901-903 重排登记
--   (非 Flyway 迁移文件，不入 db/migration；一次性 dev 对账，与 2026-06-09-flyway-rebaseline.sql 同款)
-- ----------------------------------------------------------------------------
-- 背景（号冲突根因）:
--   C 线 teacher-copilot 探针的 3 个迁移原编号 V16/V17/V18，放在 book-server/sql/（非 flyway 位置）。
--   A 线 master 后来把自己的 V16/17/18(rename subject / backfill / rebuild category) 落进
--   db/migration 并被 flyway 应用 → flyway_schema_history 的 16/17/18 = A 线内容。
--   C 线 3 个文件【号被占、又不在 flyway 位置】→ 永不自动应用，prod 部署会缺列/缺表。
--
-- 解法（2026-06-10）:
--   1. 把 C 线 3 个文件重排到【C 线预留段 V901+】并移入 db/migration（转正为 flyway 自动迁移）:
--        sql/V16 → db/migration/V901__alter_biz_question_label_dims.sql
--        sql/V17 → db/migration/V902__create_biz_label_job.sql
--        sql/V18 → db/migration/V903__create_biz_billing_event.sql
--      预留高位段 = A 线后续 V19/20/… 永不再与 C 线撞号（根治"合回 master / 下次 merge 复发"）。
--   2. 全为纯增量（ADD COLUMN/索引 + CREATE TABLE，无 DROP）→ prod 部署 flyway 直接 apply，零特殊步骤。
--   3. 本 dev 库【这三项已在上一轮手工应用过】(biz_question 维度/打标列 + biz_label_job + biz_billing_event 均在)，
--      故本脚本只补登 3 行 flyway_schema_history（checksum=NULL 跳校验、installed_by='manual'），
--      让 BE 启动 validate 认它们"已应用"、不重跑（重跑会撞 Duplicate column/table）。
--
-- 🔴 仅本 dev 需要这步对账。【全新 dev / prod】无需本脚本：flyway 会在启动时把 V901-903 当普通迁移
--    live apply（号 > baseline 16、不在 history、纯增量）→ 自然落库。
--
-- 执行（已于 2026-06-10 跑过；保留备查 / dev 重建时如已手工建过对象再跑）:
--   docker exec -i cuoti-mysql mysql -uroot -p123456 miskt_data2 < 2026-06-10-flyway-c-line-v901.sql
-- 验证: 重启 :8090，boot 日志应见 "Successfully validated 22 migrations" + "No migration necessary"。
-- ============================================================================

INSERT INTO flyway_schema_history
  (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
VALUES
  (19, '901', 'alter biz question label dims', 'SQL', 'V901__alter_biz_question_label_dims.sql', NULL, 'manual', NOW(), 0, 1),
  (20, '902', 'create biz label job',          'SQL', 'V902__create_biz_label_job.sql',          NULL, 'manual', NOW(), 0, 1),
  (21, '903', 'create biz billing event',      'SQL', 'V903__create_biz_billing_event.sql',      NULL, 'manual', NOW(), 0, 1);

-- 验收
SELECT installed_rank, version, description, checksum, success
FROM flyway_schema_history WHERE version IN ('901','902','903') ORDER BY installed_rank;
-- 预期 3 行, checksum 全 NULL, success 全 1
