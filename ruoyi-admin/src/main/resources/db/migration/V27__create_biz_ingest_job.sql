-- PRD-A-002 路B 批量上传录题：作业表 + 拆出题暂存子表。A 线顺序号，接 V26 → V27。
--
-- 设计（PRD-A-002 §6 已定实现取向）：
--   · biz_ingest_job        = 一次"上传文件→异步拆题"作业（状态机 PENDING→EXTRACT_ING→SPLIT_ING→DONE/FAILED）。
--   · biz_ingest_job_item   = 该作业 opus 拆出的题（暂存）；老师在审核页勾选的才 ingestQuestion 进正式
--                             biz_question（status='0' 草稿），没勾的随作业留存/清理 —— 正式题表只进确认题，不污染。
--   · 归属：teacher_id 冗余（= create_user），job/item 查改删入库均比对当前登录（防越权 C5）。
-- 纯增量（CREATE TABLE，无 DROP）。id 走 RuoYi 雪花（MyBatis-Plus ASSIGN_ID，非自增）。

CREATE TABLE IF NOT EXISTS `biz_ingest_job` (
    `id`              BIGINT        NOT NULL                       COMMENT '作业ID（雪花）',
    `teacher_id`      BIGINT        NOT NULL                       COMMENT '归属老师ID（=create_user 冗余，归属校验用）',
    `subject_id`      VARCHAR(20)   NOT NULL                       COMMENT '绑定章节节点 biz_subject.id（整批粗挂）',
    `source_file_name` VARCHAR(255)  DEFAULT NULL                  COMMENT '上传文件名',
    `source_oss_url`  VARCHAR(1024) DEFAULT NULL                   COMMENT '上传文件/图 OSS 地址（原文件留存）',
    `source_type`     VARCHAR(16)   DEFAULT NULL                   COMMENT '来源类型 image/pdf/docx/text',
    `lane`            VARCHAR(8)    DEFAULT NULL                    COMMENT '分档 fast 快档(文字层)/slow 慢档(页图)',
    `answer_mode`     VARCHAR(16)   NOT NULL DEFAULT 'from_source' COMMENT '答案模式 from_source原卷自带/ai_solve AI解题/stem_only只录题',
    `commit_mode`     VARCHAR(16)   NOT NULL DEFAULT 'review'      COMMENT '入库方式 review审核后/direct直接',
    `grade_hint`      VARCHAR(32)   DEFAULT NULL                   COMMENT '学段提示（约束解法不超纲，可空）',
    `status`          VARCHAR(16)   NOT NULL DEFAULT 'PENDING'     COMMENT '状态 PENDING/EXTRACT_ING/SPLIT_ING/DONE/FAILED',
    `error_msg`       VARCHAR(1024) DEFAULT NULL                   COMMENT '失败原因（FAILED 时带因）',
    `question_count`  INT           NOT NULL DEFAULT 0             COMMENT '拆出题数',
    `committed_count` INT           NOT NULL DEFAULT 0             COMMENT '已入库题数',
    `dropped_json`    TEXT          DEFAULT NULL                   COMMENT '完整度过滤丢弃项（JSON 数组，可读原因）',
    `create_dept`     BIGINT        DEFAULT NULL                   COMMENT '创建部门',
    `create_by`       VARCHAR(64)   DEFAULT NULL                   COMMENT '创建者',
    `create_user`     BIGINT        DEFAULT NULL                   COMMENT '创建用户ID',
    `create_time`     DATETIME      DEFAULT NULL                   COMMENT '创建时间',
    `update_by`       VARCHAR(64)   DEFAULT NULL                   COMMENT '更新者',
    `update_time`     DATETIME      DEFAULT NULL                   COMMENT '更新时间',
    `remark`          VARCHAR(500)  DEFAULT NULL                   COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_job_teacher_status` (`teacher_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='录题批量作业（PRD-A-002 路B）';

CREATE TABLE IF NOT EXISTS `biz_ingest_job_item` (
    `id`                    BIGINT      NOT NULL                 COMMENT '拆出题ID（雪花）',
    `job_id`                BIGINT      NOT NULL                 COMMENT '所属作业 biz_ingest_job.id',
    `seq`                   INT         NOT NULL DEFAULT 0       COMMENT '题序',
    `stem_text`             MEDIUMTEXT  DEFAULT NULL             COMMENT '题干（Markdown+$LaTeX$）',
    `question_type`         INT         DEFAULT NULL             COMMENT '题型 1选择/2填空/5解答',
    `options_json`          TEXT        DEFAULT NULL             COMMENT '选项数组（JSON）',
    `answer_text`           MEDIUMTEXT  DEFAULT NULL             COMMENT '答案（from_source/ai_solve 时有）',
    `analyze_text`          MEDIUMTEXT  DEFAULT NULL             COMMENT '解析',
    `has_figure`            TINYINT     NOT NULL DEFAULT 0       COMMENT '含图 0/1',
    `difficulty`            INT         DEFAULT NULL             COMMENT '难度 1-3',
    `dna_json`              TEXT        DEFAULT NULL             COMMENT '10维DNA（ai_solve 时，JSON）',
    `verify_verdict`        VARCHAR(16) DEFAULT NULL             COMMENT 'sympy 验算 pass/fail/degrade',
    `need_review`           TINYINT     NOT NULL DEFAULT 0       COMMENT '待审软提示 0/1',
    `item_status`           VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending待审/committed已入库/dropped已弃',
    `committed_question_id` BIGINT      DEFAULT NULL             COMMENT '入库后回填 biz_question.id',
    `create_time`           DATETIME    DEFAULT NULL             COMMENT '创建时间',
    `update_time`           DATETIME    DEFAULT NULL             COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_item_job_status` (`job_id`, `item_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='录题作业拆出题暂存（PRD-A-002 路B，勾选才进 biz_question）';
