# book-server / sql

加新不改老；详见 `.claude/skills/sql-migration/SKILL.md`

> 🔴 **迁移的真实执行位置 = `ruoyi-admin/src/main/resources/db/migration/`**（flyway `locations`，BE 启动 `validate-on-migrate` 自动 apply），**不是本 `sql/` 目录**。本目录现仅放 RuoYi 系统初始化 SQL + `dev-fix/` 一次性对账脚本。新增业务迁移请直接放 `db/migration/`。
>
> 🔴 **三线 flyway 协作的权威约定 = `book-ai/CLAUDE.md` §3.1**（A/B/C 共用一个库一条号序；master 合并定号、以上线为准）。本 README 只补 C 线**怎么编号 + 合回 master 怎么 renumber** 的操作面。
>
> 🔴 **C 线（master-ai）开发期用草稿段 `V901+`**（B 线=`V801+`，A 线=master 正式顺序号）。C 线 teacher-copilot/AI 编排迁移**一律从 V901 起编**（现 V901=label 维度列、V902=biz_label_job、V903=biz_billing_event）。草稿段只防开发期撞号（dev 库也三线共用）；纯增量（ADD COLUMN/CREATE TABLE，无 DROP）。
>
> `dev-fix/`：dev 库一次性对账脚本（非迁移，不入 db/migration）。`2026-06-09-flyway-rebaseline.sql`=V1-16 baseline 恢复；`2026-06-10-flyway-c-line-v901.sql`=本 dev 已手工建过 V901-903 对象 → 补登 history 行（全新 dev/prod 不需要，flyway 会 live apply）。

## 🔴 合回 master 时：草稿号 → 正式号 renumber（checklist）

C 线（或 B 线）功能迁移**合回 master 时**必须把草稿号 renumber 成 master 当前 `max+1`（按合并落地顺序），让 prod 号序 = 上线序。逐个迁移做：

1. **定新号** = master `db/migration` 当前最大 V 号 + 1（多个迁移按本线内原顺序连续排）。
2. **改文件名**：`git mv db/migration/V901__alter_xxx.sql db/migration/V19__alter_xxx.sql`（内容不动，仅改号；文件头注释里的 V 号一并改）。
3. **改 dev 共用库 history**（dev 已用草稿号跑过/登记过 → 不改则 validate 报 description/version mismatch）：
   ```sql
   UPDATE flyway_schema_history
     SET version='19', script='V19__alter_xxx.sql', description='alter xxx'   -- description = 文件名去 V19__、去 .sql、_→空格
   WHERE version='901';
   -- installed_rank 不用动（它只管历史顺序，与 version 解耦）；checksum 仍 NULL（草稿是手工/NULL 登记的）
   ```
4. **验证**：重启该线 BE，boot 日志应见 `Successfully validated N migrations` + `No migration necessary`（没重跑、没 mismatch）。
5. prod 无需任何对账：prod 没跑过草稿号，flyway 见正式号 > baseline 直接 live apply。

> ⚠️ 若合并当天 A 线也在 master 加了新迁移，以**实际合并落地后** master 的 max 为准取号（后合的接着排）。renumber 是 vibe 下 Claude 在「合回 master」动作里执行的固定步骤。

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
