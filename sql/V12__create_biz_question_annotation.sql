-- PRD-B-012 T2: 多值标注表 biz_question_annotation
-- 题目多维标注 (认知 COG / 素养 LITERACY / 思想方法 METHOD / 场景 SCENE / 错因 ERROR / 考频 EXAM_FREQ)
-- 一题多维 + 同维度可多值 + 多来源 (S/A/U/O) 共存
-- 唯一键 uk_qda: (question_id, dimension, value_code, source) 保证同来源同维度同值不重复
CREATE TABLE biz_question_annotation (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  question_id BIGINT       NOT NULL,
  dimension   VARCHAR(16)  NOT NULL COMMENT 'COG/LITERACY/METHOD/SCENE/ERROR/EXAM_FREQ',
  value_code  VARCHAR(32)  NOT NULL COMMENT '维度值码(挂字典)',
  value_text  VARCHAR(64)  NULL     COMMENT '值冗余可读(查询免JOIN字典)',
  source      CHAR(1)      NOT NULL DEFAULT 'A' COMMENT 'S平台 A AI U老师 O原站',
  confidence  TINYINT      NULL     COMMENT 'AI置信度0-100',
  create_time DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_qda (question_id, dimension, value_code, source),
  KEY idx_dim_val (dimension, value_code),
  KEY idx_question (question_id)
) COMMENT='题目多值标注维度(认知/素养/思想/场景/错因/考频)';
