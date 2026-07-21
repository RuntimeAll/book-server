-- =====================================================================
-- 收敛DDL-2026-07.sql · book-ai 业务库 schema 唯一事实源(SSOT)
-- 生成: 2026-07-02 · PRD-C-206 P3b(初始化完成态导出)
-- 🔴 Flyway 已废止(C线,2026-07-02 维护者定):上线=空库全量新建,按本文件建业务表。
--    RuoYi 框架表(sys_*/flow_*等)走 RuoYi 官方初始化脚本,不在本文件。
--    改表结构 = 先改定稿文档(only-one/三位一体数据结构-定稿.md + 题目DNA与解法模型-定版.md)
--    → 改 dev 库 → 重导本文件。禁止绕过本链路手改 prod。
-- 业务字典种子(sys_dict biz_%)附文末,建库后需灌入并 refreshCache。
-- =====================================================================

-- ----- biz_anchor_worklist -----
DROP TABLE IF EXISTS biz_anchor_worklist;
CREATE TABLE `biz_anchor_worklist` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question_id` bigint NOT NULL,
  `dim1_kp_id` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `issue` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'coarse粗锚(节以上)/empty空锚/dirty脏编码',
  `note` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` char(1) COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '0待处理1已下沉',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_q` (`question_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1720 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PRD-C-206 P2b 锚定整改工作表:认知包打标管线的首批任务源';

-- ----- biz_billing_event -----
DROP TABLE IF EXISTS biz_billing_event;
CREATE TABLE `biz_billing_event` (
  `event_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '一次请求全链路 ID',
  `teacher_id` bigint DEFAULT NULL COMMENT '老师 ID(打标任务可空)',
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'label_job/admin_preview/老师对话(后期)',
  `source_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'job_id 或 thread_id 等',
  `provider` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实际调的 provider',
  `model` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `prompt_tokens` int DEFAULT '0',
  `completion_tokens` int DEFAULT '0',
  `fallback_count` int DEFAULT '0' COMMENT '此次切换了几次 provider',
  `service_tier` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'base' COMMENT 'base/premium',
  `is_billable` tinyint(1) DEFAULT '0' COMMENT '是否计入账单',
  `started_at` datetime(3) DEFAULT NULL,
  `ended_at` datetime(3) DEFAULT NULL,
  `duration_ms` int DEFAULT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ok/error',
  `error_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`event_id`),
  KEY `idx_teacher_time` (`teacher_id`,`started_at`),
  KEY `idx_source` (`source_type`,`source_id`),
  KEY `idx_provider` (`provider`,`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM 调用 / 服务计费埋点(第一期只埋点不扣费)';

-- ----- biz_book -----
DROP TABLE IF EXISTS biz_book;
CREATE TABLE `biz_book` (
  `id` varchar(8) NOT NULL,
  `subject_name` varchar(32) DEFAULT NULL COMMENT '学科',
  `grade` varchar(8) DEFAULT NULL COMMENT '年级(稳定维度)',
  `term` varchar(4) DEFAULT NULL COMMENT '学期上/下(稳定维度)',
  `edition` varchar(32) DEFAULT NULL COMMENT '版本(随教材更替):浙教版/人教版',
  `base_edition` varchar(32) DEFAULT NULL COMMENT '教辅跟哪版课本',
  `book_type` varchar(8) DEFAULT NULL COMMENT '课本/教辅',
  `series` varchar(64) DEFAULT NULL COMMENT '系列:2026必刷题',
  `school_year` varchar(16) DEFAULT NULL COMMENT '适用学年',
  `publisher` varchar(64) DEFAULT NULL,
  `full_name` varchar(128) DEFAULT NULL COMMENT '全名',
  `cover_url` varchar(255) DEFAULT NULL,
  `status` char(1) DEFAULT '0' COMMENT '0正常1停用',
  `remark` varchar(255) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教材版本SSOT(版本年年更替绑此表;年级/学段=稳定维度)';

-- ----- biz_book_kp_map -----
DROP TABLE IF EXISTS biz_book_kp_map;
CREATE TABLE `biz_book_kp_map` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `book_id` varchar(8) NOT NULL,
  `book_kp_id` varchar(24) NOT NULL COMMENT '教辅自有KP(biz_book_subject.id)',
  `master_kp_id` varchar(24) NOT NULL COMMENT '→biz_subject.id(master)',
  `rel` varchar(8) DEFAULT '等同' COMMENT '等同/细分/合并',
  `confidence` decimal(4,3) DEFAULT '1.000',
  `source` varchar(8) DEFAULT 'seed' COMMENT 'seed/LLM/人工',
  `review_status` tinyint DEFAULT '1' COMMENT '0待评审 1通过',
  PRIMARY KEY (`id`),
  KEY `idx_book` (`book_id`),
  KEY `idx_bkp` (`book_kp_id`),
  KEY `idx_mkp` (`master_kp_id`)
) ENGINE=InnoDB AUTO_INCREMENT=256 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教辅图谱↔主图谱映射(必刷题=种子1:1等同;主图谱靠它对齐生长)';

-- ----- biz_book_question -----
DROP TABLE IF EXISTS biz_book_question;
CREATE TABLE `biz_book_question` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `book_id` varchar(8) NOT NULL COMMENT '教材版本→biz_book.id',
  `question_id` bigint NOT NULL COMMENT '→biz_question.id(题不反向引用book)',
  `container_type` varchar(8) DEFAULT NULL COMMENT 'section正文段/topic章末专题/paper试卷',
  `container_id` varchar(16) DEFAULT NULL COMMENT '→对应容器表id',
  `column_type` varchar(16) DEFAULT NULL COMMENT '栏目(这本教辅的摆法)',
  `book_difficulty` varchar(8) DEFAULT NULL COMMENT '书印难度(这本教辅的话)',
  `role` varchar(8) DEFAULT NULL COMMENT '母题/变式/类型例题/项目题',
  `in_block_seq` int DEFAULT NULL COMMENT '块内序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_book` (`book_id`),
  KEY `idx_q` (`question_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2069819648940986399 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教辅对题的摆放(教辅侧拥有;删book级联;题对book零引用)';

-- ----- biz_book_section -----
DROP TABLE IF EXISTS biz_book_section;
CREATE TABLE `biz_book_section` (
  `id` varchar(12) NOT NULL COMMENT 'BS0001..',
  `book_id` varchar(6) DEFAULT NULL COMMENT '教材版本→biz_book.id',
  `chapter_subject_id` varchar(20) DEFAULT NULL COMMENT '挂的章 biz_subject(L3)',
  `section_subject_id` varchar(20) DEFAULT NULL COMMENT '挂的小节/课时/知识点 biz_subject(L4/5/6)',
  `column_type` varchar(16) NOT NULL COMMENT '栏目段类型:刷基础/刷提升/刷易错/刷素养/刷中考',
  `seq` int NOT NULL COMMENT '?本段在所属小节内的顺序(基础<提升<素养<易错)',
  `title` varchar(100) DEFAULT NULL COMMENT '段标题(如"刷基础")',
  `source_book` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_chapter` (`chapter_subject_id`),
  KEY `idx_section_subject` (`section_subject_id`),
  KEY `idx_column` (`column_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='?正文小节栏目段容器(组书顺序骨架)';

-- ----- biz_book_subject -----
DROP TABLE IF EXISTS biz_book_subject;
CREATE TABLE `biz_book_subject` (
  `id` varchar(20) NOT NULL COMMENT '层级数字编码,每3位一层;根=学段+学科',
  `book_id` varchar(8) DEFAULT NULL,
  `parent_id` varchar(20) DEFAULT NULL,
  `name` varchar(200) NOT NULL,
  `level` tinyint NOT NULL COMMENT '1学科 2教材 3章 4节 5课时 6知识点',
  `sort` int DEFAULT '0',
  `status` char(1) DEFAULT '0' COMMENT '0正常 1停用',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`),
  KEY `idx_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='书图谱备份(biz_subject快照,id同级绑定;本书版本层,将来承载书特有编排/综测挂载)';

-- ----- biz_chapter_figure_map -----
DROP TABLE IF EXISTS biz_chapter_figure_map;
CREATE TABLE `biz_chapter_figure_map` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `chapter_keyword` varchar(64) NOT NULL COMMENT '章节/考点主题关键词（toolkit 用母题章节名/考点名包含匹配它）',
  `figure_type` varchar(32) NOT NULL COMMENT '图型 code（英文 code，不翻译）',
  `note` varchar(128) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  PRIMARY KEY (`id`),
  KEY `idx_chapter_keyword` (`chapter_keyword`)
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='章节×图型映射（举一反三造图定型闸，PRD-A-021 R3b）';

-- ----- biz_dna_edit_log -----
DROP TABLE IF EXISTS biz_dna_edit_log;
CREATE TABLE `biz_dna_edit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `teacher_id` bigint NOT NULL COMMENT '操作老师 user_id',
  `target_id` varchar(64) DEFAULT NULL COMMENT '母题/变式 id 或 thread_id',
  `edit_kind` varchar(16) NOT NULL COMMENT '编辑类型: dna维 / 图修正',
  `dim` varchar(32) DEFAULT NULL COMMENT '改的哪一维(dna维时)',
  `before_val` text COMMENT '改前值',
  `after_val` text COMMENT '改后值',
  `correction_prompt` text COMMENT '图修正提示词',
  `committed` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否入库(1=已入库)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_teacher_created` (`teacher_id`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='PRD-C-100 经验层留痕(只累计不消费)';

-- ----- biz_exam_paper -----
DROP TABLE IF EXISTS biz_exam_paper;
CREATE TABLE `biz_exam_paper` (
  `id` varchar(16) NOT NULL COMMENT 'EP01..',
  `book_id` varchar(6) DEFAULT NULL COMMENT '教材版本→biz_book.id',
  `kind` tinyint NOT NULL COMMENT '1全章综合训练 2章测',
  `chapter_subject_id` varchar(20) DEFAULT NULL COMMENT '挂的章 L3',
  `title` varchar(100) NOT NULL,
  `total_score` int DEFAULT NULL COMMENT '满分(章测100)',
  `duration_min` int DEFAULT NULL COMMENT '建议用时(分)',
  `source_book` varchar(64) DEFAULT NULL,
  `sort` int DEFAULT '0',
  `status` char(1) NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kind` (`kind`),
  KEY `idx_chapter` (`chapter_subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='试卷(全章综合/章测)';

-- ----- biz_exam_paper_import -----
DROP TABLE IF EXISTS biz_exam_paper_import;
CREATE TABLE `biz_exam_paper_import` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `exam_paper_name` varchar(200) NOT NULL,
  `question_num` int DEFAULT '0',
  `error_num` int DEFAULT '0',
  `import_time` datetime DEFAULT NULL,
  `status` tinyint DEFAULT '1' COMMENT '1成功 0失败',
  `generated_paper_id` bigint DEFAULT NULL,
  `create_by` varchar(64) DEFAULT '',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_creator_time` (`create_by`,`import_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='试卷导入历史';

-- ----- biz_exam_paper_item -----
DROP TABLE IF EXISTS biz_exam_paper_item;
CREATE TABLE `biz_exam_paper_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `paper_id` varchar(16) NOT NULL COMMENT 'biz_exam_paper.id',
  `seq` int NOT NULL COMMENT '卷内题序',
  `question_id` bigint NOT NULL,
  `section` varchar(8) DEFAULT NULL COMMENT '章测:选择/填空/解答',
  `exam_point` varchar(64) DEFAULT NULL COMMENT '综合卷:考点1/2/3分组名',
  `score` int DEFAULT NULL COMMENT '该题分值(章测有)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_q` (`paper_id`,`question_id`),
  KEY `idx_question` (`question_id`)
) ENGINE=InnoDB AUTO_INCREMENT=198 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卷内题(题序/分值/考点)';

-- ----- biz_export_record -----
DROP TABLE IF EXISTS biz_export_record;
CREATE TABLE `biz_export_record` (
  `id` bigint NOT NULL COMMENT '主键(雪花 id, 出 VO 必 string)',
  `user_id` bigint NOT NULL COMMENT '导出发起者 sys_user.id(服务端 LoginHelper 强制, 不信前端)',
  `paper_id` bigint DEFAULT NULL COMMENT '来源试卷 biz_paper.id(可空: 篮子直接导出无卷)',
  `file_name` varchar(255) DEFAULT NULL COMMENT '导出文件名(弹窗里填的, 默认=卷名)',
  `options` json DEFAULT NULL COMMENT '导出配置 JSON: {variant, watermark, ids[]}',
  `status` char(1) DEFAULT '0' COMMENT '状态: 0排队 1生成中 2完成 3失败',
  `progress` tinyint DEFAULT '0' COMMENT '进度 0-100',
  `duration_ms` int DEFAULT NULL COMMENT '生成耗时(毫秒, 用于滚动预估模型)',
  `file_url` varchar(500) DEFAULT NULL COMMENT 'OSS 公网可访 PDF URL',
  `file_size` bigint DEFAULT NULL COMMENT 'PDF 字节数',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '失败原因(status=3 时)',
  `expire_at` datetime DEFAULT NULL COMMENT 'OSS 留存到期(now+7天, 惰性判过期)',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='试卷导出任务记录';

-- ----- biz_free_tag -----
DROP TABLE IF EXISTS biz_free_tag;
CREATE TABLE `biz_free_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(255) NOT NULL,
  `use_count` int NOT NULL DEFAULT '0' COMMENT '引用次数（题数）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=6504 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题目 freeTag 字典表（V4 / X 卡）';

-- ----- biz_ingest_job -----
DROP TABLE IF EXISTS biz_ingest_job;
CREATE TABLE `biz_ingest_job` (
  `id` bigint NOT NULL COMMENT '作业ID（雪花）',
  `teacher_id` bigint NOT NULL COMMENT '归属老师ID（=create_user 冗余，归属校验用）',
  `subject_id` varchar(20) NOT NULL COMMENT '绑定章节节点 biz_subject.id（整批粗挂）',
  `source_file_name` varchar(255) DEFAULT NULL COMMENT '上传文件名',
  `source_oss_url` varchar(1024) DEFAULT NULL COMMENT '上传文件/图 OSS 地址（原文件留存）',
  `source_type` varchar(16) DEFAULT NULL COMMENT '来源类型 image/pdf/docx/text',
  `lane` varchar(8) DEFAULT NULL COMMENT '分档 fast 快档(文字层)/slow 慢档(页图)',
  `answer_mode` varchar(16) NOT NULL DEFAULT 'from_source' COMMENT '答案模式 from_source原卷自带/ai_solve AI解题/stem_only只录题',
  `commit_mode` varchar(16) NOT NULL DEFAULT 'review' COMMENT '入库方式 review审核后/direct直接',
  `grade_hint` varchar(32) DEFAULT NULL COMMENT '学段提示（约束解法不超纲，可空）',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/EXTRACT_ING/SPLIT_ING/DONE/FAILED',
  `error_msg` varchar(1024) DEFAULT NULL COMMENT '失败原因（FAILED 时带因）',
  `question_count` int NOT NULL DEFAULT '0' COMMENT '拆出题数',
  `committed_count` int NOT NULL DEFAULT '0' COMMENT '已入库题数',
  `dropped_json` text COMMENT '完整度过滤丢弃项（JSON 数组，可读原因）',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_user` bigint DEFAULT NULL COMMENT '创建用户ID',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_job_teacher_status` (`teacher_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='录题批量作业（PRD-A-002 路B）';

-- ----- biz_ingest_job_item -----
DROP TABLE IF EXISTS biz_ingest_job_item;
CREATE TABLE `biz_ingest_job_item` (
  `id` bigint NOT NULL COMMENT '拆出题ID（雪花）',
  `job_id` bigint NOT NULL COMMENT '所属作业 biz_ingest_job.id',
  `seq` int NOT NULL DEFAULT '0' COMMENT '题序',
  `stem_text` mediumtext COMMENT '题干（Markdown+$LaTeX$）',
  `question_type` int DEFAULT NULL COMMENT '题型 1选择/2填空/5解答',
  `options_json` text COMMENT '选项数组（JSON）',
  `answer_text` mediumtext COMMENT '答案（from_source/ai_solve 时有）',
  `analyze_text` mediumtext COMMENT '解析',
  `has_figure` tinyint NOT NULL DEFAULT '0' COMMENT '含图 0/1',
  `difficulty` int DEFAULT NULL COMMENT '难度 1-3',
  `dna_json` text COMMENT '10维DNA（ai_solve 时，JSON）',
  `verify_verdict` varchar(16) DEFAULT NULL COMMENT 'sympy 验算 pass/fail/degrade',
  `need_review` tinyint NOT NULL DEFAULT '0' COMMENT '待审软提示 0/1',
  `item_status` varchar(16) NOT NULL DEFAULT 'pending' COMMENT 'pending待审/committed已入库/dropped已弃',
  `committed_question_id` bigint DEFAULT NULL COMMENT '入库后回填 biz_question.id',
  `figures_json` json DEFAULT NULL COMMENT '裁出题图 [{seq,ossUrl,bbox,conf,assigned}]（PRD-A-024 批1，NULL=无图/未裁）',
  `kp_anchor_json` json DEFAULT NULL COMMENT 'KG锚定 {kpId,kpName,matchedName,stage,confidence,fallback}（PRD-A-024 批2，NULL=未锚）',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_item_job_status` (`job_id`,`item_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='录题作业拆出题暂存（PRD-A-002 路B，勾选才进 biz_question）';

-- ----- biz_key_concept -----
DROP TABLE IF EXISTS biz_key_concept;
CREATE TABLE `biz_key_concept` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `subject_id` varchar(20) NOT NULL COMMENT '所属知识点',
  `concept` varchar(200) NOT NULL COMMENT '重点关键字',
  `source` varchar(16) DEFAULT '书',
  `source_book` varchar(64) DEFAULT NULL,
  `sort` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_subject` (`subject_id`),
  KEY `idx_concept` (`concept`)
) ENGINE=InnoDB AUTO_INCREMENT=551 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识点→重点关键字';

-- ----- biz_kg_doc -----
DROP TABLE IF EXISTS biz_kg_doc;
CREATE TABLE `biz_kg_doc` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `course_id` varchar(20) NOT NULL COMMENT '所挂KG节点id=节(L3);2026-07-02定稿,旧语义(课时L4)作废',
  `lesson_no` int NOT NULL DEFAULT '1' COMMENT '节内第几课时(课时=讲义编排属性,非KG节点)',
  `book_id` varchar(32) DEFAULT NULL COMMENT '教辅书 id，如 CC7S（崔崔讲义七上）',
  `title` varchar(200) DEFAULT NULL COMMENT '课件标题',
  `doc_json` longtext COMMENT 'Umo/Tiptap 文档 JSON（整份课件；讲义大纲=heading，自定义节点 example(qid)/callout/mindmap）',
  `status` char(1) DEFAULT '0' COMMENT '状态：0=正常',
  `source` varchar(32) DEFAULT NULL COMMENT '来源',
  `source_book` varchar(64) DEFAULT NULL COMMENT '来源书名',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_book_lesson` (`course_id`,`book_id`,`lesson_no`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课件文档（一课时一份 Tiptap JSON，PRD-C-205）';

-- ----- biz_kg_lecture_frag （PRD-C-207 V0：讲义片段库，取代 biz_kg_doc 的整块存储）-----
-- 讲义=挂 KG 节点的原子教学片段；某层完整讲义 = WHERE subject_id LIKE '<前缀>%' ORDER BY subject_id 汇聚（树序）。
-- 一节点多份 = book_id(教辅套) × owner_id(0官方/个人)；UK 三维唯一。id 走雪花(Java ASSIGN_ID)。
DROP TABLE IF EXISTS biz_kg_lecture_frag;
CREATE TABLE `biz_kg_lecture_frag` (
  `id` bigint NOT NULL COMMENT '主键(雪花)',
  `subject_id` varchar(20) NOT NULL COMMENT '挂载KG节点id(biz_subject.id);任意层:课时L4/知识点L5(原子)/节L3/章L2/册L1',
  `kg_level` tinyint NOT NULL COMMENT '节点层级=LENGTH(subject_id)/3:1册2章3节4课时5知识点(冗余,按层批量取)',
  `book_id` varchar(32) NOT NULL COMMENT '教辅套id(biz_book.id);崔崔=CC7S',
  `owner_id` bigint NOT NULL DEFAULT '0' COMMENT '创建者user_id(权限模型v2=only-one/权限与内容归属模型-定版.md:官方=超管uid1;写=owner本人∥org_admin同部门∥superadmin;可见=官方+本部门互见+我;草稿仅本人)',
  `title` varchar(200) DEFAULT NULL COMMENT '片段标题,默认取节点名',
  `content_json` longtext COMMENT '本节点自身讲义片段(Tiptap JSON:讲解/例题kgExample(qid)/表/图/思维导图);空=纯汇聚节点',
  `stem_text` mediumtext COMMENT '片段纯文本镜像(全文检索/agent召回)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '同层排序覆盖;0=跟随subject_id树序',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0正常/1草稿',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_node_book_owner` (`subject_id`,`book_id`,`owner_id`),
  KEY `idx_book_level` (`book_id`,`kg_level`),
  KEY `idx_subject` (`subject_id`),
  KEY `idx_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='讲义片段-挂KG节点的原子教学内容,agent组书原料;某层完整讲义=自身+子孙片段按树序汇聚';

-- ----- biz_label_audit -----
DROP TABLE IF EXISTS biz_label_audit;
CREATE TABLE `biz_label_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `api` varchar(64) NOT NULL COMMENT '接口标识 如 label.question.upsert',
  `action` varchar(16) NOT NULL COMMENT 'insert/update/delete',
  `target_type` varchar(16) DEFAULT NULL COMMENT '目标对象类型',
  `target_id` varchar(32) DEFAULT NULL COMMENT '目标id',
  `actor` varchar(64) DEFAULT NULL COMMENT '调用方标识（agent 名 / 人名 / 系统）',
  `actor_type` varchar(8) NOT NULL DEFAULT 'agent' COMMENT 'agent / human',
  `payload_brief` varchar(2000) DEFAULT NULL COMMENT '入参摘要（可审查，不存全量大字段）',
  `result` varchar(16) DEFAULT NULL COMMENT 'ok / fail',
  `err_msg` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
  PRIMARY KEY (`id`),
  KEY `idx_target` (`target_type`,`target_id`),
  KEY `idx_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2066552299571949571 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打标写接口审计（所有增删改留痕）';

-- ----- biz_label_job -----
DROP TABLE IF EXISTS biz_label_job;
CREATE TABLE `biz_label_job` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'job_id (uuid)',
  `batch_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `target_filter` json DEFAULT NULL COMMENT '打标范围 SQL where 条件',
  `total` int DEFAULT '0',
  `done` int DEFAULT '0',
  `failed` int DEFAULT '0',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'pending/running/done/error/aborted',
  `config` json DEFAULT NULL COMMENT 'prompt 版本/模型/参数',
  `started_at` datetime DEFAULT NULL,
  `ended_at` datetime DEFAULT NULL,
  `error_summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status_time` (`status`,`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题库打标任务';

-- ----- biz_label_lesson -----
DROP TABLE IF EXISTS biz_label_lesson;
CREATE TABLE `biz_label_lesson` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `scope` varchar(16) NOT NULL DEFAULT 'question' COMMENT '范围: question单题/dimension某维度/global全局',
  `ref_id` varchar(32) DEFAULT NULL COMMENT '关联对象（可空）: question_id 等',
  `dimension` varchar(32) DEFAULT NULL COMMENT '涉及维度: 富文本/主考点/解法/难度/模型/验算…',
  `failure_mode` varchar(64) DEFAULT NULL COMMENT 'LLM 失效模式（对应失误账 L-xx）',
  `lesson` varchar(2000) NOT NULL COMMENT '经验/教训正文',
  `sop_constraint` varchar(1000) DEFAULT NULL COMMENT '该挡住它的 SOP 约束/闸',
  `author` varchar(64) DEFAULT NULL COMMENT '记录人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_scope` (`scope`)
) ENGINE=InnoDB AUTO_INCREMENT=2066525899611713539 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打标累计经验沉淀（喂回 SOP/失误账）';

-- ----- biz_label_prior -----
DROP TABLE IF EXISTS biz_label_prior;
CREATE TABLE `biz_label_prior` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `kp_id` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '新树节点id(biz_subject)',
  `old_knowledge_id` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `old_knowledge_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tag_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `weight` int DEFAULT '1',
  `src` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'miskt',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kp` (`kp_id`)
) ENGINE=InnoDB AUTO_INCREMENT=528 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节标签先验(旧树按名称映射,2026-07-02 定版S2);认知包④注入源';

-- ----- biz_label_review -----
DROP TABLE IF EXISTS biz_label_review;
CREATE TABLE `biz_label_review` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `target_type` varchar(16) NOT NULL COMMENT '审核对象: question单题/paper试卷报告/model模型',
  `target_id` varchar(32) NOT NULL COMMENT '对象id: question_id / paper_id / model_id(Mxx)',
  `verdict` varchar(16) NOT NULL COMMENT '裁决: pass通过/reject打回/doubt存疑；模型: keep/merge/kill',
  `merge_into` varchar(10) DEFAULT NULL COMMENT '模型 merge 时并入的 Mid',
  `rework_note` varchar(1000) DEFAULT NULL COMMENT '打回注释（回炉重写/重生的依据）',
  `reviewer` varchar(64) DEFAULT NULL COMMENT '审核人',
  `review_round` int NOT NULL DEFAULT '1' COMMENT '审核轮次（多轮轨迹）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审核时间',
  PRIMARY KEY (`id`),
  KEY `idx_target` (`target_type`,`target_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2066552299488063490 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打标审核裁决（单题/报告/模型，多轮轨迹）';

-- ----- biz_label_vocab -----
DROP TABLE IF EXISTS biz_label_vocab;
CREATE TABLE `biz_label_vocab` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `vocab_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'scenario/method/key_object',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `aliases` json DEFAULT NULL COMMENT '已并入的近义词',
  `use_count` int DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_name` (`vocab_type`,`name`),
  KEY `idx_type` (`vocab_type`)
) ENGINE=InnoDB AUTO_INCREMENT=1399 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='半开放维受控字典(写库前归一,禁近义)';

-- ----- biz_paper -----
DROP TABLE IF EXISTS biz_paper;
CREATE TABLE `biz_paper` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(200) NOT NULL,
  `subject_id` varchar(20) DEFAULT NULL COMMENT '教材冗余',
  `paper_category_id` varchar(20) DEFAULT NULL COMMENT 'M4 新增 试卷分类',
  `question_count` int DEFAULT '0' COMMENT '冗余',
  `score` decimal(6,2) DEFAULT '0.00',
  `suggest_time` int DEFAULT NULL COMMENT '建议时长(分钟)',
  `paper_type` tinyint DEFAULT '1' COMMENT '1手工 2自动',
  `exam_year` varchar(10) DEFAULT NULL,
  `status` char(1) DEFAULT '0' COMMENT '0草稿 1发布 2软删',
  `sort` int DEFAULT '0',
  `create_by` varchar(64) DEFAULT '',
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT NULL,
  `remark` text,
  `region` varchar(50) DEFAULT NULL COMMENT '地区(如 浙江杭州拱墅区)',
  `school` varchar(100) DEFAULT NULL COMMENT '学校(可空)',
  `exam_type` varchar(20) DEFAULT NULL COMMENT '考试类型(字典biz_question_source_type,收敛后权威)',
  `paper_kind` char(1) NOT NULL DEFAULT '1' COMMENT '1普通 2备课卷(PRD-B-101;备课卷私有,公共卷库查询排除)',
  PRIMARY KEY (`id`),
  KEY `idx_subject` (`subject_id`),
  KEY `idx_category` (`paper_category_id`),
  KEY `idx_creator` (`create_by`)
) ENGINE=InnoDB AUTO_INCREMENT=96 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='试卷主表';

-- ----- biz_paper_basket -----
DROP TABLE IF EXISTS biz_paper_basket;
CREATE TABLE `biz_paper_basket` (
  `user_id` bigint NOT NULL,
  `paper_id` bigint NOT NULL,
  `add_time` datetime DEFAULT NULL,
  PRIMARY KEY (`user_id`,`paper_id`),
  KEY `idx_user_time` (`user_id`,`add_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='试卷筐';

-- ----- biz_paper_category -----
DROP TABLE IF EXISTS biz_paper_category;
CREATE TABLE `biz_paper_category` (
  `id` varchar(20) NOT NULL COMMENT '4位数字编码 3001/3003/3004 等',
  `parent_id` varchar(20) DEFAULT '0',
  `name` varchar(200) NOT NULL,
  `sort` int DEFAULT '0',
  `subject` tinyint DEFAULT NULL COMMENT '学科 dict biz_edu_subject',
  `stage` tinyint DEFAULT NULL COMMENT '学段 dict biz_edu_stage',
  `grade` tinyint DEFAULT NULL COMMENT '年级 dict biz_edu_grade',
  `volume` tinyint DEFAULT NULL COMMENT '册 dict biz_edu_volume',
  `paper_type` tinyint DEFAULT NULL COMMENT '卷型 dict biz_paper_type',
  `node_kind` varchar(12) DEFAULT NULL COMMENT 'root/grade/ptype/exam/chapter/year/misc',
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='试卷分类(独立于章节树)';

-- ----- biz_paper_question -----
DROP TABLE IF EXISTS biz_paper_question;
CREATE TABLE `biz_paper_question` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `paper_id` bigint NOT NULL,
  `section_id` bigint NOT NULL,
  `question_id` bigint NOT NULL,
  `sort` int NOT NULL COMMENT '组内顺序',
  `score` decimal(5,2) DEFAULT '0.00',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_section_sort` (`section_id`,`sort`),
  KEY `idx_paper` (`paper_id`),
  KEY `idx_question` (`question_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3596 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='试卷-题目';

-- ----- biz_paper_section -----
DROP TABLE IF EXISTS biz_paper_section;
CREATE TABLE `biz_paper_section` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `paper_id` bigint NOT NULL,
  `title` varchar(50) NOT NULL COMMENT '选择题/填空题/解答题',
  `sort` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_section_sort` (`paper_id`,`sort`)
) ENGINE=InnoDB AUTO_INCREMENT=96 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='试卷题目分组';

-- ----- biz_pitfall -----
DROP TABLE IF EXISTS biz_pitfall;
CREATE TABLE `biz_pitfall` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `book_id` varchar(8) DEFAULT NULL,
  `knowledge_id` varchar(20) DEFAULT NULL COMMENT '所属知识点',
  `title` varchar(200) NOT NULL COMMENT '易错点(书"刷易错"金标标题,忠于原文)',
  `error_type` varchar(16) DEFAULT NULL COMMENT '?闭集6:概念混淆/漏解分类不全/运算律误用/去括号去分母/审题漏条件/移项符号',
  `description` varchar(1000) DEFAULT NULL COMMENT '蒸馏:为什么错(踩坑)+正确关键',
  `is_gold` tinyint(1) DEFAULT '0' COMMENT '?1书刷易错金标21 / 0骨架反推高频',
  `source_book` varchar(64) DEFAULT NULL,
  `source_anchor` varchar(32) DEFAULT NULL,
  `status` char(1) NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `discipline` varchar(20) DEFAULT NULL COMMENT '分科(科学用,字典同biz_question.discipline)',
  `trigger_feature` varchar(500) DEFAULT NULL COMMENT '触发特征(什么题面信号会踩这个坑)',
  `typical_error` varchar(500) DEFAULT NULL COMMENT '典型错误动作',
  `correction` varchar(500) DEFAULT NULL COMMENT '纠正要点',
  `severity_tier` tinyint DEFAULT NULL COMMENT '严重度基调(人定):1轻坑提醒/2重坑高发/3致命坑整题翻车;供科学难度双主轴消费',
  PRIMARY KEY (`id`),
  KEY `idx_knowledge` (`knowledge_id`),
  KEY `idx_title` (`title`),
  KEY `idx_error_type` (`error_type`)
) ENGINE=InnoDB AUTO_INCREMENT=194 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='易错知识库(金标+反推)';

-- ----- biz_question -----
DROP TABLE IF EXISTS biz_question;
CREATE TABLE `biz_question` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question_type` tinyint NOT NULL COMMENT '1选择 2填空 3判断 4计算 5解答',
  `is_anchor` tinyint(1) NOT NULL DEFAULT '0' COMMENT '压轴题标记',
  `subject_id` varchar(20) NOT NULL COMMENT '科目锚 level1',
  `dim1_kp_id` varchar(20) DEFAULT NULL COMMENT '①主考点(biz_subject 叶子)',
  `stem_text` mediumtext COMMENT '题干纯文本(全文检索用)',
  `stem_hash` char(32) DEFAULT NULL COMMENT '?题面归一化MD5(去重:综合卷真题vs正文题防重复入库)',
  `stem_text_content_id` bigint DEFAULT NULL,
  `answer_text_content_id` bigint DEFAULT NULL,
  `analyze_text_content_id` bigint DEFAULT NULL,
  `version` int DEFAULT '1010' COMMENT '题目格式版本码',
  `exam_year` varchar(10) DEFAULT NULL COMMENT '年份',
  `region_code` varchar(12) DEFAULT NULL COMMENT '地区码 → biz_region.code',
  `source_type` tinyint DEFAULT NULL COMMENT '1中考2模拟3期末4月考5单元6自编7期中9其他',
  `source_raw` varchar(255) DEFAULT NULL COMMENT '原始来源前缀全文(可回滚)',
  `mother_question_id` bigint DEFAULT NULL COMMENT '母题指针(派生缓存,SSOT在trace)',
  `variant_relation` varchar(16) DEFAULT NULL COMMENT '数值变式/情境变式/结构变式/同源',
  `annotate_version` int NOT NULL DEFAULT '0' COMMENT '标注 schema 版本(维度扩展时 bump)',
  `annotate_status` tinyint NOT NULL DEFAULT '0' COMMENT '0未标 1已标全 2部分',
  `mother_source` varchar(16) DEFAULT NULL COMMENT '?textbook书原生(金标) / ai反推',
  `stem_embedding` blob COMMENT '题干向量预留,本期空',
  `embedding_model` varchar(64) DEFAULT NULL COMMENT '生成向量的模型',
  `embedding_updated_at` datetime DEFAULT NULL,
  `label_status` tinyint DEFAULT '0' COMMENT '0未标 1AI已标 2已审 3存疑',
  `label_confidence` decimal(4,3) DEFAULT NULL COMMENT 'AI 自评置信度 0-1',
  `labeled_by` varchar(64) DEFAULT NULL COMMENT 'AI 模型名或人员',
  `labeled_at` datetime DEFAULT NULL,
  `reviewed_by` varchar(64) DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `aux_tags` json DEFAULT NULL COMMENT '辅标签:错因/情境/考查角度/母题',
  `import_source` varchar(32) DEFAULT 'main' COMMENT '?main主书/kuangk狂K/textin',
  `import_batch_id` varchar(64) DEFAULT NULL,
  `status` char(1) DEFAULT '1' COMMENT '0草稿 1发布 2软删',
  `create_by` varchar(64) DEFAULT '' COMMENT '若依用户名',
  `create_user` bigint DEFAULT NULL COMMENT '录入者 sys_user.id',
  `remark` varchar(500) DEFAULT NULL,
  `base_score` decimal(5,2) DEFAULT NULL COMMENT '题自身标准分值, 与 biz_paper_question.score 精度对齐, 卷内 override 优先',
  `is_collected` tinyint(1) NOT NULL DEFAULT '1' COMMENT '0=misikt老题(可删) 1=自有化新题',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_public` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否公开 0私有(默认/入库态) 1超管审核通过公开（PRD-A-023 B10 公共题库审核闸）',
  `stem_img_url` varchar(500) DEFAULT NULL COMMENT '题干渲染图(可选缓存)',
  `answer_img_url` varchar(500) DEFAULT NULL,
  `explain_img_url` varchar(500) DEFAULT NULL,
  `file_bin_url` varchar(500) DEFAULT NULL COMMENT '原站 data.bin(笔迹层,不实现)',
  `dim2_qtype` tinyint DEFAULT NULL COMMENT '②题型 1选择/4填空/5解答/6证明',
  `dim4_difficulty` tinyint DEFAULT NULL COMMENT '④难度 1-4',
  `discipline` varchar(20) DEFAULT NULL COMMENT '分科(科学专属,字典biz_question_discipline:1物理2化学3生物4地学5综合探究);数学恒空',
  `dim5_structure` varchar(500) DEFAULT NULL COMMENT '[冻结2026-07-02]结构指纹由solution_skeleton兼任,本列只读存量,不再写入',
  `exam_paper_id` bigint DEFAULT NULL COMMENT '出处试卷id 冗余',
  `exam_paper_name` varchar(200) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_subject` (`subject_id`),
  KEY `idx_kp` (`dim1_kp_id`),
  KEY `idx_type_diff` (`question_type`),
  KEY `idx_mother` (`mother_question_id`),
  KEY `idx_stem_hash` (`stem_hash`),
  KEY `idx_source_type` (`source_type`),
  KEY `idx_label_status` (`label_status`),
  FULLTEXT KEY `ft_stem` (`stem_text`) /*!50100 WITH PARSER `ngram` */ 
) ENGINE=InnoDB AUTO_INCREMENT=2072375603524366339 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题目主表';

-- ----- biz_question_ai -----
DROP TABLE IF EXISTS biz_question_ai;
CREATE TABLE `biz_question_ai` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question_id` bigint NOT NULL,
  `annotate_version` int NOT NULL DEFAULT '1',
  `solution_skeleton` mediumtext COMMENT '解法骨架(步骤序列,【】标最难步)=变式生成基因',
  `assessment_type` varchar(128) DEFAULT NULL COMMENT '考察类型(C-204加宽:32->128,长描述型assessmentType)',
  `hard_point_count` tinyint DEFAULT NULL COMMENT '难点个数(0=基础题)',
  `breakthrough_points` json DEFAULT NULL COMMENT '突破点/难点(半开放)',
  `scenario` varchar(128) DEFAULT NULL COMMENT '场景(半开放)=变式表皮基因',
  `math_thoughts` json DEFAULT NULL COMMENT '数学思想(⊂小闭集)',
  `tags` json DEFAULT NULL COMMENT '检索标签 3-6',
  `difficulty_reason` varchar(500) DEFAULT NULL COMMENT '?难度综合判级依据',
  `anchor_id` varchar(20) DEFAULT NULL COMMENT '锚定 subject 节点',
  `confidence` decimal(4,3) DEFAULT NULL COMMENT '锚定置信 0-1',
  `need_anchor_review` tinyint(1) DEFAULT '0' COMMENT '锚定存疑待人审',
  `reasoning` text COMMENT 'agent 抽取依据',
  `label_status` tinyint DEFAULT '1' COMMENT '1AI已标 2已审 3存疑',
  `labeled_by` varchar(64) DEFAULT NULL,
  `labeled_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_time` datetime DEFAULT NULL,
  `parametric_slots` json DEFAULT NULL COMMENT 'C:参数槽位[{量名,当前值,类型,约束}](①数值⑤推广)',
  `modeling_frame` json DEFAULT NULL COMMENT 'C:建模骨架{设元,相等关系,运算链}|null(④情境)',
  `conditions` json DEFAULT NULL COMMENT 'C:条件与小问{已知[],小问[],约束[]}(③条件增删)',
  `variation_profile` json DEFAULT NULL COMMENT 'D:变式路由,每算子{可用,原料指针,依据,风险,自动盘}',
  `hard_points` json DEFAULT NULL COMMENT 'B:难点[](书▶难点直抽)',
  `verify_kind` varchar(32) DEFAULT 'LLM核验' COMMENT 'B:验证方式,现统一独立LLM核验,留题型域标签(代数/几何/应用),sympy留位',
  `dna_type` varchar(50) DEFAULT NULL COMMENT '[冻结2026-07-02]三集合是打标方法论非存储维,本列只读存量',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_q_ver` (`question_id`,`annotate_version`),
  KEY `idx_qid` (`question_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2072378690146234370 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI派生维(反向DNA;砍难点)';

-- ----- biz_question_basket -----
DROP TABLE IF EXISTS biz_question_basket;
CREATE TABLE `biz_question_basket` (
  `user_id` bigint NOT NULL,
  `question_id` bigint NOT NULL,
  `add_time` datetime DEFAULT NULL,
  PRIMARY KEY (`user_id`,`question_id`),
  KEY `idx_user_time` (`user_id`,`add_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='试题筐';

-- ----- biz_question_block -----
DROP TABLE IF EXISTS biz_question_block;
CREATE TABLE `biz_question_block` (
  `question_id` bigint NOT NULL COMMENT '题目 id(逻辑 FK→biz_question.id; 一题一份 block 文档, 作 PK)',
  `block_json` json DEFAULT NULL COMMENT 'block 文档 JSON(§10.1 schema: {v,rows:[{cells:[block]}]})',
  `v` int DEFAULT '1' COMMENT 'schema 版本号(当前 1)',
  `update_by` bigint DEFAULT NULL COMMENT '最后保存者 sys_user.id(服务端 LoginHelper 强制)',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `answer_block_json` mediumtext COMMENT 'C-204:答案 blockJson(走convertRichText统一渲染)',
  `analyze_block_json` mediumtext COMMENT 'C-204:解析 blockJson(选项分析/小问/步骤拆块,三端统一渲染)',
  PRIMARY KEY (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题目结构化网格块存储(PRD-A-015)';

-- ----- biz_question_favorite -----
DROP TABLE IF EXISTS biz_question_favorite;
CREATE TABLE `biz_question_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '收藏者 sys_user.user_id',
  `question_id` bigint NOT NULL COMMENT '题目 biz_question.id',
  `folder_id` bigint DEFAULT '0' COMMENT '收藏夹 biz_question_folder.id，0=默认收藏夹',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_question` (`user_id`,`question_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题目收藏(每用户对每题至多一条)';

-- ----- biz_question_folder -----
DROP TABLE IF EXISTS biz_question_folder;
CREATE TABLE `biz_question_folder` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '归属 sys_user.user_id',
  `name` varchar(64) NOT NULL COMMENT '收藏夹名称',
  `pid` bigint DEFAULT '0' COMMENT '父收藏夹 id，0=根',
  `sort` int DEFAULT '0' COMMENT '同级排序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_pid` (`user_id`,`pid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏夹分类树';

-- ----- biz_question_free_tag -----
DROP TABLE IF EXISTS biz_question_free_tag;
CREATE TABLE `biz_question_free_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `question_id` bigint NOT NULL COMMENT 'biz_question.id',
  `tag_id` bigint NOT NULL COMMENT 'biz_free_tag.id',
  `position` tinyint NOT NULL COMMENT '出现位置 0/1/2/3/4 — 决定 FE 颜色',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_q_t` (`question_id`,`tag_id`),
  KEY `idx_q` (`question_id`),
  KEY `idx_t` (`tag_id`)
) ENGINE=InnoDB AUTO_INCREMENT=72488 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题 × freeTag 关联（V4 / X 卡）';

-- ----- biz_question_image -----
DROP TABLE IF EXISTS biz_question_image;
CREATE TABLE `biz_question_image` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question_id` bigint NOT NULL COMMENT '→biz_question.id',
  `asset_id` bigint NOT NULL COMMENT '→image_asset.id(OSS资产/去重层)',
  `oss_url` varchar(1024) DEFAULT NULL COMMENT '冗余 image_asset.oss_url(=blockJson图块url, join键)',
  `block_id` varchar(32) DEFAULT NULL COMMENT '绑哪个块:题干/选项A/小问1/图块(对应blockJson内逻辑块)',
  `role` varchar(12) DEFAULT NULL COMMENT '题图/选项图/答案图/辅助图',
  `seq` int DEFAULT '1' COMMENT '同块内图序 图1/图2',
  `is_decorative` tinyint(1) NOT NULL DEFAULT '0' COMMENT '1装饰图(章首/版式,滤掉) 0内容图',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_q` (`question_id`),
  KEY `idx_asset` (`asset_id`),
  KEY `idx_role` (`role`)
) ENGINE=InnoDB AUTO_INCREMENT=2072375603524366341 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题↔图 M:N块级绑定(块/角色/序/装饰flag;一图可绑多题)';

-- ----- biz_question_knowledge -----
DROP TABLE IF EXISTS biz_question_knowledge;
CREATE TABLE `biz_question_knowledge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question_id` bigint NOT NULL,
  `knowledge_id` varchar(20) NOT NULL COMMENT '关联 biz_subject.id（叶子）',
  `source` varchar(8) DEFAULT NULL COMMENT 'U用户 / S标准 / AI=AI锚定(PRD-A-024批2录题KG锚定，原char(1)已扩)',
  `create_time` datetime DEFAULT NULL,
  `is_primary` tinyint(1) NOT NULL DEFAULT '1' COMMENT '1主考点/0副考点（同一知识体系 biz_subject，主副只此标记区分）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qk_src` (`question_id`,`knowledge_id`,`source`),
  KEY `idx_knowledge` (`knowledge_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2071829784442177135 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题目-知识点 M:N';

-- ----- biz_question_model -----
DROP TABLE IF EXISTS biz_question_model;
CREATE TABLE `biz_question_model` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question_id` bigint NOT NULL COMMENT 'biz_question.id',
  `model_id` varchar(10) NOT NULL COMMENT 'biz_solution_model.id',
  `is_primary` tinyint(1) NOT NULL DEFAULT '1' COMMENT '1主模型 0辅助模型',
  `source` varchar(16) NOT NULL DEFAULT 'AI' COMMENT 'AI/人工',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `role` varchar(8) DEFAULT NULL COMMENT '?母题/变式/应用(题在该模型里的角色)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_q_m` (`question_id`,`model_id`),
  KEY `idx_model` (`model_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2066523284010291213 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题↔解题模型 M:N（命题血缘，举一反三调套路用）';

-- ----- biz_question_note -----
DROP TABLE IF EXISTS biz_question_note;
CREATE TABLE `biz_question_note` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '备注归属 sys_user.user_id',
  `question_id` bigint NOT NULL COMMENT '题目 biz_question.id',
  `content` text NOT NULL COMMENT '备注内容(富文本/纯文本均可)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次写入时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_question` (`user_id`,`question_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题目个人备注(每用户对每题至多一条)';

-- ----- biz_question_pattern -----
DROP TABLE IF EXISTS biz_question_pattern;
CREATE TABLE `biz_question_pattern` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(200) NOT NULL COMMENT '题型名(题型1 利用数轴比较大小)',
  `anchor_subject_id` varchar(20) DEFAULT NULL COMMENT '挂的知识点/小节(biz_subject.id),题型长在知识点下',
  `source` varchar(8) NOT NULL DEFAULT '书' COMMENT '书(main7s题型过关)/AI补',
  `sort` int DEFAULT '0',
  `description` varchar(1000) DEFAULT NULL COMMENT '题型说明/解题要点',
  `book_id` varchar(8) DEFAULT NULL COMMENT '来源教辅(可选)',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0正常1停用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_anchor_name` (`anchor_subject_id`,`name`),
  KEY `idx_anchor` (`anchor_subject_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2069694365843857411 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题型(解题pattern)目录:独立可查维度,锚知识点,不进KG树';

-- ----- biz_question_pattern_rel -----
DROP TABLE IF EXISTS biz_question_pattern_rel;
CREATE TABLE `biz_question_pattern_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question_id` bigint NOT NULL,
  `pattern_id` bigint NOT NULL COMMENT '→biz_question_pattern.id',
  `is_primary` tinyint(1) NOT NULL DEFAULT '1' COMMENT '1主题型 0副题型',
  `source` varchar(8) NOT NULL DEFAULT '书' COMMENT '书/AI',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_q_p` (`question_id`,`pattern_id`),
  KEY `idx_pattern` (`pattern_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2069819648940986375 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题↔题型 M:N(is_primary标主题型;题卡徽标+题库筛选)';

-- ----- biz_question_pitfall -----
DROP TABLE IF EXISTS biz_question_pitfall;
CREATE TABLE `biz_question_pitfall` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question_id` bigint NOT NULL,
  `pitfall_id` bigint NOT NULL,
  `source` varchar(16) NOT NULL DEFAULT 'book',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_q_p` (`question_id`,`pitfall_id`),
  KEY `idx_pitfall` (`pitfall_id`)
) ENGINE=InnoDB AUTO_INCREMENT=184 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题↔易错 M:N';

-- ----- biz_region -----
DROP TABLE IF EXISTS biz_region;
CREATE TABLE `biz_region` (
  `code` varchar(12) NOT NULL COMMENT 'GB/T2260 行政码',
  `name` varchar(40) NOT NULL COMMENT '本级名',
  `full_name` varchar(60) DEFAULT NULL COMMENT '完整可读名(题首原文)',
  `parent_code` varchar(12) DEFAULT NULL,
  `level` tinyint NOT NULL COMMENT '1省 2地区',
  `sort` int DEFAULT '0',
  PRIMARY KEY (`code`),
  KEY `idx_parent` (`parent_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='来源地区维表';

-- ----- biz_solution_model -----
DROP TABLE IF EXISTS biz_solution_model;
CREATE TABLE `biz_solution_model` (
  `id` varchar(10) NOT NULL COMMENT 'M001/SM01..',
  `book_id` varchar(8) DEFAULT NULL,
  `name` varchar(100) NOT NULL COMMENT '模型名(=大招小招"母题学大招N名" 或 反推模型名)',
  `model_kind` varchar(10) NOT NULL DEFAULT 'derived' COMMENT '?gold书金标大招小招 / derived反推补充(替代隐式前缀区分)',
  `category` varchar(50) DEFAULT NULL COMMENT '模型大类(gold/derived共用解法大类)',
  `topic_id` varchar(10) DEFAULT NULL COMMENT '?挂的大招专题 biz_special_topic.id(仅gold有)',
  `mother_question_id` bigint DEFAULT NULL COMMENT '?该小招母题指针(仅gold有;约束:model_kind=gold⟹非空)',
  `trigger_feature` varchar(500) DEFAULT NULL COMMENT '触发特征',
  `action_conclusion` varchar(500) DEFAULT NULL COMMENT '动作→结论(招式)',
  `is_gold` tinyint(1) DEFAULT '0' COMMENT '?1书金标大招小招 / 0反推(=model_kind冗余,查询便利)',
  `source_book` varchar(64) DEFAULT NULL,
  `page_anchor` varchar(32) DEFAULT NULL,
  `sort` int DEFAULT '0',
  `status` char(1) NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `difficulty_tier` tinyint NOT NULL DEFAULT '1' COMMENT '难度阶:1基础阶 2高阶',
  `freq_band` tinyint NOT NULL DEFAULT '1' COMMENT '考频:1一次性低频 2高频通用',
  PRIMARY KEY (`id`),
  KEY `idx_topic` (`topic_id`),
  KEY `idx_mother` (`mother_question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='解题模型词库(金标大招小招+反推补充)';

-- ----- biz_solution_model_kp -----
DROP TABLE IF EXISTS biz_solution_model_kp;
CREATE TABLE `biz_solution_model_kp` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `model_id` varchar(10) NOT NULL COMMENT 'biz_solution_model.id',
  `subject_id` varchar(20) NOT NULL COMMENT 'biz_subject.id(知识点/图谱节点)',
  `bind_type` varchar(10) NOT NULL DEFAULT 'primary' COMMENT 'primary|native|cross',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_subject_type` (`model_id`,`subject_id`,`bind_type`),
  KEY `idx_subject` (`subject_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2066211585864527876 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='解题模型↔知识点绑定';

-- ----- biz_special_topic -----
DROP TABLE IF EXISTS biz_special_topic;
CREATE TABLE `biz_special_topic` (
  `id` varchar(10) NOT NULL COMMENT 'ST01..',
  `book_id` varchar(6) DEFAULT NULL COMMENT '教材版本→biz_book.id',
  `kind` tinyint NOT NULL COMMENT '1重难专题 2大招专题 3项目化学习',
  `seq` int DEFAULT NULL COMMENT '专题序号(如 重难专题1 / 大招专题1)',
  `title` varchar(100) NOT NULL COMMENT '专题标题(忠于原文)',
  `chapter_subject_id` varchar(20) DEFAULT NULL COMMENT '挂的章 biz_subject(L3)',
  `anchor_kp_id` varchar(20) DEFAULT NULL COMMENT '主知识点(可空)',
  `intro` varchar(500) DEFAULT NULL COMMENT '专题导语/适用说明',
  `page_anchor` varchar(32) DEFAULT NULL,
  `source_book` varchar(64) DEFAULT NULL,
  `sort` int DEFAULT '0',
  `status` char(1) NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `topic_type` varchar(16) DEFAULT NULL COMMENT '大招专题/重难专题/项目化学习/全章综合',
  `granularity` varchar(8) DEFAULT NULL COMMENT '章级(平行小节)/节内(挂小节下)',
  `anchor_subject_id` varchar(20) DEFAULT NULL COMMENT '挂的章 or 小节(biz_subject.id)',
  PRIMARY KEY (`id`),
  KEY `idx_kind` (`kind`),
  KEY `idx_chapter` (`chapter_subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='章末专题(重难/大招/项目化)';

-- ----- biz_special_topic_item -----
DROP TABLE IF EXISTS biz_special_topic_item;
CREATE TABLE `biz_special_topic_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `topic_id` varchar(10) NOT NULL COMMENT 'biz_special_topic.id',
  `item_kind` varchar(12) NOT NULL DEFAULT '类型' COMMENT '?类型(重难解法分类)/子任务(项目化子问)',
  `seq` int DEFAULT NULL COMMENT '类型1/2/3 或 子任务序',
  `name` varchar(100) NOT NULL COMMENT '类型名(如"逆用分配律")/子任务名',
  `teach_content` text COMMENT '该类型解法讲解 / 子任务说明',
  `sort` int DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_topic` (`topic_id`)
) ENGINE=InnoDB AUTO_INCREMENT=72 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专题子项(重难类型/项目子任务)';

-- ----- biz_subject -----
DROP TABLE IF EXISTS biz_subject;
CREATE TABLE `biz_subject` (
  `id` varchar(20) NOT NULL COMMENT '层级数字编码，每3位一层；根=学段+学科',
  `parent_id` varchar(20) DEFAULT NULL,
  `name` varchar(200) NOT NULL,
  `level` tinyint NOT NULL COMMENT '1学科 2教材 3章 4节 5知识点',
  `sort` int DEFAULT '0',
  `status` char(1) DEFAULT '0' COMMENT '0正常 1停用',
  `create_by` varchar(64) DEFAULT '',
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `mine_visible` char(1) NOT NULL DEFAULT '1' COMMENT '个人题库(我的题库)目录是否展示(1展示 0隐藏)',
  `subject` tinyint DEFAULT NULL COMMENT '学科 dict biz_edu_subject(仅level=1教材根)',
  `stage` tinyint DEFAULT NULL COMMENT '学段 dict biz_edu_stage',
  `grade` tinyint DEFAULT NULL COMMENT '年级 dict biz_edu_grade',
  `volume` tinyint DEFAULT NULL COMMENT '册 dict biz_edu_volume',
  `edition` tinyint DEFAULT NULL COMMENT '版本基名 dict biz_edu_edition',
  `edition_year` smallint DEFAULT NULL COMMENT '版本年份 2024/2012/0',
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`),
  KEY `idx_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教材-章节-知识点树';

-- ----- biz_subject_relation -----
DROP TABLE IF EXISTS biz_subject_relation;
CREATE TABLE `biz_subject_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `from_subject_id` varchar(20) NOT NULL,
  `to_subject_id` varchar(20) NOT NULL,
  `rel_type` varchar(16) NOT NULL COMMENT '前置/相关(共现)/方法迁移(共享模型)',
  `source` varchar(16) DEFAULT NULL COMMENT 'code派生 / LLM',
  `weight` decimal(5,3) DEFAULT NULL COMMENT '强度',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_edge` (`from_subject_id`,`to_subject_id`,`rel_type`),
  KEY `idx_to` (`to_subject_id`)
) ENGINE=InnoDB AUTO_INCREMENT=201 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识点间关系';

-- ----- biz_tag_import -----
DROP TABLE IF EXISTS biz_tag_import;
CREATE TABLE `biz_tag_import` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `use_count` int DEFAULT '0',
  `src` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'miskt',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=5455 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签复用池种子(miskt_data2同步,2026-07-02 定版S2);打标注入取 top-N,不直接污染 biz_free_tag';

-- ----- biz_teacher_ai_memory -----
DROP TABLE IF EXISTS biz_teacher_ai_memory;
CREATE TABLE `biz_teacher_ai_memory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `teacher_id` bigint NOT NULL COMMENT '归属老师 user_id',
  `mem_type` varchar(16) NOT NULL COMMENT '记忆类型: 偏好 / 纠正 / 习惯',
  `mem_key` varchar(64) NOT NULL COMMENT '记忆键(如「常教年级」「教材版本」)',
  `mem_value` varchar(512) NOT NULL COMMENT '记忆值',
  `source` varchar(8) NOT NULL DEFAULT '手填' COMMENT '来源: 自动 / 手填',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '启停(0=停用, 停用不注入 prompt)',
  `confidence` decimal(4,3) DEFAULT NULL COMMENT '置信度(自动写入时可带)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_teacher_type` (`teacher_id`,`mem_type`),
  KEY `idx_teacher_enabled` (`teacher_id`,`enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='PRD-C-100 老师全局AI记忆';

-- ----- biz_text_content -----
DROP TABLE IF EXISTS biz_text_content;
CREATE TABLE `biz_text_content` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question_id` bigint NOT NULL COMMENT '鍙嶆煡 biz_question.id',
  `content_type` char(1) NOT NULL COMMENT 'S=棰樺共 / A=绛旀? / E=瑙ｆ瀽',
  `content` mediumtext COMMENT '闀挎枃鏈?唴瀹?LaTeX 婧?/ 绾?枃鏈?',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question_type` (`question_id`,`content_type`),
  KEY `idx_question` (`question_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2072375603524366340 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='棰樼洰涓夎?绱犻暱鏂囨湰澶栫疆(棰樺共 S / 绛旀? A / 瑙ｆ瀽 E)';

-- ----- biz_ts_base -----
DROP TABLE IF EXISTS biz_ts_base;
CREATE TABLE `biz_ts_base` (
  `id` bigint NOT NULL COMMENT '主键 id (snowflake 19 位 / VO 必须 Long)',
  `user_id` bigint NOT NULL COMMENT '老师 user_id (sys_user.user_id)',
  `address_text` varchar(255) NOT NULL COMMENT '地址原文（用户输入）',
  `lng` decimal(10,6) DEFAULT NULL COMMENT '经度（高德 geocode）',
  `lat` decimal(10,6) DEFAULT NULL COMMENT '纬度（高德 geocode）',
  `formatted_address` varchar(255) DEFAULT NULL COMMENT '高德格式化地址',
  `address_level` varchar(32) DEFAULT NULL COMMENT '高德地址级别（兴趣点/区/市等）',
  `create_by` bigint DEFAULT NULL COMMENT '创建人 user_id',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='老师全局基点 (C 主线 / MVP)';

-- ----- biz_ts_chat_message -----
DROP TABLE IF EXISTS biz_ts_chat_message;
CREATE TABLE `biz_ts_chat_message` (
  `id` bigint NOT NULL COMMENT '主键 id',
  `session_id` bigint NOT NULL COMMENT '所属会话 id',
  `role` varchar(16) NOT NULL COMMENT '角色 user / assistant / tool',
  `content` text COMMENT 'user/assistant 文本内容',
  `tool_use` text COMMENT 'assistant tool_use block JSON (extract_lessons 入参)',
  `tool_result` text COMMENT 'tool result block JSON',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 对话消息 (C 主线 / MVP)';

-- ----- biz_ts_chat_session -----
DROP TABLE IF EXISTS biz_ts_chat_session;
CREATE TABLE `biz_ts_chat_session` (
  `id` bigint NOT NULL COMMENT '主键 id',
  `user_id` bigint NOT NULL COMMENT '老师 user_id',
  `status` varchar(16) NOT NULL DEFAULT 'active' COMMENT '会话状态 active / finished / cancelled',
  `round_count` int NOT NULL DEFAULT '0' COMMENT '已追问轮数（MVP 上限 3）',
  `create_by` bigint DEFAULT NULL COMMENT '创建人 user_id',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 对话会话 (C 主线 / MVP 多轮追问)';

-- ----- biz_ts_lesson -----
DROP TABLE IF EXISTS biz_ts_lesson;
CREATE TABLE `biz_ts_lesson` (
  `id` bigint NOT NULL COMMENT '主键 id',
  `user_id` bigint NOT NULL COMMENT '老师 user_id',
  `lesson_date` date NOT NULL COMMENT '日期 YYYY-MM-DD',
  `start_time` time NOT NULL COMMENT '开始时间 HH:MM:SS',
  `duration_min` int NOT NULL COMMENT '持续分钟数',
  `location_text` varchar(255) NOT NULL COMMENT '地点原文',
  `lng` decimal(10,6) DEFAULT NULL COMMENT '经度（高德 geocode）',
  `lat` decimal(10,6) DEFAULT NULL COMMENT '纬度（高德 geocode）',
  `lesson_name` varchar(255) DEFAULT NULL COMMENT '课程名称（自由文本：人/学科/时间/地点）',
  `create_by` bigint DEFAULT NULL COMMENT '创建人 user_id',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_date` (`user_id`,`lesson_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程节 (C 主线 / MVP 单天单节)';

-- ----- biz_variant_upload -----
DROP TABLE IF EXISTS biz_variant_upload;
CREATE TABLE `biz_variant_upload` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `oss_url` varchar(500) NOT NULL COMMENT 'OSS 公网可读 URL(给 FE 回显 + LLM 中转抓取)',
  `object_key` varchar(500) DEFAULT NULL COMMENT 'OSS 对象键(桶内路径, 删除/审计用)',
  `original_name` varchar(255) DEFAULT NULL COMMENT '上传时的原始文件名',
  `file_size` bigint DEFAULT NULL COMMENT '文件字节数',
  `content_type` varchar(100) DEFAULT NULL COMMENT 'MIME 类型(image/png 等)',
  `biz_scene` varchar(32) DEFAULT 'variant-mother' COMMENT '业务场景, 现仅 variant-mother(举一反三母题图)',
  `oss_config_key` varchar(64) DEFAULT NULL COMMENT '实际使用的 sys_oss_config.config_key(留痕, 换桶可追溯)',
  `create_by` varchar(64) DEFAULT '' COMMENT '若依用户名',
  `create_user` bigint DEFAULT NULL COMMENT '上传者 sys_user.id(服务端 LoginHelper 强制, 不信前端)',
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`create_user`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=182 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='举一反三母题图上传留痕';

-- ----- biz_variation_method -----
DROP TABLE IF EXISTS biz_variation_method;
CREATE TABLE `biz_variation_method` (
  `code` varchar(16) NOT NULL COMMENT 'VM01..VM09',
  `name` varchar(40) NOT NULL COMMENT '数值/结构/情境变式/定值化/逆向构造/推广一般化/维度提升/条件增删/分类讨论化',
  `definition` text COMMENT '定义',
  `trigger_cond` varchar(500) DEFAULT NULL COMMENT '什么母题适合用它变',
  `similarity_low` decimal(3,2) DEFAULT NULL,
  `similarity_high` decimal(3,2) DEFAULT NULL,
  `vs_dna` varchar(500) DEFAULT NULL COMMENT '与纯DNA克隆的区别',
  `examples` json DEFAULT NULL COMMENT '本书真实例',
  `sort` int DEFAULT '0',
  PRIMARY KEY (`code`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='变式手法库(9算子)';

-- ----- biz_variation_trace -----
DROP TABLE IF EXISTS biz_variation_trace;
CREATE TABLE `biz_variation_trace` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mother_question_id` bigint NOT NULL COMMENT '母题',
  `variant_question_id` bigint NOT NULL COMMENT '变式',
  `method` varchar(32) NOT NULL COMMENT '9算子裸名',
  `method_detail` varchar(500) DEFAULT NULL COMMENT '相对母题改了什么',
  `variation_degree` decimal(3,2) DEFAULT NULL COMMENT '变式程度 0-1',
  `similarity_band` varchar(16) DEFAULT NULL COMMENT '高/中/低',
  `same_source` tinyint(1) DEFAULT '1' COMMENT '是否真同考点(0=错挂)',
  `created_by` varchar(32) DEFAULT 'reverse-dna' COMMENT 'reverse-dna反推 / forward-gen正向 / textbook书原生',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_variant` (`variant_question_id`),
  KEY `idx_mother` (`mother_question_id`),
  KEY `idx_method` (`method`)
) ENGINE=InnoDB AUTO_INCREMENT=658 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='举一反三过程表(正向燃料)';

-- ===================== 业务字典种子(sys_dict) =====================
INSERT INTO sys_dict_type(dict_id,tenant_id,dict_name,dict_type,remark) VALUES('20','000000','题目标注-认知层级','biz_anno_COG','课标四层认知层级');
INSERT INTO sys_dict_type(dict_id,tenant_id,dict_name,dict_type,remark) VALUES('21','000000','题目标注-核心素养','biz_anno_LITERACY','新课标九大数学核心素养');
INSERT INTO sys_dict_type(dict_id,tenant_id,dict_name,dict_type,remark) VALUES('22','000000','题目标注-思想方法','biz_anno_METHOD','八大数学思想方法');
INSERT INTO sys_dict_type(dict_id,tenant_id,dict_name,dict_type,remark) VALUES('23','000000','题目标注-情境场景','biz_anno_SCENE','题目情境/场景四分');
INSERT INTO sys_dict_type(dict_id,tenant_id,dict_name,dict_type,remark) VALUES('24','000000','题目标注-易错点陷阱','biz_anno_ERROR','常见易错点/陷阱分类');
INSERT INTO sys_dict_type(dict_id,tenant_id,dict_name,dict_type,remark) VALUES('25','000000','题目题型(形式)','biz_question_type','C-204 枚举字典化');
INSERT INTO sys_dict_type(dict_id,tenant_id,dict_name,dict_type,remark) VALUES('26','000000','题目难度档','biz_question_difficulty','C-204 枚举字典化');
INSERT INTO sys_dict_type(dict_id,tenant_id,dict_name,dict_type,remark) VALUES('27','000000','题目来源类型','biz_question_source','C-204 枚举字典化 [2026-07-02 收敛:考试类型语义并入biz_question_source_type,本字典冻结存量用]');
INSERT INTO sys_dict_type(dict_id,tenant_id,dict_name,dict_type,remark) VALUES('28','000000','题目来源(source_type)','biz_question_source_type','source_type 列字典化 [2026-07-02 定版S1:本字典=考试类型唯一权威(biz_paper.exam_type用);biz_question_source/biz_paper_type 语义重叠,冻结不再扩]');
INSERT INTO sys_dict_type(dict_id,tenant_id,dict_name,dict_type,remark) VALUES('29','000000','题目打标状态(label_status)','biz_question_label_status','魔法值字典化');
INSERT INTO sys_dict_type(dict_id,tenant_id,dict_name,dict_type,remark) VALUES('30','000000','题目标注完成度(annotate_status)','biz_question_annotate_status','魔法值字典化');
INSERT INTO sys_dict_type(dict_id,tenant_id,dict_name,dict_type,remark) VALUES('31','000000','题目考察类型(dim2)','biz_question_assessment_type','魔法值字典化');
INSERT INTO sys_dict_type(dict_id,tenant_id,dict_name,dict_type,remark) VALUES('32','000000','学科','biz_edu_subject','KG/卷库共享 学科枚举');
INSERT INTO sys_dict_type(dict_id,tenant_id,dict_name,dict_type,remark) VALUES('33','000000','学段','biz_edu_stage','KG/卷库共享 学段枚举');
INSERT INTO sys_dict_type(dict_id,tenant_id,dict_name,dict_type,remark) VALUES('34','000000','年级','biz_edu_grade','KG/卷库共享 年级枚举');
INSERT INTO sys_dict_type(dict_id,tenant_id,dict_name,dict_type,remark) VALUES('35','000000','册','biz_edu_volume','KG/卷库共享 上下册枚举');
INSERT INTO sys_dict_type(dict_id,tenant_id,dict_name,dict_type,remark) VALUES('36','000000','版本','biz_edu_edition','KG 教材版本(不含年份，年份存 edition_year)');
INSERT INTO sys_dict_type(dict_id,tenant_id,dict_name,dict_type,remark) VALUES('37','000000','卷型','biz_paper_type','卷库 卷型枚举(独立于 source_type) [2026-07-02 收敛:考试类型语义并入biz_question_source_type,本字典冻结存量用]');
INSERT INTO sys_dict_type(dict_id,tenant_id,dict_name,dict_type,remark) VALUES('38','000000','题目分科(科学专属)','biz_question_discipline','2026-07-02 定版A8:科学题的物/化/生/地/综合分科;数学恒空;与biz_edu_subject(学科)不同轴');

INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('100','000000','1','了解','UNDERSTAND','biz_anno_COG','课标认知层级-了解');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('101','000000','2','理解','COMPREHEND','biz_anno_COG','课标认知层级-理解');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('102','000000','3','掌握','MASTER','biz_anno_COG','课标认知层级-掌握');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('103','000000','4','灵活运用','APPLY','biz_anno_COG','课标认知层级-灵活运用');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('170','000000','1','概念混淆','CONCEPT','biz_anno_ERROR','易错-概念混淆');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('171','000000','2','计算失误','CALCULATION','biz_anno_ERROR','易错-计算失误');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('172','000000','3','审题偏差','READING','biz_anno_ERROR','易错-审题偏差');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('173','000000','4','隐含遗漏','IMPLICIT','biz_anno_ERROR','易错-隐含遗漏');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('174','000000','5','分类不全','CLASSIFY','biz_anno_ERROR','易错-分类不全');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('175','000000','6','表达不规范','EXPRESS','biz_anno_ERROR','易错-表达不规范');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('176','000000','7','思路缺失','THINKING','biz_anno_ERROR','易错-思路缺失');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('110','000000','1','抽象','ABSTRACT','biz_anno_LITERACY','核心素养-抽象');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('111','000000','2','运算','OPERATION','biz_anno_LITERACY','核心素养-运算');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('112','000000','3','几何直观','GEOMETRIC_VIEW','biz_anno_LITERACY','核心素养-几何直观');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('113','000000','4','空间观念','SPATIAL_VIEW','biz_anno_LITERACY','核心素养-空间观念');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('114','000000','5','推理','REASONING','biz_anno_LITERACY','核心素养-推理');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('115','000000','6','模型观念','MODEL_VIEW','biz_anno_LITERACY','核心素养-模型观念');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('116','000000','7','数据观念','DATA_VIEW','biz_anno_LITERACY','核心素养-数据观念');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('117','000000','8','应用意识','APPLICATION','biz_anno_LITERACY','核心素养-应用意识');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('118','000000','9','创新意识','INNOVATION','biz_anno_LITERACY','核心素养-创新意识');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('130','000000','1','数形结合','SHU_XING','biz_anno_METHOD','思想方法-数形结合');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('131','000000','2','分类讨论','FEN_LEI','biz_anno_METHOD','思想方法-分类讨论');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('132','000000','3','化归转化','HUA_GUI','biz_anno_METHOD','思想方法-化归转化');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('133','000000','4','方程函数','FANG_HAN','biz_anno_METHOD','思想方法-方程函数');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('134','000000','5','数学建模','JIAN_MO','biz_anno_METHOD','思想方法-数学建模');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('135','000000','6','特殊与一般','TE_SHU','biz_anno_METHOD','思想方法-特殊与一般');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('136','000000','7','待定系数','DAI_DING','biz_anno_METHOD','思想方法-待定系数');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('137','000000','8','数学归纳','GUI_NA','biz_anno_METHOD','思想方法-数学归纳');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('150','000000','1','纯数学','PURE_MATH','biz_anno_SCENE','情境-纯数学');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('151','000000','2','现实生活','REAL_LIFE','biz_anno_SCENE','情境-现实生活');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('152','000000','3','科学跨学科','CROSS_SCI','biz_anno_SCENE','情境-科学跨学科');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('153','000000','4','数学文化','CULTURE','biz_anno_SCENE','情境-数学文化');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('240','000000','1','浙教','1','biz_edu_edition',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('241','000000','2','人教','2','biz_edu_edition',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('226','000000','1','一年级','1','biz_edu_grade',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('227','000000','2','二年级','2','biz_edu_grade',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('228','000000','3','三年级','3','biz_edu_grade',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('229','000000','4','四年级','4','biz_edu_grade',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('230','000000','5','五年级','5','biz_edu_grade',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('231','000000','6','六年级','6','biz_edu_grade',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('232','000000','7','七年级','7','biz_edu_grade',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('233','000000','8','八年级','8','biz_edu_grade',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('234','000000','9','九年级','9','biz_edu_grade',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('235','000000','10','高一','10','biz_edu_grade',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('236','000000','11','高二','11','biz_edu_grade',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('237','000000','12','高三','12','biz_edu_grade',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('223','000000','1','小学','1','biz_edu_stage',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('224','000000','2','初中','2','biz_edu_stage',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('225','000000','3','高中','3','biz_edu_stage',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('221','000000','1','数学','1','biz_edu_subject',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('222','000000','2','科学','2','biz_edu_subject',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('238','000000','1','上册','1','biz_edu_volume',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('239','000000','2','下册','2','biz_edu_volume',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('242','000000','1','单元','1','biz_paper_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('243','000000','2','月考','2','biz_paper_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('244','000000','3','期中','3','biz_paper_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('245','000000','4','期末','4','biz_paper_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('208','000000','1','未标','0','biz_question_annotate_status',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('209','000000','2','已标全','1','biz_question_annotate_status',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('210','000000','3','部分','2','biz_question_annotate_status',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('211','000000','1','概念辨析','概念辨析','biz_question_assessment_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('212','000000','2','直接计算','直接计算','biz_question_assessment_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('213','000000','3','公式套用','公式套用','biz_question_assessment_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('214','000000','4','性质判定','性质判定','biz_question_assessment_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('215','000000','5','证明推理','证明推理','biz_question_assessment_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('216','000000','6','应用建模','应用建模','biz_question_assessment_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('217','000000','7','作图','作图','biz_question_assessment_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('218','000000','8','探究归纳','探究归纳','biz_question_assessment_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('219','000000','9','阅读理解迁移','阅读理解迁移','biz_question_assessment_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('220','000000','10','纠错','纠错','biz_question_assessment_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('185','000000','1','基础','1','biz_question_difficulty',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('186','000000','2','中等','2','biz_question_difficulty',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('187','000000','3','较难','3','biz_question_difficulty',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('188','000000','4','压轴','4','biz_question_difficulty',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('247','000000','1','物理','1','biz_question_discipline',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('248','000000','2','化学','2','biz_question_discipline',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('249','000000','3','生物','3','biz_question_discipline',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('250','000000','4','地学','4','biz_question_discipline',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('251','000000','5','综合探究','5','biz_question_discipline',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('204','000000','1','未标','0','biz_question_label_status',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('205','000000','2','AI已标','1','biz_question_label_status',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('206','000000','3','已审核','2','biz_question_label_status',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('207','000000','4','争议','3','biz_question_label_status',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('189','000000','1','教材/同步','1','biz_question_source',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('190','000000','2','质检/调研','2','biz_question_source',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('191','000000','3','竞赛','3','biz_question_source',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('192','000000','4','中考真题','4','biz_question_source',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('193','000000','5','模拟卷','5','biz_question_source',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('194','000000','6','自编/原创','6','biz_question_source',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('195','000000','7','期中','7','biz_question_source',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('196','000000','8','期末','8','biz_question_source',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('197','000000','1','中考真题','1','biz_question_source_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('198','000000','2','模拟','2','biz_question_source_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('199','000000','3','期末','3','biz_question_source_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('200','000000','4','月考','4','biz_question_source_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('201','000000','5','单元','5','biz_question_source_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('202','000000','6','自编','6','biz_question_source_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('203','000000','7','其他','9','biz_question_source_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('252','000000','8','期中','10','biz_question_source_type','2026-07-02 收敛补值');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('253','000000','9','质检调研','11','biz_question_source_type','2026-07-02 收敛补值');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('254','000000','10','同步练习','12','biz_question_source_type','2026-07-02 收敛补值');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('255','000000','11','竞赛','13','biz_question_source_type','2026-07-02 收敛补值');
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('177','000000','1','选择题','1','biz_question_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('178','000000','2','判断题','2','biz_question_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('179','000000','3','应用题','3','biz_question_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('180','000000','4','填空题','4','biz_question_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('181','000000','5','解答题','5','biz_question_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('182','000000','6','作图题','6','biz_question_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('183','000000','7','计算题','7','biz_question_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('184','000000','8','证明题','8','biz_question_type',NULL);
INSERT INTO sys_dict_data(dict_code,tenant_id,dict_sort,dict_label,dict_value,dict_type,remark) VALUES('246','000000','9','实验探究题','9','biz_question_type','2026-07-02 定版A3:科学真卷分区实证新增');

-- ============================================================
-- PRD-C-211 系统管理中心（2026-07-03）：org_admin 权限种子
-- 上线全量建库时必须带上；dev 库已 apply（探针期种入并复测通过）。
-- 语义正本 = only-one/权限与内容归属模型-定版.md §2/§4 + PRD-C-211 D2 拍板：
--   org_admin 可见 用户管理（全 CRUD+重置密码）+ 部门管理（只读），数据限本部门；
--   建部门/角色/字典等系统级仅 superadmin（uid=1 内建放行，无需种）。
-- ============================================================
-- role 7 = org_admin（角色本体已于 2026-07-03 P2 期种入）
-- data_scope='3'（本部门）—— 对齐权限模型"同部门精确匹配"；
--   ⚠️ 勿配 '1'(全部) 的其他角色给 org_admin 账号：多角色数据范围取并集会顶掉本部门限制
--   （踩坑实录：teacher001 曾挂遗留 data_admin(scope=1) 致数据范围失效）。
UPDATE sys_role SET data_scope='3' WHERE role_id=7;
-- 用户管理：目录1 + 列表100 + 查询/新增/修改/删除/重置密码按钮
INSERT INTO sys_role_menu(role_id, menu_id) VALUES (7,1),(7,100),(7,1001),(7,1002),(7,1003),(7,1004),(7,1007);
-- 部门管理：列表103 + 查询1017（只读；建部门=超管的机构管理权）
INSERT INTO sys_role_menu(role_id, menu_id) VALUES (7,103),(7,1017);

-- ------------------------------------------------------------
-- PRD-C-211 追加（2026-07-03）：角色收口全量种子（上线新库必带；dev 已同态）
-- 正本 = only-one/权限与内容归属模型-定版.md §2/§6.1
-- ------------------------------------------------------------
INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_time, remark) VALUES
(5,'000000','教师','teacher',5,'5',1,1,'0','0',NOW(),'机构成员老师:业务功能,无管理中心;数据仅本人'),
(6,'000000','数据管理员','data_admin',6,'1',1,1,'0','0',NOW(),'🔴机器账号专用(灌库/打标agent):全数据范围;人类账号禁挂——多角色data_scope取并集会顶掉org_admin本机构限制'),
(7,'000000','机构管理员','org_admin',7,'3',1,1,'0','0',NOW(),'机构负责人:管理中心见用户(本机构CRUD)+机构(只读);通常兼课=同时挂teacher;官方内容不可改')
ON DUPLICATE KEY UPDATE remark=VALUES(remark), data_scope=VALUES(data_scope);
-- RuoYi 演示角色停用（若新库沿用 RuoYi init 自带 role 3/4）
UPDATE sys_role SET status='1', remark='RuoYi演示遗留,已停用勿挂(2026-07-03 角色收口)' WHERE role_id IN (3,4);

-- ============================================================
-- PRD-C-213 教学安排与备课闭环（2026-07-05）：教学安排域 8 新表 + biz_question 3 新列
-- 契约正本 = workplace/.prd_ccw/PRD-C/PRD-C-213/artifacts/契约/批0-契约冻结.md §二
-- 落位：C 线 book-server（:8090，库 ai_lesson_prep），包 org.dromara.book，挂 /teacher/schedule/**。
-- RuoYi 基类列 = create_dept/create_by(Long userId)/create_time/update_by/update_time/remark（BaseEntity 自动填充）。
-- create_by = 归属老师（sys_user.id）。JSON 列在实体存 String，Service 侧 Jackson 解析。
-- 🔴 无冗余聚合列：total_lessons / 学员数 / 已排已上请假数 / 进度 全走实时聚合查询。
-- ============================================================

-- ----- 1. biz_student（排课对象·学生档案 + 学生肖像 profile_json）-----
DROP TABLE IF EXISTS `biz_student`;
CREATE TABLE `biz_student` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(50) NOT NULL COMMENT '学生姓名',
  `grade` varchar(30) DEFAULT NULL COMMENT '年级(如 四年级)',
  `subject` varchar(30) DEFAULT NULL COMMENT '学科(如 思维数学)',
  `textbook` varchar(100) DEFAULT NULL COMMENT '教材/进度环境说明',
  `parent_phone` varchar(20) DEFAULT NULL COMMENT '家长电话',
  `color` varchar(20) DEFAULT NULL COMMENT '日历着色(空则服务端色板轮转分配)',
  `profile_json` json DEFAULT NULL COMMENT '学生肖像:{traits[],level{desc,target_layer},env,history[],error_signals[]}',
  `archived` char(1) NOT NULL DEFAULT '0' COMMENT '归档:0在册/1归档(归档≠删,不进排课选择器)',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '归属老师(sys_user.id)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_owner` (`create_by`),
  KEY `idx_archived` (`archived`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='排课对象·学生档案(含学生肖像)';

-- ----- 2. biz_class（排课对象·班课档案 + 班级肖像同构）-----
DROP TABLE IF EXISTS `biz_class`;
CREATE TABLE `biz_class` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(50) NOT NULL COMMENT '班课名称',
  `grade` varchar(30) DEFAULT NULL COMMENT '年级',
  `subject` varchar(30) DEFAULT NULL COMMENT '学科',
  `color` varchar(20) DEFAULT NULL COMMENT '日历着色(空则色板轮转)',
  `profile_json` json DEFAULT NULL COMMENT '班级肖像(与学生肖像同构)',
  `archived` char(1) NOT NULL DEFAULT '0' COMMENT '归档:0在册/1归档',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '归属老师(sys_user.id)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_owner` (`create_by`),
  KEY `idx_archived` (`archived`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='排课对象·班课档案(含班级肖像)';

-- ----- 3. biz_class_student（班课学员关系;学员数实时聚合不落列）-----
DROP TABLE IF EXISTS `biz_class_student`;
CREATE TABLE `biz_class_student` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `class_id` bigint NOT NULL COMMENT '班课id(biz_class.id)',
  `student_id` bigint NOT NULL COMMENT '学生id(biz_student.id)',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_student` (`class_id`,`student_id`),
  KEY `idx_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='班课学员关系';

-- ----- 4. biz_course_plan（课程计划;无total_lessons/无target绑定列,均实时聚合)-----
DROP TABLE IF EXISTS `biz_course_plan`;
CREATE TABLE `biz_course_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '计划名称',
  `target_type` char(1) NOT NULL DEFAULT '0' COMMENT '适配对象类型:0学生/1班级',
  `term_tag` varchar(20) DEFAULT NULL COMMENT '期段(字典:暑假/上学期/寒假/下学期)',
  `year` int DEFAULT NULL COMMENT '年份',
  `material_note` varchar(200) DEFAULT NULL COMMENT '素材池说明(如 学而思36周书·挑题制)',
  `default_seg_template` json DEFAULT NULL COMMENT '默认分段模板[{name,style,topic}](lesson 空则继承)',
  `default_paper_slots` json DEFAULT NULL COMMENT '计划级默认卷位模板(PRD-B-101;lesson.paper_slots 空则继承)',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '状态:0草稿/1启用/2归档',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '归属老师(sys_user.id)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_owner` (`create_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程计划(大纲层);total_lessons=count(lessons)实时聚合,绑定关系活在session.plan_id';

-- ----- 5. biz_course_plan_lesson（计划课次·大纲/细备双精度)-----
DROP TABLE IF EXISTS `biz_course_plan_lesson`;
CREATE TABLE `biz_course_plan_lesson` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `plan_id` bigint NOT NULL COMMENT '所属计划id(biz_course_plan.id)',
  `lesson_seq` int NOT NULL DEFAULT '0' COMMENT '课次序号(第几次课,顺延/排课按此)',
  `title` varchar(100) DEFAULT NULL COMMENT '课次标题/主题',
  `lesson_type` char(1) NOT NULL DEFAULT '0' COMMENT '课次类型:0教学/1测试',
  `tag` varchar(50) DEFAULT NULL COMMENT '自由标签(吃透课①走这)',
  `source_ref` varchar(200) DEFAULT NULL COMMENT '素材源(如 学而思四年级P25)',
  `thinking_action` varchar(100) DEFAULT NULL COMMENT '思维动作(内部字段)',
  `layer_target` varchar(20) DEFAULT NULL COMMENT '目标层数(挂课次;与题星级两刻度互不换算)',
  `parent_copy` varchar(200) DEFAULT NULL COMMENT '家长版口语文案',
  `kg_node_ids` json DEFAULT NULL COMMENT 'KG锚点(biz_subject.id 数组)',
  `seg_template` json DEFAULT NULL COMMENT '本课分段配置[{name,style,topic}](空则继承plan)',
  `paper_slots` json DEFAULT NULL COMMENT '专项卷位[{slot_seq,name,style,rules,note,paper_id,manual_ready}](PRD-B-101;空则继承plan.default_paper_slots)',
  `prep_state` char(1) NOT NULL DEFAULT '0' COMMENT '内容态:0大纲态/1细备中/2已备好',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_plan_seq` (`plan_id`,`lesson_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计划课次(大纲态无题也合法)';

-- ----- 6. biz_schedule_session（排课场次)-----
DROP TABLE IF EXISTS `biz_schedule_session`;
CREATE TABLE `biz_schedule_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `target_type` char(1) NOT NULL DEFAULT '0' COMMENT '对象类型:0学生/1班级',
  `target_id` bigint NOT NULL COMMENT '对象id(biz_student.id 或 biz_class.id)',
  `plan_id` bigint DEFAULT NULL COMMENT '绑定计划id(可空)',
  `plan_lesson_id` bigint DEFAULT NULL COMMENT '绑定课次id(可空;autoBind按lesson_seq绑)',
  `session_date` date NOT NULL COMMENT '上课日期',
  `start_time` time DEFAULT NULL COMMENT '开始时间',
  `end_time` time DEFAULT NULL COMMENT '结束时间',
  `session_type` char(1) NOT NULL DEFAULT '1' COMMENT '场次类型:1正课/2测试/3外部占位',
  `session_status` char(1) NOT NULL DEFAULT '0' COMMENT '状态:0已排/1已上/2请假/3取消',
  `prep_status` char(1) NOT NULL DEFAULT '0' COMMENT '场次备课态:0未备/1备课中/2已备好',
  `lesson_locked` char(1) NOT NULL DEFAULT '0' COMMENT '内容锁定:0否/1是(顺延跳过锁定场次)',
  `external_title` varchar(100) DEFAULT NULL COMMENT '外部占位标题',
  `note` varchar(200) DEFAULT NULL COMMENT '备注',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '归属老师(sys_user.id;老师撞场口径按此)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_target_date` (`target_type`,`target_id`,`session_date`),
  KEY `idx_date` (`session_date`),
  KEY `idx_owner` (`create_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='排课场次';

-- ----- 7. biz_prep_pack（备课包;1:1 course_plan_lesson 或散课 session,二选一)-----
DROP TABLE IF EXISTS `biz_prep_pack`;
CREATE TABLE `biz_prep_pack` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `plan_lesson_id` bigint DEFAULT NULL COMMENT '绑定课次id(与session_id二选一,uniq)',
  `session_id` bigint DEFAULT NULL COMMENT '绑定散课场次id(与plan_lesson_id二选一,uniq)',
  `segs` json DEFAULT NULL COMMENT '分段内容[{name,style,question_ids[str],rules,note}]',
  `artifacts` json DEFAULT NULL COMMENT '产物[{seg,file,pages}]',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '状态:0装配中/1已生成/2已备好',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lesson` (`plan_lesson_id`),
  UNIQUE KEY `uk_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='备课包(段内容物只有题目)';

-- ----- 8. biz_session_review（课后回收;重复提交=覆盖并把上一版进prev_json)-----
DROP TABLE IF EXISTS `biz_session_review`;
CREATE TABLE `biz_session_review` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id` bigint NOT NULL COMMENT '场次id(uniq)',
  `item_results` json DEFAULT NULL COMMENT '逐题结果[{question_id?,seg,seq,result(对/错/卡),cause(计算/概念辨析/策略/其他)}]',
  `teacher_note` varchar(1000) DEFAULT NULL COMMENT '老师备注',
  `parent_msg` text COMMENT '生成的家长反馈消息',
  `portrait_delta` json DEFAULT NULL COMMENT '写回profile的error_signals(by=system,status=pending,带session_id溯源)',
  `prev_json` json DEFAULT NULL COMMENT '上一版整体快照(重复提交覆盖时留痕)',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本号(乐观锁,每次覆盖+1)',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课后回收(对错错因→肖像delta+家长反馈)';

-- ----- 9. biz_question ALTER：私有题池增列(星级/专项挂题;layer_target挂课次,两刻度互不换算)-----
-- 🔴 应用前先 SHOW COLUMNS 确认三列不存在(存在则跳过);dev 用 pymysql 逐列判存在再 ADD。
ALTER TABLE `biz_question`
  ADD COLUMN `source_ref` varchar(200) DEFAULT NULL COMMENT 'PRD-C-213 素材源(私有题池:学而思四年级P25)',
  ADD COLUMN `star_level` char(1) DEFAULT NULL COMMENT 'PRD-C-213 星级 1★/2★★/3★★★(专项卷分层)',
  ADD COLUMN `topic_tag` varchar(50) DEFAULT NULL COMMENT 'PRD-C-213 专项名(自由文本,字典化后置)';

-- ── PRD-C-213 字典种子：课程计划期段 biz_term_tag（2026-07-05，值=中文文本与业务列零迁移）──
-- 🔴 prod 手动执行 + 执行后必刷字典缓存（DELETE /system/dict/type/refreshCache 或重启 BE）。
-- dict_id/dict_code 为示意占位，prod 插入时按该库雪花习惯取号（或由超管在字典管理 UI 录入等价数据）。
INSERT INTO `sys_dict_type` (dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, remark)
VALUES (1826050689283072, '000000', '课程计划期段', 'biz_term_tag', 103, 1, NOW(), 'PRD-C-213 教学安排:课程计划term_tag(值=中文文本,与既有数据一致)');
INSERT INTO `sys_dict_data` (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, remark) VALUES
(1826050689283073, '000000', 1, '暑假',   '暑假',   'biz_term_tag', '', 'primary', 'N', 103, 1, NOW(), 'PRD-C-213'),
(1826050689283074, '000000', 2, '上学期', '上学期', 'biz_term_tag', '', 'primary', 'N', 103, 1, NOW(), 'PRD-C-213'),
(1826050689283075, '000000', 3, '寒假',   '寒假',   'biz_term_tag', '', 'primary', 'N', 103, 1, NOW(), 'PRD-C-213'),
(1826050689283076, '000000', 4, '下学期', '下学期', 'biz_term_tag', '', 'primary', 'N', 103, 1, NOW(), 'PRD-C-213');

-- ============================================================
-- PRD-C-213 修复轮（BUG-001 档案建模 + S1 计划归属）2026-07-05
-- 台账 = workplace/.prd_ccw/PRD-C/PRD-C-213/bug/PRD-bug.md BUG-001/BUG-013
-- 建模拍板：
--   · biz_student/biz_class 删 grade/textbook 自由文本列，改「grade_no(1-12) + grade_year(学年锚) + textbook_edition(字典码)」；
--     年级/学段/册次是【推导状态不落列】：当前年级 = grade_no + (当前学年起始年 - grade_year)，9/1 进位；
--     期段边界（类常量，yml 覆盖口 edu.term.*）= 7/1-8/31 暑假、9/1-1/15 上学期、1/16-2/15 寒假、2/16-6/30 下学期。
--     推导实现 = org.dromara.book.util.EduTermUtil。
--   · grade_year 语义 = grade_no 生效学年的起始年（如 2026 = 2026-09-01 起学年）；暑期录入「升四」即 grade_no=4, grade_year=2026。
--   · subject 列保留，改存字典码（biz_edu_subject：1数学/2科学/3语文/4英语）。
--   · textbook_edition 存字典码（biz_edu_edition：1浙教/2人教/3北师大/4苏教），"人教版三年级下册"式全串由推导生成。
--   · S1：biz_course_plan 加 target_id（计划归属对象，与 target_type 联合），建计划必传；换绑走
--     POST /teacher/schedule/target/{targetType}/{targetId}/rebind-plan。
-- 字典复用结论（2026-07-05 查 dev 库）：biz_edu_grade(1-12)/biz_term_tag 已有全量复用；
--   biz_edu_edition 补 3北师大/4苏教、biz_edu_subject 补 3语文/4英语（seed 见 dev-fix 迁移脚本）。
-- 🔴 本段为全量新建口径的收敛 ALTER；dev 存量库执行含回填的
--   sql/dev-fix/2026-07-05-c213-r1a-student-remodel-plan-target.sql，不要直接跑本段删列。
-- ============================================================

ALTER TABLE `biz_student`
  ADD COLUMN `grade_no` int DEFAULT NULL COMMENT '年级(1-12,字典biz_edu_grade;=grade_year学年就读年级,当前年级按9/1进位推导)' AFTER `name`,
  ADD COLUMN `grade_year` int DEFAULT NULL COMMENT 'grade_no生效学年起始年(如2026=2026-09-01起学年)' AFTER `grade_no`,
  ADD COLUMN `textbook_edition` varchar(20) DEFAULT NULL COMMENT '教材版本字典码(biz_edu_edition:1浙教/2人教/3北师大/4苏教)' AFTER `grade_year`,
  MODIFY COLUMN `subject` varchar(30) DEFAULT NULL COMMENT '学科字典码(biz_edu_subject:1数学/2科学/3语文/4英语)',
  DROP COLUMN `grade`,
  DROP COLUMN `textbook`;

ALTER TABLE `biz_class`
  ADD COLUMN `grade_no` int DEFAULT NULL COMMENT '年级(1-12,字典biz_edu_grade;允许NULL)' AFTER `name`,
  ADD COLUMN `grade_year` int DEFAULT NULL COMMENT 'grade_no生效学年起始年(允许NULL)' AFTER `grade_no`,
  ADD COLUMN `textbook_edition` varchar(20) DEFAULT NULL COMMENT '教材版本字典码(biz_edu_edition)' AFTER `grade_year`,
  MODIFY COLUMN `subject` varchar(30) DEFAULT NULL COMMENT '学科字典码(biz_edu_subject)',
  DROP COLUMN `grade`;

ALTER TABLE `biz_course_plan`
  ADD COLUMN `target_id` bigint DEFAULT NULL COMMENT 'S1 计划归属对象id(biz_student.id 或 biz_class.id,与target_type联合;建计划必传)' AFTER `target_type`,
  ADD KEY `idx_target` (`target_type`,`target_id`);

-- ============================================================
-- PRD-C-213 修复轮 R1b（备课域数据结构矫正 S2-S5 + BUG-003 防重入）2026-07-05
-- 台账 = workplace/.prd_ccw/PRD-C/PRD-C-213/bug/PRD-bug.md BUG-003/BUG-013
-- 拍板：
--   · S2/S3 pack.status = 备课状态唯一权威：删 biz_course_plan_lesson.prep_state；
--     课次 VO 的 prepState 键名保留兼容，值改为按 plan_lesson_id join biz_prep_pack 推导（无包='0'）；
--     biz_schedule_session.prep_status 保留作日历色点缓存（建包/渲染联动 + 绑定/换绑时按包状态回填）。
--   · S2 一课一包闸：场次已绑课次时从场次入口建/取包一律归并 lesson 口径，不再产生双包（代码层）。
--   · S5 biz_session_review.parent_msg 删列：家长消息不落库，submit 生成仅返回前端即时显示，
--     get 按存量 item_results + 备课包段落即时生成（内部词剥离防线原样保留）。
--   · BUG-003 取消/请假/标已上防重入：仅 session_status='0'(已排) 可操作、不做恢复（BE 闸，代码层，无 DDL）。
-- ============================================================

ALTER TABLE `biz_course_plan_lesson` DROP COLUMN `prep_state`;

ALTER TABLE `biz_session_review` DROP COLUMN `parent_msg`;

-- ============================================================
-- 学科归位到课程安排层（排课总览 bug 修复轮）2026-07-11
-- 拍板：学科原只挂学生（单值），一生两科（如数学+科学轮流）无处安放、排课单科目列空白。
--   · biz_course_plan.subject：一计划一科（字典 biz_edu_subject：1数学/2科学/3语文/4英语）
--   · biz_schedule_session.subject：散排/覆盖用；NULL = 展示时兜底链 场次→计划→学生对象
--   · biz_student.subject 保留作默认科目（建对象时的主学科）
-- ============================================================

ALTER TABLE `biz_course_plan` ADD COLUMN `subject` varchar(20) NULL COMMENT '学科(字典biz_edu_subject;一计划一科)' AFTER `term_tag`;

ALTER TABLE `biz_schedule_session` ADD COLUMN `subject` varchar(20) NULL COMMENT '学科(字典biz_edu_subject;NULL=兜底计划/对象)' AFTER `session_type`;

-- ============================================================
-- 课时绑定「书籍章节」材料位（B 位 2026-07-15）
-- 拍板：材料位从"仅专项"扩为"专项 ∪ 书章节"——课次可直接绑书架书（biz_shelf_book）
--   的目录节点（biz_shelf_node），备课态口径同步扩为「有专项或有书章节 = 已备好」。
--   · book_node_ids 与 special_ids/kg_node_ids 同构（json id 数组，字符串元素）
--   · 🔴 更新只 UPDATE 本列（partial updateById），绝不整行 upsert（防抹 paper_slots 绑定事故）
-- 已于 2026-07-15 直接 apply 到 :3307 ai_lesson_prep（四线共库，一次生效全线）。
-- ============================================================

ALTER TABLE `biz_course_plan_lesson`
  ADD COLUMN `book_node_ids` json NULL COMMENT '本课绑定的书章节节点id数组JSON(biz_shelf_node.id;材料位,只UPDATE单列)' AFTER `special_ids`;

-- 2026-07-17 书架公开可读（PRD 无卡·用户直令：小学数学 16 本书对全员开放）
-- 读/导出/绑定放行 is_public=1；写路径仍限 owner（ShelfService.requireReadableBook / requireOwnedBook 分闸）
ALTER TABLE biz_shelf_book ADD COLUMN is_public tinyint NOT NULL DEFAULT 0 COMMENT '公开可读:0私有 1全员可见(读/导出/绑定,写仍限owner)';
-- 数据变更（prod 需手工同步）：小学数学 16 本书 UPDATE biz_shelf_book SET is_public=1 WHERE id IN (…16 个 bookId 见台账…);

-- ============================================================
-- PRD-006 录题比对审核（按页人工审核确认 + 问题登记）批1  A 位 2026-07-20
-- 拍板：录入验收转「人工按页比对源书原版、逐页确认」。给每题补 source_page 元数据
--   （脚本从源 PDF 文本反查题干定位页码，单调填充；explain 块归到其后第一道题的页）。
--   页级审核状态 biz_review_page（书×源页，页级确认）；问题登记 biz_review_issue（跨书沉淀）。
-- 已于 2026-07-20 直接 apply 到 :3307 ai_lesson_prep（四线共库，一次生效全线）。
-- 数据：一上/一下/四上/四下 4 本 biz_shelf_item.source_page 已回填（覆盖 100%，脚本反查+单调填充）。
-- ============================================================

ALTER TABLE `biz_shelf_item`
  ADD COLUMN `source_page` int NULL COMMENT '源书页码(PRD-006 反查回填;NULL=待定位)' AFTER `explain_json`;

DROP TABLE IF EXISTS `biz_review_page`;
CREATE TABLE `biz_review_page` (
  `id` bigint NOT NULL COMMENT '主键(雪花)',
  `book_id` bigint NOT NULL COMMENT '书 biz_shelf_book.id',
  `page_no` int NOT NULL COMMENT '源书页码',
  `reviewed` tinyint NOT NULL DEFAULT 0 COMMENT '0未审 1已确认',
  `reviewed_by` bigint DEFAULT NULL COMMENT '确认人 sys_user.id',
  `reviewed_time` datetime DEFAULT NULL COMMENT '确认时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_book_page` (`book_id`,`page_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='PRD-006 页级审核状态(书×源页,页级确认)';

DROP TABLE IF EXISTS `biz_review_issue`;
CREATE TABLE `biz_review_issue` (
  `id` bigint NOT NULL COMMENT '主键(雪花)',
  `book_id` bigint NOT NULL COMMENT '书 biz_shelf_book.id',
  `question_id` bigint DEFAULT NULL COMMENT '题 biz_question.id(可空:整页问题)',
  `source_page` int DEFAULT NULL COMMENT '源书页码',
  `issue_type` varchar(32) DEFAULT NULL COMMENT '问题类型',
  `description` text COMMENT '问题描述',
  `status` varchar(16) NOT NULL DEFAULT '待处理' COMMENT '待处理/已改/搁置',
  `create_by` bigint DEFAULT NULL COMMENT '登记人 sys_user.id',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_book` (`book_id`),
  KEY `idx_book_type_status` (`book_id`,`issue_type`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='PRD-006 录题问题登记表(跨书沉淀,转录改进原料)';

-- ============================================================
-- PRD-007 飞书机器人多身份接入（B 位 2026-07-20）
-- sys_user 加 openid 映射列：飞书 open_id → teacher(user_id)，/auth/botLogin 免密签发用。
-- dev :3307 已于 2026-07-20 直接 apply（四线共库一次生效）；🔴 prod RDS(ai_lesson_prep) 部署时需手工同步。
-- 配套非 DDL 项（部署勿漏）：BE env 需注入 BOT_SECRET（compose environment 显式透传，见卡内部署须知.md）。
-- ============================================================

-- 🔴 UNIQUE（verifier D1 加固 2026-07-20）：DB 层杜绝一 open_id 绑多账号（双绑会让 selectVoOne 抛异常且语义混乱）。
-- dev 已按唯一索引 apply（先普通索引后 DROP+ADD UNIQUE 转换）；prod 直接按下面唯一版执行。
ALTER TABLE sys_user ADD COLUMN openid VARCHAR(64) NULL COMMENT '飞书 open_id（PRD-007 机器人免密身份映射）',
  ADD UNIQUE INDEX idx_sys_user_openid (openid);

-- 数据变更（绑定=授权，管理员操作；prod 按首批名单执行）：
-- UPDATE sys_user SET openid='ou_xxx' WHERE user_name='某老师账号';
