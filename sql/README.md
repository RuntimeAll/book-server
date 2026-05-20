# book-server / sql

加新不改老；部署 = 按 V 编号顺序 `mysql < ...` 执行；详见 `.claude/skills/sql-migration/SKILL.md`

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
