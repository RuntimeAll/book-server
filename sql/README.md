# book-server / sql

加新不改老；详见 `.claude/skills/sql-migration/SKILL.md`

> 🔴 **迁移的真实执行位置 = `ruoyi-admin/src/main/resources/db/migration/`**（flyway `locations`，BE 启动 `validate-on-migrate` 自动 apply），**不是本 `sql/` 目录**。本目录现仅放 RuoYi 系统初始化 SQL + `dev-fix/` 一次性对账脚本。新增业务迁移请直接放 `db/migration/`。
>
> 🔴 **三线 flyway 协作的权威约定 = `book-ai/CLAUDE.md` §3.1**（线专属预留段、号定不改；prod 各线库独立 + 改动不频繁 → 不上 renumber 重纪律）。
>
> 🔴 **C 线（master-ai）迁移用预留段 `V901+`**（B 线=`V801+`，A 线=主线顺序号）。C 线 teacher-copilot/AI 编排迁移**一律从 V901 起顺序加**（现 V901=label 维度列、V902=biz_label_job、V903=biz_billing_event）。预留段防的是 dev 共用库平行加迁移撞号；纯增量（ADD COLUMN/CREATE TABLE，无 DROP）。**号一旦定就不改，合并不 renumber**——flyway 只要号唯一/单调即可，空洞无所谓。
>
> `dev-fix/`：dev 库一次性对账脚本（非迁移，不入 db/migration）。`2026-06-09-flyway-rebaseline.sql`=V1-16 baseline 恢复；`2026-06-10-flyway-c-line-v901.sql`=本 dev 已手工建过 V901-903 对象 → 补登 history 行（全新 dev/prod 不需要，flyway 会 live apply）。

## 文件清单

| 文件 | 说明 |
|---|---|
| `ry_vue_5.X.sql` | RuoYi-Vue-Plus 5.x 系统初始化 SQL（sys_* 表 + 初始数据），首次部署跑一次 |
| `ry_job.sql` | SnailJob 定时任务初始化 SQL，按需跑 |
| `V{n}__<desc>.sql` | 业务 schema 变更（V 文件，加新不改老）|

## 执行顺序（初次部署）

```bash
# 1. 创建数据库
mysql -h 127.0.0.1 -P 3307 -u root -p123456 -e "CREATE DATABASE IF NOT EXISTS misikt_data DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"

# 2. 跑 RuoYi 系统初始化
mysql -h 127.0.0.1 -P 3307 -u root -p123456 misikt_data < sql/ry_vue_5.X.sql

# 3. 按 V 编号顺序跑业务 SQL（目前暂无）
```
