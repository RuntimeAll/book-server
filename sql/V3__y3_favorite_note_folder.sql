-- ============================================================
-- V3 · Y3 卡：收藏 + 笔记 + 收藏夹分类树 (3 张 biz_* 表)
-- 数据库: MySQL 8.0+，字符集 utf8mb4
-- 目标库: miskt_data2 (本机 127.0.0.1:3307)
-- 目标框架: RuoYi-Vue-Plus 5.6.1 (Spring Boot 3.5 / Java 17 / Sa-Token)
--
-- 端点对照 (PRD §3.1):
--   biz_question_favorite ← V-1/V-2/V-12 (GET/POST/DELETE /teacher/qd/favorite/{id})
--   biz_question_note     ← V-3/V-4      (GET/POST /teacher/qd/note/{id})
--   biz_question_folder   ← V-7          (GET /teacher/center/q-folder/tree)
--
-- 🔴 升级 V1 占位表 (开发组长授权 2026-05-21):
--   V1 已建占位 biz_question_favorite / biz_question_note，结构 PK=(user_id, question_id)、
--   无自增 id / 无 folder_id / create_time DEFAULT NULL，与 PRD §3.2 契约不符。
--   两表当前 COUNT=0 无数据，按 V1 自身风格 (357/373 行) 改为 DROP+CREATE，
--   升级到 PRD §3.2 完整契约 (id 自增 + folder_id + uk_user_question + create_time NOT NULL)。
--   biz_question_folder 全新建。
--
-- 用法 (dev 本机执行):
--   $env:MYSQL_PWD='123456'; & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" `
--     -h 127.0.0.1 -P 3307 -u root --default-character-set=utf8mb4 miskt_data2 `
--     < V3__y3_favorite_note_folder.sql
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- 1. biz_question_favorite — 题目收藏关系
-- 升级 V1 占位 (DROP+CREATE)，COUNT=0 无数据丢失
-- ============================================================
DROP TABLE IF EXISTS biz_question_favorite;
CREATE TABLE biz_question_favorite (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL                            COMMENT '收藏者 sys_user.user_id',
  question_id BIGINT      NOT NULL                            COMMENT '题目 biz_question.id',
  folder_id   BIGINT      DEFAULT 0                           COMMENT '收藏夹 biz_question_folder.id，0=默认收藏夹',
  create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '收藏时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_question (user_id, question_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目收藏(每用户对每题至多一条)';

-- ============================================================
-- 2. biz_question_note — 题目个人备注
-- 升级 V1 占位 (DROP+CREATE)，COUNT=0 无数据丢失
-- V1 占位结构：PK=(user_id, question_id) 复合主键 + 无自增 id + create_time DEFAULT NULL
-- 用户授权 2026-05-21：方案 A — DROP+CREATE 升级到 PRD §3.2 完整契约
-- ============================================================
DROP TABLE IF EXISTS biz_question_note;
CREATE TABLE biz_question_note (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL                            COMMENT '备注归属 sys_user.user_id',
  question_id BIGINT      NOT NULL                            COMMENT '题目 biz_question.id',
  content     TEXT        NOT NULL                            COMMENT '备注内容(富文本/纯文本均可)',
  create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '首次写入时间',
  update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
                          ON UPDATE CURRENT_TIMESTAMP         COMMENT '最近修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_question (user_id, question_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目个人备注(每用户对每题至多一条)';

-- ============================================================
-- 3. biz_question_folder — 收藏夹分类树 (全新表，无冲突)
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_question_folder (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL                            COMMENT '归属 sys_user.user_id',
  name        VARCHAR(64) NOT NULL                            COMMENT '收藏夹名称',
  pid         BIGINT      DEFAULT 0                           COMMENT '父收藏夹 id，0=根',
  sort        INT         DEFAULT 0                           COMMENT '同级排序',
  create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_user_pid (user_id, pid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏夹分类树(本卡可 mock 单条默认夹)';
