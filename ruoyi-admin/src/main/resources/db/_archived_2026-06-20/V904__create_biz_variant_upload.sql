-- ============================================================================
-- V904: PRD-C(举一反三 agent) — biz_variant_upload 母题图上传留痕表
-- ----------------------------------------------------------------------------
-- 目的: book-ui 举一反三页"本地选图→上传 OSS→拿公网 URL"的上传留痕(谁/何时/传了什么/落在哪个桶)。
--   上传走 RuoYi OssFactory(数据归 RuoYi, Python toolkit 不碰 OSS 凭据);
--   产出的 oss_url 会被外部 LLM 中转抓取做多模态分析, 必须公网可读(默认 OSS 配置
--   access_policy=1 公开读, dev 实测匿名 GET 200)。
-- 🔴 C 线预留段 V901+ 顺延 = V904(权威约定见 book-ai/CLAUDE.md §3.1, 号定不改)。
--    纯新建表(CREATE TABLE, 无 DROP), prod 部署直接 apply, dev 由 BE 重启 flyway 自动 apply。
--
-- 🔴 跑此文件用 utf8mb4(中文 COMMENT)。
-- ============================================================================

CREATE TABLE biz_variant_upload (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  oss_url         VARCHAR(500)  NOT NULL                   COMMENT 'OSS 公网可读 URL(给 FE 回显 + LLM 中转抓取)',
  object_key      VARCHAR(500)  DEFAULT NULL               COMMENT 'OSS 对象键(桶内路径, 删除/审计用)',
  original_name   VARCHAR(255)  DEFAULT NULL               COMMENT '上传时的原始文件名',
  file_size       BIGINT        DEFAULT NULL               COMMENT '文件字节数',
  content_type    VARCHAR(100)  DEFAULT NULL               COMMENT 'MIME 类型(image/png 等)',
  biz_scene       VARCHAR(32)   DEFAULT 'variant-mother'   COMMENT '业务场景, 现仅 variant-mother(举一反三母题图)',
  oss_config_key  VARCHAR(64)   DEFAULT NULL               COMMENT '实际使用的 sys_oss_config.config_key(留痕, 换桶可追溯)',

  -- RuoYi 标配审计列(照 biz_question 风格)
  create_by       VARCHAR(64)   DEFAULT ''                 COMMENT '若依用户名',
  create_user     BIGINT        DEFAULT NULL               COMMENT '上传者 sys_user.id(服务端 LoginHelper 强制, 不信前端)',
  create_time     DATETIME      DEFAULT NULL,
  update_by       VARCHAR(64)   DEFAULT '',
  update_time     DATETIME      DEFAULT NULL,
  remark          VARCHAR(500)  DEFAULT NULL,

  PRIMARY KEY (id),
  KEY idx_user_time (create_user, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举一反三母题图上传留痕';

-- 验收(apply 后人工核对):
-- SHOW CREATE TABLE biz_variant_upload;
-- SELECT version, success FROM flyway_schema_history WHERE version='904';
