# Book-Server 代码健康度报告

**评测日期**: 2026-05-25
**项目路径**: `D:\workplace\book-ai\codeSpace\book-server`
**技术栈**: Java 17 + Spring Boot 3.5.14 + MyBatis-Plus 3.5.16 + Sa-Token 1.45 + Redisson 3.52 + warm-flow 1.8.5 + MapStruct-Plus 1.5 + Lombok 1.18.44；Maven 多模块；基于 RuoYi-Vue-Plus 5.6.1 二次开发。

## 总分: 5.0 / 10

| 维度 | 得分 | 说明 |
|------|------|------|
| 代码结构与复杂度 | 7/10 | 模块化划分清晰，但少数核心服务类肥胖、长方法 23 个 |
| 测试与质量 | 3/10 | 仅 4 个测试文件、无 Lint、无 CI、`skipTests=true` 默认跳过 |

> 加权方式：两维度各占 50%（(7+3)/2 = 5.0）。

---

## 一、代码结构与复杂度

### 项目结构概览

项目沿用 RuoYi-Vue-Plus 多模块 Maven 布局，整体分层清晰：

- `ruoyi-admin/`：可执行入口，22 个 Java 文件，1,981 行
- `ruoyi-common/`：21 个 common-* 子模块（core / redis / mybatis / excel / oss / mail / satoken / web / websocket / sse / log / sensitive / encrypt / job / tenant / translation / sms / social / idempotent / doc / ratelimiter），共 291 文件、24,046 行
- `ruoyi-extend/`：2 个外部服务整合（monitor-admin / snailjob-server），仅 6 文件、242 行
- `ruoyi-modules/`：业务模块共 402 文件、39,480 行：
  - `ruoyi-system` (151 文件) — RuoYi 自带系统域
  - `ruoyi-book` (90 文件) — **本项目业务核心：题库/卷库 C 端**
  - `ruoyi-workflow` (79 文件) — warm-flow 工作流集成
  - `ruoyi-demo` (44 文件) — 示例代码
  - `ruoyi-generator` (13 文件) — 代码生成器
  - `ruoyi-book-admin` (12 文件) — **本项目业务核心：题库管理 B 端**
  - `ruoyi-job` (11 文件) — 定时任务
  - `ruoyi-admin-common` (2 文件) — admin 上传公共能力

**统计**：合计 721 个 `.java` 文件、65,749 行源码。目录命名一致（dromara 包名 + book/bookadmin 业务前缀），分层符合 controller → service → mapper → domain（bo/vo/entity）规范。

**观察**：

- `ruoyi-demo` 模块尚未清理，作为业务工程长期保留会拖低读入门槛与编译时长，建议在 V0.x 收尾时移除或下沉到独立工程。
- 真正项目特有的业务代码集中在 `ruoyi-book` (90) + `ruoyi-book-admin` (12) = 102 个文件，仅占总量 14%，其余为 RuoYi 脚手架；当前阶段（README 标注 "🟢 基建中 V0.0"）合理，但后续随业务增长需关注框架代码与业务代码的边界维护。
- `ruoyi-common` 子模块达 21 个，部分（如 `ruoyi-common-sms`、`ruoyi-common-social`、`ruoyi-common-translation`）当前业务用不到，可在合规前提下裁掉以缩小攻击面。

### 最大文件 Top 10

| 排名 | 文件 | 行数 |
|------|------|------|
| 1 | `ruoyi-modules/ruoyi-workflow/.../service/impl/FlwTaskServiceImpl.java` | 915 |
| 2 | `ruoyi-modules/ruoyi-system/.../service/impl/SysUserServiceImpl.java` | 777 |
| 3 | `ruoyi-modules/ruoyi-book-admin/.../service/impl/AdminQuestionServiceImpl.java` | 748 |
| 4 | `ruoyi-modules/ruoyi-system/.../service/impl/SysRoleServiceImpl.java` | 614 |
| 5 | `ruoyi-common/ruoyi-common-redis/.../utils/RedisUtils.java` | 581 |
| 6 | `ruoyi-modules/ruoyi-generator/.../service/GenTableServiceImpl.java` | 578 |
| 7 | `ruoyi-modules/ruoyi-system/.../service/impl/SysTenantServiceImpl.java` | 567 |
| 8 | `ruoyi-common/ruoyi-common-oss/.../core/OssClient.java` | 564 |
| 9 | `ruoyi-modules/ruoyi-workflow/.../service/impl/FlwInstanceServiceImpl.java` | 485 |
| 10 | `ruoyi-common/ruoyi-common-excel/.../utils/ExcelUtil.java` | 479 |

90% 的大文件都是 Service 实现或工具类。**值得关注的本项目自研代码：`AdminQuestionServiceImpl` 已经膨胀到 748 行**，建议关注。

### 超长函数 Top 10（>50 行）

全项目共 **23 个方法超过 50 行**，最长 10 个如下：

| 排名 | 行数 | 文件:行号 | 方法 |
|------|------|-----------|------|
| 1 | 117 | `ruoyi-common-excel/.../ExcelDownHandler.java:180` | `dropDownLinkedOptions()` |
| 2 | 101 | `ruoyi-system/.../SysTenantServiceImpl.java:409` | `syncTenantDict()` |
| 3 | 101 | `ruoyi-system/.../SysTenantServiceImpl.java:120` | `insertByBo()` |
| 4 | 93 | `ruoyi-common-doc/.../OpenApiHandler.java:156` | `buildTags()` |
| 5 | 84 | `ruoyi-workflow/.../FlwTaskServiceImpl.java:755` | `taskOperation()` |
| 6 | 82 | `ruoyi-common-mybatis/.../PlusDataPermissionHandler.java:100` | `buildDataFilter()` |
| 7 | **77** | **`ruoyi-book-admin/.../AdminQuestionServiceImpl.java:287`** | **`adminEdit()`** |
| 8 | 75 | `ruoyi-workflow/.../WorkflowGlobalListener.java:174` | `finish()` |
| 9 | 73 | `ruoyi-demo/.../ExportExcelServiceImpl.java:35` | `exportWithOptions()` |
| 10 | 73 | `ruoyi-common-excel/.../ExcelDownHandler.java:87` | `afterSheetCreate()` |

绝大多数长方法位于 RuoYi 框架自带模块，本项目业务代码中 **`AdminQuestionServiceImpl.adminEdit`（77 行）** 是唯一一处自研超长方法，已用 `// ===== Step N =====` 注释做了分段，可读性尚可，但仍建议按 Step 拆出私有方法。

### 嵌套过深位置（>5 层）

按粗略括号深度统计，共 31 处嵌套 >5 层，最深 8 层。代表性位置：

- `ruoyi-common/ruoyi-common-oss/.../OssClient.java:307` — depth=8（try-嵌套-try）
- `ruoyi-common/ruoyi-common-core/.../ThreadPoolConfig.java:72` — depth=7
- `ruoyi-common/ruoyi-common-log/.../LogAspect.java:181` — depth=7
- `ruoyi-modules/ruoyi-system/.../SysTenantServiceImpl.java:480` — depth=7
- `ruoyi-modules/ruoyi-workflow/.../FlwNodeExtServiceImpl.java:307` — depth=7

**本项目业务代码（`ruoyi-book` / `ruoyi-book-admin`）目前没有 depth>5 的位置**，复杂度可控。

### 代码重复观察

- `//TODO 做一些数据校验,如唯一约束` 这条注释出现在 **3 个 ServiceImpl** 里（`TestDemoServiceImpl:99`、`TestTreeServiceImpl:80`、`SysSocialServiceImpl:88`），表明 RuoYi 脚手架/代码生成器输出了相同的 boilerplate，可抽取为基类校验模板或显式接口。
- 49 个 `ServiceImpl`、63 个 `Controller`、46 个 `Mapper` 命名一致，无明显跨模块复制粘贴；MyBatis-Plus 已经吸收了 CRUD 重复，自研代码可读性 OK。
- `AdminQuestionServiceImpl.adminEdit` 的 INSERT 与 UPDATE 分支重复了大量字段赋值（约 25 行 `entity.setXxx(bo.getXxx())`），可抽 `applyBoToEntity(bo, entity)` 私有方法或用 MapStruct-Plus 转换。

### 圈复杂度（人工估计）

未集成自动工具（无 PMD / SonarQube / SpotBugs）。基于人工抽样：

- 类平均圈复杂度：低-中（多数 ServiceImpl 在 5-10）
- 高复杂度热点：`FlwTaskServiceImpl.taskOperation`（多分支 switch / if 链）、`AdminQuestionServiceImpl.adminEdit`（多分支 + 多步事务）、`OssClient`（嵌套 try-catch）
- 整体复杂度可控，但缺少持续监控手段，长期趋势不可见。

### 本维度得分: 7/10

**加分**：分层清晰、命名一致、MyBatis-Plus 减少了 CRUD 重复、本项目业务代码（`ruoyi-book` / `ruoyi-book-admin`）复杂度低。
**减分**：少数 ServiceImpl 文件偏大（>500 行）、23 处超长方法、OssClient 嵌套 8 层、demo 模块尚未清理、缺少自动复杂度监控。

---

## 二、测试与质量

### 测试情况

| 指标 | 数值 |
|------|------|
| 测试目录 | 仅 `ruoyi-admin/src/test/java/org/dromara/test/`（1 个） |
| 测试文件数 | **4** |
| 源文件数（不含 test） | 717 |
| 测试比例 | **0.6%** |
| 估算测试覆盖率 | **接近 0%**（仅有 `AssertUnitTest`、`DemoUnitTest`、`ParamUnitTest`、`TagUnitTest` 四个示例性测试，未覆盖 `ruoyi-book` / `ruoyi-book-admin` 任何业务方法） |
| Maven Surefire 默认行为 | `<skipTests>true</skipTests>` — **打包时默认跳过测试** |

**严重缺口**：项目核心业务模块 `ruoyi-book`（90 文件）、`ruoyi-book-admin`（12 文件）**零测试覆盖**；workflow / system / generator 等所有 modules 同样无测试。

### Lint 与代码风格

**未配置任何静态检查工具**：

- `.editorconfig` — ❌ 不存在
- Checkstyle / PMD / SpotBugs / SonarLint — ❌ 不存在
- Spotless（自动格式化） — ❌ 不存在
- ErrorProne — ❌ 不存在
- 仅有 IDE 级别的 `.idea/codeStyleSettings.xml` — ❌ 也未发现

**风格一致性观察**（抽样）：

- 包命名一致（`org.dromara.<module>.<layer>`）
- Lombok 注解使用一致（`@Service`、`@RequiredArgsConstructor`、`@Slf4j`）
- 业务代码注释中文为主、规范完整（如 `AdminQuestionServiceImpl.adminEdit` 用 `// ===== Step N =====` 分步注释）
- 但**无自动门禁**，新人加入后风格漂移风险高

### CI 配置

**完全没有 CI 配置**：

- `.github/workflows/` — ❌ 不存在
- `.gitlab-ci.yml` — ❌ 不存在
- `Jenkinsfile` / `.drone.yml` / `.circleci/` — ❌ 均不存在

意味着每次提交都依赖人工本地构建验证，缺少：编译保障、测试自动回归、依赖漏洞扫描、代码风格门禁、覆盖率门禁。

### TODO/FIXME 统计

| 标记 | 数量（不含 target/） |
|------|---|
| TODO | **7** |
| FIXME | **0** |
| XXX（代码标记） | **0**（搜到的 3 处 `XXX` 实际为业务字符串 `"节点 XXXX"` 占位符，非代码 marker） |

**TODO 主要分布**：

- `ruoyi-modules/ruoyi-demo/.../TestDemoServiceImpl.java:99`、`TestTreeServiceImpl.java:80/86` — demo 代码遗留模板注释（建议随 demo 模块一并清理）
- `ruoyi-modules/ruoyi-system/.../SysSocialServiceImpl.java:88` — 模板注释
- `ruoyi-modules/ruoyi-workflow/.../FlwSpelServiceImpl.java:150` — 模板注释
- `ruoyi-modules/ruoyi-workflow/.../FlwTaskServiceImpl.java:179` — `// TODO: 按照自己业务规则生成编号`（**框架预留点，业务接手时需处理**）
- `ruoyi-modules/ruoyi-workflow/.../FlwCommonServiceImpl.java:110` — `log.info("【短信发送 - TODO】...")`（**真实业务漏点 — 短信下发未接入，仅打日志**）

业务代码 `ruoyi-book` / `ruoyi-book-admin` 中没有 TODO。

### 本维度得分: 3/10

**加分**：业务代码注释清晰、Lombok 用法规范、Git 提交信息有结构化前缀（`[R 卡 BE]`、`[H-card-refactor]` 等）便于追溯。
**减分**：
1. 测试比例 0.6%，业务模块零覆盖
2. 打包默认 `skipTests=true`
3. 完全无 Lint / 静态检查 / CI
4. `FlwCommonServiceImpl:110` 短信发送 TODO 为运行时漏点

---

## 三、改进建议（按优先级）

1. **【高】补齐核心业务测试** — 涉及文件：`ruoyi-modules/ruoyi-book/`、`ruoyi-modules/ruoyi-book-admin/`。优先为 `AdminQuestionServiceImpl.adminEdit`、`PaperLibraryController` / `QuestionController` 端到端流程编写 JUnit + MockMvc + Testcontainers 测试，目标 2-4 周内覆盖率从 0% 提到 30%。同时把 `pom.xml` 顶层 `<skipTests>true</skipTests>` 默认值改成 `false`，或新增 `verify` profile 强制跑测试。

2. **【高】接入最小可用 CI** — 涉及文件：新建 `.github/workflows/build.yml`（如使用 GitHub）或 `.gitlab-ci.yml`。CI 至少包含三步：`mvn -B compile`、`mvn -B test`、依赖扫描（OWASP Dependency-Check 或 GitHub Dependabot）。当前提交完全靠人工本地构建，回归风险大。

3. **【中】引入 Spotless + Checkstyle 静态检查门禁** — 涉及文件：`pom.xml`（添加 `spotless-maven-plugin` + `maven-checkstyle-plugin`）、新建 `checkstyle.xml` 与 `.editorconfig`。配套在 CI 上设为 fail-on-violation，可阻止风格漂移与常见反模式。先用最低门槛上线，后续逐步收紧。

4. **【中】重构肥胖 Service 与超长方法** — 优先重构以下两处：
   - `ruoyi-modules/ruoyi-book-admin/.../AdminQuestionServiceImpl.java`（748 行，`adminEdit` 77 行）— 把 `adminEdit` 按 Step 0/1/2/3 拆成 4 个私有方法；INSERT/UPDATE 分支的字段赋值抽出 `applyBoToEntity()` 或改用 MapStruct-Plus converter。
   - `ruoyi-common/ruoyi-common-oss/.../OssClient.java:307`（嵌套 8 层）— 用早返回 / 提取私有方法把 try-catch 嵌套打平。

5. **【中】清理 demo 模块与已废弃 TODO** — 涉及文件：`ruoyi-modules/ruoyi-demo/`（44 文件）、`ruoyi-common/ruoyi-common-{sms,social,translation,sse}` 等未使用 common 模块。当前业务路径不依赖这些，可减少 ~10K LOC 编译时长，降低维护噪声。同时处理 `ruoyi-modules/ruoyi-workflow/.../FlwCommonServiceImpl.java:110` 的 "短信发送 - TODO"（要么接入短信通道，要么显式 fallback）。

6. **【低】启用代码覆盖率工具** — 涉及文件：`pom.xml` 添加 `jacoco-maven-plugin`，并配置 `report` goal。即使初期覆盖率低，把数字摆出来也有助于趋势监控；CI 中后续可加 ≥30%/≥50% 的覆盖率门禁。

---

## 四、本周变化（对比上次报告）

首次评测，无对比数据。后续每周运行同名计划任务时，将自动在此小节生成与上次报告的差异（指标增量、得分变化、热点变动）。

---

*报告由 Cowork 调度任务 `book-server-code-health-weekly` 自动生成 · 评测人 Susan Yusuf · 自动化运行模式*
