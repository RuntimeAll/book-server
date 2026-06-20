-- ============================================================================
-- V902: PRD-C(teacher-copilot 探针) — biz_label_job 打标任务表
-- ----------------------------------------------------------------------------
-- 来源: codeplace-C/teacher-copilot-ready/01-DDL.sql §2
-- 目的: 一个批次的打标任务(管理员触发, 可查进度)的元数据。
-- 🔴 2026-06-10 转正为 flyway 自动迁移(原 sql/V17 → C 线预留段 V902)。纯新建表(CREATE TABLE, 无 DROP),
--    探针未来用、现 prod 空表零风险; dev 已手工建过 → history NULL checksum 记 success=1 跳过重跑。
--
-- 🔴 分工(两层别混):
--   • 本表(MySQL) = 任务"业务事实": 批次/总数/进度/状态/配置。
--   • LangGraph agent 的【运行时 state + 中断续跑快照】走 Redis checkpoint(redis db1), 不进此表。
--     —— MySQL 管业务事实, Redis 管 agent 运行时。
--
-- 🔴 跑此文件用 utf8mb4(中文 COMMENT)。
-- ============================================================================

CREATE TABLE biz_label_job (
    id              VARCHAR(36)  PRIMARY KEY                  COMMENT 'job_id (uuid)',
    batch_name      VARCHAR(128) NULL,
    target_filter   JSON         NULL                         COMMENT '打标范围 SQL where 条件',
    total           INT          DEFAULT 0,
    done            INT          DEFAULT 0,
    failed          INT          DEFAULT 0,
    status          VARCHAR(16)  NULL                         COMMENT 'pending/running/done/error/aborted',
    config          JSON         NULL                         COMMENT 'prompt 版本/模型/参数',
    started_at      DATETIME     NULL,
    ended_at        DATETIME     NULL,
    error_summary   TEXT         NULL,
    -- RuoYi 标配字段
    create_by       VARCHAR(64),
    create_time     DATETIME,
    update_by       VARCHAR(64),
    update_time     DATETIME,
    remark          VARCHAR(500),
    INDEX idx_status_time (status, started_at)
) COMMENT '题库打标任务';
