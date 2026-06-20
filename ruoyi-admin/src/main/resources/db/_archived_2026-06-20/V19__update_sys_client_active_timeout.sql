-- ============================================================================
-- V19: 登录持久化 — 关掉 Sa-Token 活跃超时（active_timeout 1800s → -1）
-- ----------------------------------------------------------------------------
-- 根因（2026-06-11 用户报"经常掉线"排查）：sys_client.active_timeout = 1800（RuoYi 模板默认），
-- Sa-Token 冻结机制 = 30 分钟无任何请求 token 即失活——哪怕总 timeout 还有 7 天。
-- 老师备课中途离开半小时回来必被踢回登录页，体感="经常掉线"。
--
-- 修法：active_timeout 改 -1（Sa-Token 约定 = 不做活跃检查），总有效期仍由 timeout=604800（7 天）兜底。
-- 影响面：pc + app 两个 client 全改；已发 token 不受影响（active_timeout 在登录时写进 token 模型），
--          新登录生效。无 schema 改动，纯配置数据 UPDATE，可重复执行（幂等）。
-- ============================================================================

UPDATE sys_client SET active_timeout = -1 WHERE active_timeout = 1800;

-- 验收：SELECT client_key, timeout, active_timeout FROM sys_client; → active_timeout 全 -1
