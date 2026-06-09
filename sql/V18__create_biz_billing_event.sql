-- ============================================================================
-- V18: PRD-C(teacher-copilot 探针) — biz_billing_event 计费埋点表(骨架版)
-- ----------------------------------------------------------------------------
-- 来源: codeplace-C/teacher-copilot-ready/01-DDL.sql §3
-- 目的: 每次 LLM 调用 / 服务计量埋点。
-- 状态: 🟡 留档(第2周 persist 节点每次 LLM 调用写一条)。手动跑, 非自动迁移。
--
-- 🔴 第一期【只埋点, 不扣费】, 但字段必须从第一天就有 —— 否则上线后想做计费, 历史数据全丢。
-- ⚠️ 待决(见 teacher-copilot/ROADMAP.md): 埋点【写入路径】走 RuoYi HTTP 还是 Python 直写 3307?
--    (本表是探针新表、纯埋点, 不含权限语义, 倾向允许直写以简化; 与"业务数据写走 RuoYi"铁律不冲突。)
--
-- 🔴 跑此文件用 utf8mb4(中文 COMMENT)。
-- ============================================================================

CREATE TABLE biz_billing_event (
    event_id         VARCHAR(36)   PRIMARY KEY,
    request_id       VARCHAR(36)   NULL                         COMMENT '一次请求全链路 ID',
    teacher_id       BIGINT        NULL                         COMMENT '老师 ID(打标任务可空)',

    -- 来源
    source_type      VARCHAR(32)   COMMENT 'label_job/admin_preview/老师对话(后期)',
    source_id        VARCHAR(64)   NULL                         COMMENT 'job_id 或 thread_id 等',

    -- LLM 计量
    provider         VARCHAR(64)   NULL                         COMMENT '实际调的 provider',
    model            VARCHAR(64)   NULL,
    prompt_tokens    INT           DEFAULT 0,
    completion_tokens INT          DEFAULT 0,
    fallback_count   INT           DEFAULT 0                    COMMENT '此次切换了几次 provider',

    -- 服务分级(为后期 VIP/水印铺垫, 第一期都填 base)
    service_tier     VARCHAR(16)   DEFAULT 'base'               COMMENT 'base/premium',
    is_billable      BOOLEAN       DEFAULT FALSE                COMMENT '是否计入账单',

    -- 时间/状态
    started_at       DATETIME(3),
    ended_at         DATETIME(3),
    duration_ms      INT,
    status           VARCHAR(16)   NULL                         COMMENT 'ok/error',
    error_type       VARCHAR(64)   NULL,

    INDEX idx_teacher_time (teacher_id, started_at),
    INDEX idx_source       (source_type, source_id),
    INDEX idx_provider     (provider, started_at)
) COMMENT 'LLM 调用 / 服务计费埋点(第一期只埋点不扣费)';
