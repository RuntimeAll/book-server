-- ============================================================
-- V1 · misikt 教师端业务库 schema 初始化（22 张 biz_* 表）
-- 数据库: MySQL 8.0+，字符集 utf8mb4
-- 目标库: miskt_data2 (本机 127.0.0.1:3307)
-- 目标框架: RuoYi-Vue-Plus 5.6.1 (Spring Boot 3.5 / Java 17 / Sa-Token)
-- 学生与教师共用 sys_user 表（role 区分），不单独建 biz_student
--
-- schema 源: 数据建模/07-补充资料/K-最终建表SQL.sql（含 M3/M4 ALTER 合并）
--
-- 用法（dev 本机初始化）:
--   mysql -h 127.0.0.1 -P 3307 -u root -p123456 \
--     --default-character-set=utf8mb4 miskt_data2 < V1__init_book_tables.sql
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. 教材 / 章节 / 知识点（一张树表）
-- ============================================================
DROP TABLE IF EXISTS biz_subject;
CREATE TABLE biz_subject (
  id              VARCHAR(20)   NOT NULL COMMENT '层级数字编码，每3位一层；根=学段+学科',
  parent_id       VARCHAR(20)   DEFAULT NULL,
  name            VARCHAR(200)  NOT NULL,
  level           TINYINT       NOT NULL COMMENT '1学科 2教材 3章 4节 5知识点',
  sort            INT           DEFAULT 0,
  knowledge_img   VARCHAR(500)  DEFAULT NULL COMMENT '知识点配图(叶子)',
  knowledge_video VARCHAR(500)  DEFAULT NULL COMMENT '知识点微课视频URL(叶子)',
  is_share        TINYINT       DEFAULT 0,
  status          CHAR(1)       DEFAULT '0' COMMENT '0正常 1停用',
  create_by       VARCHAR(64)   DEFAULT '',
  create_time     DATETIME      DEFAULT NULL,
  update_by       VARCHAR(64)   DEFAULT '',
  update_time     DATETIME      DEFAULT NULL,
  remark          VARCHAR(500)  DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_parent (parent_id),
  KEY idx_level (level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教材-章节-知识点树';

-- biz_subject 顶层 seed（仅浙教数学；3 层及以下叶子知识点等 ETL 从 raw_question_knowledge 反推灌库）
INSERT INTO biz_subject (id, parent_id, name, level, sort, status, create_by, create_time) VALUES
  ('3071',    '0',    '浙教版数学',     1, 1, '0', 'system', NOW()),
  ('3071001', '3071', '七年级上册',     2, 1, '0', 'system', NOW()),
  ('3071002', '3071', '七年级下册',     2, 2, '0', 'system', NOW()),
  ('3071003', '3071', '八年级上册',     2, 3, '0', 'system', NOW()),
  ('3071004', '3071', '八年级下册',     2, 4, '0', 'system', NOW()),
  ('3071005', '3071', '九年级上册',     2, 5, '0', 'system', NOW()),
  ('3071006', '3071', '九年级下册',     2, 6, '0', 'system', NOW()),
  ('3071007', '3071', '中考一轮复习',   2, 7, '0', 'system', NOW()),
  ('3071008', '3071', '中考二轮复习',   2, 8, '0', 'system', NOW());

-- ============================================================
-- 2. 题目
-- ============================================================
DROP TABLE IF EXISTS biz_question;
CREATE TABLE biz_question (
  id                       BIGINT         NOT NULL AUTO_INCREMENT,
  question_type            TINYINT        NOT NULL COMMENT '1选择 2填空 3判断 4计算 5解答',
  difficult                TINYINT        NOT NULL COMMENT '1-4星',
  subject_id               VARCHAR(20)    NOT NULL COMMENT '关联 biz_subject',

  short_title              VARCHAR(200)   DEFAULT NULL,
  stem_text                MEDIUMTEXT     COMMENT '题干 LaTeX 源 / 纯文本',
  stem_img_url             VARCHAR(500)   COMMENT '题干渲染图(可选缓存)',
  answer_img_url           VARCHAR(500),
  explain_img_url          VARCHAR(500),
  file_bin_url             VARCHAR(500)   COMMENT '原站 data.bin(笔迹层,不实现)',
  video_url                VARCHAR(500),

  options_json             JSON           COMMENT '选项 [{"key":"A","content":"..."}]',
  correct_answer           VARCHAR(200)   COMMENT 'A/B/C/D 或文本',
  score_std_json           JSON           COMMENT '评分标准(主观题)',

  exam_year                VARCHAR(10),
  exam_paper_id            BIGINT         COMMENT '出处试卷id 冗余',
  exam_paper_name          VARCHAR(200),
  analyze_text_content_id  BIGINT         DEFAULT NULL,

  free_tag                 VARCHAR(500)   COMMENT '逗号分隔标签',
  question_std_knowledge_str VARCHAR(500) COMMENT '标准知识点字符串冗余',
  dedup_kind               VARCHAR(50)    COMMENT 'similar/duplicate/variant',

  is_share                 TINYINT        DEFAULT 0,
  is_repeat                TINYINT        DEFAULT 0,
  repeat_question_id       BIGINT         DEFAULT NULL,
  version                  INT            DEFAULT 1010 COMMENT '题目格式版本码',
  status                   CHAR(1)        DEFAULT '0' COMMENT '0草稿 1发布 2软删',

  create_by                VARCHAR(64)    DEFAULT '' COMMENT '若依用户名',
  create_user              BIGINT         DEFAULT NULL COMMENT '录入者 sys_user.id',
  create_time              DATETIME       DEFAULT NULL,
  update_by                VARCHAR(64)    DEFAULT '',
  update_time              DATETIME       DEFAULT NULL,
  remark                   VARCHAR(500)   DEFAULT NULL,

  PRIMARY KEY (id),
  KEY idx_subject (subject_id),
  KEY idx_type_diff (question_type, difficult),
  KEY idx_exam_paper (exam_paper_id),
  KEY idx_create_time (create_time),
  KEY idx_create_user (create_user)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目主表';

-- 中文全文索引（注意 my.cnf 需配 ngram_token_size=2 — 跑不过就忽略，B 卡 V-2 关键字筛选可走 LIKE）
ALTER TABLE biz_question ADD FULLTEXT KEY ft_stem (stem_text) WITH PARSER ngram;

-- ============================================================
-- 3. 题目-知识点 关联（U=用户挂 S=标准库挂）
-- ============================================================
DROP TABLE IF EXISTS biz_question_knowledge;
CREATE TABLE biz_question_knowledge (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  question_id   BIGINT       NOT NULL,
  knowledge_id  VARCHAR(20)  NOT NULL COMMENT '关联 biz_subject.id（叶子）',
  source        CHAR(1)      NOT NULL DEFAULT 'U' COMMENT 'U用户 S标准',
  create_time   DATETIME     DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_qk_src (question_id, knowledge_id, source),
  KEY idx_knowledge (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目-知识点 M:N';

-- ============================================================
-- 4. 试卷分类树（M4 新增）
-- ============================================================
DROP TABLE IF EXISTS biz_paper_category;
CREATE TABLE biz_paper_category (
  id          VARCHAR(20)  NOT NULL COMMENT '4位数字编码 3001/3003/3004 等',
  parent_id   VARCHAR(20)  DEFAULT '0',
  name        VARCHAR(200) NOT NULL,
  sort        INT          DEFAULT 0,
  is_share    TINYINT      DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷分类(独立于章节树)';

INSERT INTO biz_paper_category (id, parent_id, name, sort, is_share) VALUES
  ('3003', '0', '资料库', 1, 0),
  ('3003001', '3003', '期中卷', 1, 0),
  ('3003002', '3003', '期末卷', 2, 0),
  ('3003003', '3003', '月考卷', 3, 0),
  ('3001', '0', '公共试卷', 2, 0),
  ('3001001', '3001', '七年级', 1, 0),
  ('3001002', '3001', '八年级', 2, 0),
  ('3001003', '3001', '九年级', 3, 0),
  ('3004', '0', '专题卷库', 3, 0),
  ('3004001', '3004', '函数专题', 1, 0),
  ('3004002', '3004', '几何专题', 2, 0);

-- ============================================================
-- 5. 试卷主表
-- ============================================================
DROP TABLE IF EXISTS biz_paper;
CREATE TABLE biz_paper (
  id                      BIGINT         NOT NULL AUTO_INCREMENT,
  name                    VARCHAR(200)   NOT NULL,
  subject_id              VARCHAR(20)    DEFAULT NULL COMMENT '教材冗余',
  paper_category_id       VARCHAR(20)    DEFAULT NULL COMMENT 'M4 新增 试卷分类',
  directory_name          VARCHAR(200)   DEFAULT NULL,
  question_count          INT            DEFAULT 0 COMMENT '冗余',
  score                   DECIMAL(6,2)   DEFAULT 0,
  suggest_time            INT            COMMENT '建议时长(分钟)',
  hg_score                DECIMAL(6,2)   COMMENT '合格分',
  paper_type              TINYINT        DEFAULT 1 COMMENT '1手工 2自动',
  frame_text_content_id   BIGINT         DEFAULT NULL,
  exam_year               VARCHAR(10),
  is_share                TINYINT        DEFAULT 0,
  status                  CHAR(1)        DEFAULT '0' COMMENT '0草稿 1发布 2软删',
  sort                    INT            DEFAULT 0,

  create_by               VARCHAR(64)    DEFAULT '',
  create_time             DATETIME       DEFAULT NULL,
  update_by               VARCHAR(64)    DEFAULT '',
  update_time             DATETIME       DEFAULT NULL,
  remark                  VARCHAR(500)   DEFAULT NULL,

  PRIMARY KEY (id),
  KEY idx_subject (subject_id),
  KEY idx_category (paper_category_id),
  KEY idx_creator (create_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷主表';

-- ============================================================
-- 5.1 试卷题目分组
-- ============================================================
DROP TABLE IF EXISTS biz_paper_section;
CREATE TABLE biz_paper_section (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  paper_id    BIGINT       NOT NULL,
  title       VARCHAR(50)  NOT NULL COMMENT '选择题/填空题/解答题',
  sort        INT          NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_paper_section_sort (paper_id, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷题目分组';

-- ============================================================
-- 5.2 试卷-题目
-- ============================================================
DROP TABLE IF EXISTS biz_paper_question;
CREATE TABLE biz_paper_question (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  paper_id     BIGINT       NOT NULL,
  section_id   BIGINT       NOT NULL,
  question_id  BIGINT       NOT NULL,
  sort         INT          NOT NULL COMMENT '组内顺序',
  score        DECIMAL(5,2) DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_section_sort (section_id, sort),
  KEY idx_paper (paper_id),
  KEY idx_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷-题目';

-- ============================================================
-- 5.3 试卷导入历史
-- ============================================================
DROP TABLE IF EXISTS biz_exam_paper_import;
CREATE TABLE biz_exam_paper_import (
  id                BIGINT        NOT NULL AUTO_INCREMENT,
  exam_paper_name   VARCHAR(200)  NOT NULL,
  question_num      INT           DEFAULT 0,
  error_num         INT           DEFAULT 0,
  import_time       DATETIME      DEFAULT NULL,
  status            TINYINT       DEFAULT 1 COMMENT '1成功 0失败',
  generated_paper_id BIGINT       DEFAULT NULL,
  create_by         VARCHAR(64)   DEFAULT '',
  create_time       DATETIME      DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_creator_time (create_by, import_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷导入历史';

-- ============================================================
-- 6. 班级
-- ============================================================
DROP TABLE IF EXISTS biz_class;
CREATE TABLE biz_class (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  class_name     VARCHAR(100) NOT NULL COMMENT '班级名',
  grade          INT          NOT NULL COMMENT '7初一 8初二 9初三',
  grade_name     VARCHAR(20)  NOT NULL COMMENT '初一/初二/初三',
  grade_code     VARCHAR(50)  NOT NULL COMMENT 'UUID 邀请码',
  school         VARCHAR(100),
  teacher_id     BIGINT       NOT NULL,
  subject_id     VARCHAR(20)  COMMENT '默认教材',
  student_count  INT          DEFAULT 0,
  status         CHAR(1)      DEFAULT '0' COMMENT '0正常 1停用 2软删',
  create_by      VARCHAR(64)  DEFAULT '',
  create_time    DATETIME     DEFAULT NULL,
  update_by      VARCHAR(64)  DEFAULT '',
  update_time    DATETIME     DEFAULT NULL,
  remark         VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_grade_code (grade_code),
  KEY idx_teacher (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级';

-- ============================================================
-- 6.1 班级-学生
-- ============================================================
DROP TABLE IF EXISTS biz_class_student;
CREATE TABLE biz_class_student (
  class_id    BIGINT NOT NULL,
  user_id     BIGINT NOT NULL COMMENT 'sys_user.user_id, role=1=学生',
  join_time   DATETIME DEFAULT NULL,
  PRIMARY KEY (class_id, user_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级-学生 M:N';

-- ============================================================
-- 7. 作业
-- ============================================================
DROP TABLE IF EXISTS biz_task;
CREATE TABLE biz_task (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  name          VARCHAR(200) NOT NULL,
  paper_id      BIGINT       NOT NULL,
  class_id      BIGINT       NOT NULL,
  publish_time  DATETIME,
  deadline      DATETIME,
  total_score   DECIMAL(6,2),
  status        TINYINT      DEFAULT 0 COMMENT '0草稿 1发布 2结束',
  finish_count  INT          DEFAULT 0,
  pending_grade INT          DEFAULT 0,
  create_by     VARCHAR(64)  DEFAULT '',
  create_time   DATETIME     DEFAULT NULL,
  update_by     VARCHAR(64)  DEFAULT '',
  update_time   DATETIME     DEFAULT NULL,
  remark        VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_paper (paper_id),
  KEY idx_class (class_id),
  KEY idx_deadline (deadline),
  KEY idx_status_deadline (status, deadline),
  KEY idx_creator (create_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业';

-- ============================================================
-- 7.1 学生作业提交
-- ============================================================
DROP TABLE IF EXISTS biz_task_submission;
CREATE TABLE biz_task_submission (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  task_id       BIGINT       NOT NULL,
  user_id       BIGINT       NOT NULL,
  answers_json  JSON         COMMENT '{"q123":"B"}',
  score         DECIMAL(6,2),
  status        TINYINT      DEFAULT 0 COMMENT '0未交 1已交 2已批',
  submit_time   DATETIME,
  correct_time  DATETIME,
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_user (task_id, user_id),
  KEY idx_user (user_id),
  KEY idx_task_status (task_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生作业提交';

-- ============================================================
-- 7.2 答题明细（最大表）
-- ============================================================
DROP TABLE IF EXISTS biz_task_question_answer;
CREATE TABLE biz_task_question_answer (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  submission_id   BIGINT       NOT NULL,
  question_id     BIGINT       NOT NULL,
  student_answer  TEXT,
  is_correct      TINYINT,
  score           DECIMAL(5,2),
  PRIMARY KEY (id),
  KEY idx_submission (submission_id),
  KEY idx_question (question_id),
  KEY idx_question_correct (question_id, is_correct)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答题明细';

-- ============================================================
-- 8. 试题筐 / 试卷筐
-- ============================================================
DROP TABLE IF EXISTS biz_question_basket;
CREATE TABLE biz_question_basket (
  user_id      BIGINT   NOT NULL,
  question_id  BIGINT   NOT NULL,
  add_time     DATETIME,
  PRIMARY KEY (user_id, question_id),
  KEY idx_user_time (user_id, add_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试题筐';

DROP TABLE IF EXISTS biz_paper_basket;
CREATE TABLE biz_paper_basket (
  user_id    BIGINT   NOT NULL,
  paper_id   BIGINT   NOT NULL,
  add_time   DATETIME,
  PRIMARY KEY (user_id, paper_id),
  KEY idx_user_time (user_id, add_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷筐';

-- ============================================================
-- 9. 收藏 / 笔记 / 错题本
-- ============================================================
DROP TABLE IF EXISTS biz_question_favorite;
CREATE TABLE biz_question_favorite (
  user_id      BIGINT NOT NULL,
  question_id  BIGINT NOT NULL,
  create_time  DATETIME,
  PRIMARY KEY (user_id, question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目收藏';

DROP TABLE IF EXISTS biz_paper_favorite;
CREATE TABLE biz_paper_favorite (
  user_id      BIGINT NOT NULL,
  paper_id     BIGINT NOT NULL,
  create_time  DATETIME,
  PRIMARY KEY (user_id, paper_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷收藏';

DROP TABLE IF EXISTS biz_question_note;
CREATE TABLE biz_question_note (
  user_id      BIGINT NOT NULL,
  question_id  BIGINT NOT NULL,
  content      TEXT,
  update_time  DATETIME,
  PRIMARY KEY (user_id, question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目笔记';

DROP TABLE IF EXISTS biz_question_wrong;
CREATE TABLE biz_question_wrong (
  user_id        BIGINT NOT NULL,
  question_id    BIGINT NOT NULL,
  wrong_count    INT DEFAULT 1,
  last_wrong_at  DATETIME,
  resolved       TINYINT DEFAULT 0,
  PRIMARY KEY (user_id, question_id),
  KEY idx_last_wrong (last_wrong_at),
  KEY idx_user_resolved (user_id, resolved)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错题本';

-- ============================================================
-- 10. 资料库
-- ============================================================
DROP TABLE IF EXISTS biz_material_section;
CREATE TABLE biz_material_section (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  name         VARCHAR(50)  NOT NULL,
  subject_code VARCHAR(20)  NOT NULL,
  sort         INT          DEFAULT 0,
  status       CHAR(1)      DEFAULT '0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_subject_name (subject_code, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资料库栏目';

DROP TABLE IF EXISTS biz_material;
CREATE TABLE biz_material (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  name            VARCHAR(200) NOT NULL,
  material_type   VARCHAR(20)  COMMENT 'PDF/PPT/视频/课件',
  subject_code    VARCHAR(20)  COMMENT 'math/chinese/english/science/social',
  section_id      BIGINT       COMMENT 'biz_material_section.id',
  file_url        VARCHAR(500),
  file_size       BIGINT,
  file_type       VARCHAR(20),
  download_count  INT          DEFAULT 0,
  is_hot          TINYINT      DEFAULT 0,
  is_share        TINYINT      DEFAULT 1,
  status          CHAR(1)      DEFAULT '0',
  create_by       VARCHAR(64)  DEFAULT '',
  create_time     DATETIME     DEFAULT NULL,
  update_by       VARCHAR(64)  DEFAULT '',
  update_time     DATETIME     DEFAULT NULL,
  remark          VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_subject_section (subject_code, section_id),
  KEY idx_hot (is_hot, download_count),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资料库文件';

-- ============================================================
-- 11. 配置（关闭验证码，方便 dev 调试）
-- ============================================================
UPDATE sys_config SET config_value='false' WHERE config_key='sys.account.captchaEnabled';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 后置脚本提示:
--   本 V1 只灌:
--     - 22 张 biz_* 表 schema
--     - biz_subject 9 行顶层 seed（浙教版数学 + 8 册教材占位）
--     - biz_paper_category 11 行试卷分类树初始数据
--     - 关闭 RuoYi 默认验证码
--   ETL 从 raw_*（miskt_data2.raw_question_page / raw_paper / raw_question_knowledge / raw_paper_question）灌
--     biz_question / biz_paper / biz_question_knowledge / biz_paper_section + biz_paper_question / biz_subject 叶子
--     等下一个 V 文件做（V2__etl_from_raw.sql）
-- ============================================================
