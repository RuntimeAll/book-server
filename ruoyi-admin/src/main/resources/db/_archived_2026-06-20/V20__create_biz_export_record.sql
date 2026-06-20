-- ============================================================================
-- V20: PRD-A-014(试卷导出异步化) — biz_export_record 试卷导出任务记录表
-- ----------------------------------------------------------------------------
-- 目的: 试卷导出从纯前端 jsPDF 同步生成升级为后端异步生成的任务留痕表。
--   一行 = 一次导出任务: 谁(user_id, 服务端 LoginHelper 强制)/导哪卷(paper_id)/
--   导出配置(options JSON: variant+watermark+ids)/状态机(status 0排队/1生成中/2完成/3失败)/
--   进度(progress 0-100)/耗时(duration_ms 用于滚动预估)/产物(file_url+file_size)/
--   失败原因(error_msg)/OSS 留存到期(expire_at = now+7天, 惰性判过期)。
--
-- 🔴 A 线顺序号 V20(V19 已用; 权威约定见 book-ai/CLAUDE.md §3.1)。
--    纯新建表(CREATE TABLE, 无 DROP), prod 部署直接 apply, dev 由 BE 重启 flyway 自动 apply。
-- 🔴 无 tenant_id 列 → ruoyi-admin/src/main/resources/application.yml tenant.excludes
--    必加 biz_export_record(V904/biz_variant_upload 同款单租户约定), 否则多租户拦截器
--    注入 AND tenant_id 致 "Unknown column 'tenant_id'" 全 500。
-- 🔴 跑此文件用 utf8mb4(中文 COMMENT)。
-- ============================================================================

CREATE TABLE biz_export_record (
  id           BIGINT        NOT NULL                  COMMENT '主键(雪花 id, 出 VO 必 string)',
  user_id      BIGINT        NOT NULL                  COMMENT '导出发起者 sys_user.id(服务端 LoginHelper 强制, 不信前端)',
  paper_id     BIGINT        DEFAULT NULL              COMMENT '来源试卷 biz_paper.id(可空: 篮子直接导出无卷)',
  file_name    VARCHAR(255)  DEFAULT NULL              COMMENT '导出文件名(弹窗里填的, 默认=卷名)',
  options      JSON          DEFAULT NULL              COMMENT '导出配置 JSON: {variant, watermark, ids[]}',
  status       CHAR(1)       DEFAULT '0'               COMMENT '状态: 0排队 1生成中 2完成 3失败',
  progress     TINYINT       DEFAULT 0                 COMMENT '进度 0-100',
  duration_ms  INT           DEFAULT NULL              COMMENT '生成耗时(毫秒, 用于滚动预估模型)',
  file_url     VARCHAR(500)  DEFAULT NULL              COMMENT 'OSS 公网可访 PDF URL',
  file_size    BIGINT        DEFAULT NULL              COMMENT 'PDF 字节数',
  error_msg    VARCHAR(500)  DEFAULT NULL              COMMENT '失败原因(status=3 时)',
  expire_at    DATETIME      DEFAULT NULL              COMMENT 'OSS 留存到期(now+7天, 惰性判过期)',
  create_time  DATETIME      DEFAULT NULL              COMMENT '创建时间',
  update_time  DATETIME      DEFAULT NULL              COMMENT '更新时间',

  PRIMARY KEY (id),
  KEY idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷导出任务记录';

-- 验收(apply 后人工核对):
-- SHOW CREATE TABLE biz_export_record;
-- SELECT version, success FROM flyway_schema_history WHERE version='20';
