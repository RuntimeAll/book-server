-- ============================================================================
-- 2026-06-09 dev 库 Flyway rebaseline 一次性恢复脚本（非 Flyway 迁移文件，不入 db/migration）
-- ----------------------------------------------------------------------------
-- 背景:
--   PRD-A-012 T5 接入 Flyway 时 baseline-version=0 + baseline-on-migrate=true 双 bug,
--   导致 Flyway 启动从 V1 起重跑(V1 含 DROP TABLE 把 biz_* 表清掉, V2 ETL 又回填),
--   再到 V6 撞 sys_menu PK 1062 失败 success=0 → 后续 V7-V16 全 skip 不写 history
--   → flyway_schema_history 只剩 7 行 (G8 红).
--
-- dev 库手工跑过实情盘查 (2026-06-09):
--   V1-V5  ✅ Flyway 已重跑成功 (history success=1, 实际由 V2 ETL 回填数据)
--   V6 sys_menu 1700/1701  ✅ seed 已在表  → 历史手工跑过, Flyway 重跑撞 PK → history success=0
--   V7 sys_user.grade/school  ✅ 列已加
--   V8 biz_text_content 表 ✅, 但 biz_question.stem_text_content_id/answer_text_content_id ❌ 未加
--   V9 ETL  ✅ 数据已在 biz_text_content (29234 行)
--   V10 DROP biz_tag_knowledge  ✅
--   V11 biz_question 10 列 + 5 索引  ❌ 一列都没加
--   V12 biz_question_annotation 表  ✅
--   V13 biz_anno_* 字典 seed  ✅ (5 dict_type)
--   V14 biz_question UPDATE  ❌ (依赖 V11 列, V11 未跑 → V14 未跑)
--   V15 18 字段 DROP COLUMN  ❌ 一字段都没删 (biz_question/biz_paper/biz_paper_category/biz_subject)
--   V16 biz_subject 3071 改名 + 3071007 软删  ❌ (3071 名字还是 '浙教版数学')
--
-- 修复策略 (G8 根因, 与 artifacts/Flyway-prod-baseline-SOP.md SOP 模式对齐):
--   1. 补齐 V8/V11/V14/V15/V16 缺失的 schema 变更
--      —— 用 procedure 检测式 ALTER (MySQL 8.0.46 不支持 ADD/DROP COLUMN IF [NOT] EXISTS 子句, 实测 1064)
--   2. DROP + 重建 flyway_schema_history, 灌 17 行 (1 BASELINE rank=0 + 16 V1-V16 success=1)
--      —— SOP 模式 dev/prod 同打法
--   3. application{,-dev,-prod}.yml 已改 baseline-version=16 + baseline-on-migrate=false (PRD-A-012 G8 同 commit)
--   4. db/migration 内 V1-V16 全部低于 baseline 不跑, V17+ 由 Flyway 接管 (validate-on-migrate=true)
--
-- 不动老 V 文件 (CLAUDE.md §7 铁则): 本脚本是 dev 一次性恢复, 不入 db/migration,
--   不影响 V*.sql 历史轨道。prod 库由 deploy session 用同款 SOP 脚本对齐.
--
-- 执行:
--   docker cp 2026-06-09-flyway-rebaseline.sql cuoti-mysql:/tmp/rebaseline.sql
--   docker exec cuoti-mysql sh -c 'mysql --default-character-set=utf8mb4 -uroot -p123456 miskt_data2 < /tmp/rebaseline.sql'
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 幂等补齐 V8/V11/V14/V15/V16 缺失 schema (procedure 检测式)
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_dev_fix_rebaseline;
DELIMITER $$
CREATE PROCEDURE sp_dev_fix_rebaseline()
BEGIN
  -- ========= V8 biz_question 补两列 =========
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='stem_text_content_id') THEN
    ALTER TABLE biz_question ADD COLUMN stem_text_content_id BIGINT NULL COMMENT '题干文本外置 FK -> biz_text_content.id' AFTER stem_text;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='answer_text_content_id') THEN
    ALTER TABLE biz_question ADD COLUMN answer_text_content_id BIGINT NULL COMMENT '答案文本外置 FK -> biz_text_content.id' AFTER correct_answer;
  END IF;

  -- ========= V11 biz_question 补 10 列 =========
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='base_score') THEN
    ALTER TABLE biz_question ADD COLUMN base_score DECIMAL(5,2) NULL COMMENT '题自身标准分值, 与 biz_paper_question.score 精度对齐, 卷内 override 优先';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='is_collected') THEN
    ALTER TABLE biz_question ADD COLUMN is_collected TINYINT(1) NOT NULL DEFAULT 1 COMMENT '0=misikt老题(可删) 1=自有化新题';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='import_source') THEN
    ALTER TABLE biz_question ADD COLUMN import_source VARCHAR(32) NULL DEFAULT 'manual' COMMENT 'manual/textin/misikt/my-clone';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='import_batch_id') THEN
    ALTER TABLE biz_question ADD COLUMN import_batch_id VARCHAR(64) NULL COMMENT '录入批次(按卷)';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='region_code') THEN
    ALTER TABLE biz_question ADD COLUMN region_code VARCHAR(12) NULL COMMENT '国标行政区划码 330100=杭州 NULL=通用';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='source_type') THEN
    ALTER TABLE biz_question ADD COLUMN source_type TINYINT NULL COMMENT '1中考2模拟3期末4月考5单元6自编9其他';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='mother_question_id') THEN
    ALTER TABLE biz_question ADD COLUMN mother_question_id BIGINT NULL COMMENT '母题指针 命题血缘自关联';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='variant_relation') THEN
    ALTER TABLE biz_question ADD COLUMN variant_relation VARCHAR(16) NULL COMMENT '数值变式/情境变式/结构变式/同源';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='annotate_version') THEN
    ALTER TABLE biz_question ADD COLUMN annotate_version INT NOT NULL DEFAULT 0 COMMENT '标注 schema 版本(维度扩展时 bump)';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='annotate_status') THEN
    ALTER TABLE biz_question ADD COLUMN annotate_status TINYINT NOT NULL DEFAULT 0 COMMENT '0未标 1已标全 2部分';
  END IF;

  -- V11 5 索引
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='biz_question' AND index_name='idx_is_collected') THEN
    ALTER TABLE biz_question ADD KEY idx_is_collected (is_collected);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='biz_question' AND index_name='idx_import_batch') THEN
    ALTER TABLE biz_question ADD KEY idx_import_batch (import_batch_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='biz_question' AND index_name='idx_region') THEN
    ALTER TABLE biz_question ADD KEY idx_region (region_code);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='biz_question' AND index_name='idx_source_type') THEN
    ALTER TABLE biz_question ADD KEY idx_source_type (source_type);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='biz_question' AND index_name='idx_mother') THEN
    ALTER TABLE biz_question ADD KEY idx_mother (mother_question_id);
  END IF;

  -- ========= V14 UPDATE (V11 列已就位) =========
  UPDATE biz_question SET is_collected = 0, import_source = 'misikt' WHERE create_user = 2;

  -- ========= V15 DROP COLUMN 18 字段 =========
  -- biz_question 10 列
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='short_title') THEN
    ALTER TABLE biz_question DROP COLUMN short_title;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='video_url') THEN
    ALTER TABLE biz_question DROP COLUMN video_url;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='question_std_knowledge_str') THEN
    ALTER TABLE biz_question DROP COLUMN question_std_knowledge_str;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='dedup_kind') THEN
    ALTER TABLE biz_question DROP COLUMN dedup_kind;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='is_share') THEN
    ALTER TABLE biz_question DROP COLUMN is_share;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='is_repeat') THEN
    ALTER TABLE biz_question DROP COLUMN is_repeat;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='repeat_question_id') THEN
    ALTER TABLE biz_question DROP COLUMN repeat_question_id;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='options_json') THEN
    ALTER TABLE biz_question DROP COLUMN options_json;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='correct_answer') THEN
    ALTER TABLE biz_question DROP COLUMN correct_answer;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_question' AND column_name='score_std_json') THEN
    ALTER TABLE biz_question DROP COLUMN score_std_json;
  END IF;

  -- biz_paper 4 列
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_paper' AND column_name='directory_name') THEN
    ALTER TABLE biz_paper DROP COLUMN directory_name;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_paper' AND column_name='hg_score') THEN
    ALTER TABLE biz_paper DROP COLUMN hg_score;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_paper' AND column_name='frame_text_content_id') THEN
    ALTER TABLE biz_paper DROP COLUMN frame_text_content_id;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_paper' AND column_name='is_share') THEN
    ALTER TABLE biz_paper DROP COLUMN is_share;
  END IF;

  -- biz_paper_category 1 列
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_paper_category' AND column_name='is_share') THEN
    ALTER TABLE biz_paper_category DROP COLUMN is_share;
  END IF;

  -- biz_subject 3 列
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_subject' AND column_name='knowledge_img') THEN
    ALTER TABLE biz_subject DROP COLUMN knowledge_img;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_subject' AND column_name='knowledge_video') THEN
    ALTER TABLE biz_subject DROP COLUMN knowledge_video;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_subject' AND column_name='is_share') THEN
    ALTER TABLE biz_subject DROP COLUMN is_share;
  END IF;

  -- ========= V16 biz_subject 3071 子树改名 + 3071007 软删 =========
  UPDATE biz_subject SET name='七年级上册',           update_by='misikt-sync-v1010', update_time=NOW() WHERE id='3071'     AND status='0';
  UPDATE biz_subject SET name='第一章 有理数',         update_by='misikt-sync-v1010', update_time=NOW() WHERE id='3071001'  AND status='0';
  UPDATE biz_subject SET name='第二章 有理数的运算',    update_by='misikt-sync-v1010', update_time=NOW() WHERE id='3071002'  AND status='0';
  UPDATE biz_subject SET name='第三章 实数',           update_by='misikt-sync-v1010', update_time=NOW() WHERE id='3071003'  AND status='0';
  UPDATE biz_subject SET name='第四章 代数式',         update_by='misikt-sync-v1010', update_time=NOW() WHERE id='3071004'  AND status='0';
  UPDATE biz_subject SET name='第五章 一元一次方程',    update_by='misikt-sync-v1010', update_time=NOW() WHERE id='3071005'  AND status='0';
  UPDATE biz_subject SET name='第六章 图形的初步认识',  update_by='misikt-sync-v1010', update_time=NOW() WHERE id='3071006'  AND status='0';
  UPDATE biz_subject SET name='期末专题',              update_by='misikt-sync-v1010', update_time=NOW() WHERE id='3071008'  AND status='0';
  UPDATE biz_subject SET status='9', update_by='misikt-sync-v1010', update_time=NOW() WHERE id='3071007' AND status='0';
END$$
DELIMITER ;

CALL sp_dev_fix_rebaseline();
DROP PROCEDURE sp_dev_fix_rebaseline;

-- ----------------------------------------------------------------------------
-- 2. DROP + 重建 flyway_schema_history, 灌 17 行 (与 SOP 一致)
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS flyway_schema_history;

CREATE TABLE flyway_schema_history (
  installed_rank int NOT NULL,
  version varchar(50) DEFAULT NULL,
  description varchar(200) NOT NULL,
  type varchar(20) NOT NULL,
  script varchar(1000) NOT NULL,
  checksum int DEFAULT NULL,
  installed_by varchar(100) NOT NULL,
  installed_on timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  execution_time int NOT NULL,
  success tinyint(1) NOT NULL,
  PRIMARY KEY (installed_rank),
  KEY flyway_schema_history_s_idx (success)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- baseline 锚点 (rank=0, version=NULL, type=BASELINE)
INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success) VALUES
(0, NULL, '<< Flyway Baseline >>', 'BASELINE', '<< Flyway Baseline >>', NULL, 'manual', 0, 1);

-- V1-V16 16 条 (checksum=NULL 跳校验)
INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success) VALUES
(1,  '1',  'init book tables',                  'SQL', 'V1__init_book_tables.sql',                  NULL, 'manual', 0, 1),
(2,  '2',  'etl from raw',                      'SQL', 'V2__etl_from_raw.sql',                      NULL, 'manual', 0, 1),
(3,  '3',  'y3 favorite note folder',           'SQL', 'V3__y3_favorite_note_folder.sql',           NULL, 'manual', 0, 1),
(4,  '4',  'create free tag dict',              'SQL', 'V4__create_free_tag_dict.sql',              NULL, 'manual', 0, 1),
(5,  '5',  'etl free tag split',                'SQL', 'V5__etl_free_tag_split.sql',                NULL, 'manual', 0, 1),
(6,  '6',  'init admin question menu',          'SQL', 'V6__init_admin_question_menu.sql',          NULL, 'manual', 0, 1),
(7,  '7',  'add sys user grade school',         'SQL', 'V7__add_sys_user_grade_school.sql',         NULL, 'manual', 0, 1),
(8,  '8',  'add text content and tag knowledge','SQL', 'V8__add_text_content_and_tag_knowledge.sql',NULL, 'manual', 0, 1),
(9,  '9',  'etl text content and tag knowledge','SQL', 'V9__etl_text_content_and_tag_knowledge.sql',NULL, 'manual', 0, 1),
(10, '10', 'drop tag knowledge use realtime',   'SQL', 'V10__drop_tag_knowledge_use_realtime.sql',  NULL, 'manual', 0, 1),
(11, '11', 'alter biz question schema v1',      'SQL', 'V11__alter_biz_question_schema_v1.sql',     NULL, 'manual', 0, 1),
(12, '12', 'create biz question annotation',    'SQL', 'V12__create_biz_question_annotation.sql',   NULL, 'manual', 0, 1),
(13, '13', 'seed anno dicts',                   'SQL', 'V13__seed_anno_dicts.sql',                  NULL, 'manual', 0, 1),
(14, '14', 'backfill is collected misikt',      'SQL', 'V14__backfill_is_collected_misikt.sql',     NULL, 'manual', 0, 1),
(15, '15', 'drop dead fields v1',               'SQL', 'V15__drop_dead_fields_v1.sql',              NULL, 'manual', 0, 1),
(16, '16', 'rename subject 3071 misikt v1010',  'SQL', 'V16__rename_subject_3071_misikt_v1010.sql', NULL, 'manual', 0, 1);

-- 验收
SELECT installed_rank, version, description, type, success FROM flyway_schema_history ORDER BY installed_rank;
-- 预期 17 行: rank 0=BASELINE + 1-16=V1-V16, success 全 1
