# Book-AI 代码健康度报告（全栈版）

**评测日期**: 2026-06-02
**评测范围**:
- 后端 `D:\workplace\book-ai\codeplace-A\book-server`（Java 17 + Spring Boot 3.5.14 多模块）
- 前端 `D:\workplace\book-ai\codeplace-A\book-ui`（Vue 3.5 + TS 6.0 + Vite 8 + UnoCSS + Element Plus）
**基线**: `code-health-2026-05-25.md`（仅后端，5.0/10）

---

## 总分

| 域 | 本次 | 上次 | Δ |
|---|---|---|---|
| **后端 book-server** | **4.6 / 10** | 5.0 / 10 | -0.4 |
| **前端 book-ui** | **6.4 / 10** | — | 首次 |
| **全栈加权（50/50）** | **5.5 / 10** | — | 首次 |

> 后端略降的原因：本次评分维度从 2 维细化到 5 维，权重纳入「工程化/CI」和「安全/依赖」，曝露出此前未量化的两个低分项；底层指标本身相对基线几乎无变化（+1 文件、LOC 不变、超长方法 23 处不变、测试仍 4 个、CI 仍为 0）。

---

## 一、后端 book-server

### 1.1 维度评分

| 维度 | 权重 | 得分 | 一句话 |
|------|------|------|--------|
| 代码结构与复杂度 | 20% | 7/10 | 模块清晰；少数 ServiceImpl >500 行 |
| 测试与质量 | 25% | 3/10 | 测试文件 4/722，`skipTests=true` 未改 |
| 工程化与 CI | 20% | 2/10 | 0 个 CI、0 个 lint、0 个 jacoco/sonar |
| 安全与依赖 | 25% | 5/10 | dev.yml 明文 OAuth secret、fastjson 1.x 仍锁 |
| 业务代码健康 | 10% | 8/10 | ruoyi-book/-admin 复杂度低、规范清 |

**加权**: 7×0.2 + 3×0.25 + 2×0.2 + 5×0.25 + 8×0.1 = **4.6 / 10**

### 1.2 关键指标

```
.java 文件总数      722         (基线 721, +1)
总 LOC              65,749      (基线 65,749, ±0)
>50 行方法数        23          (基线 23,  ±0)
测试文件数          4           (基线 4,   ±0)
skipTests           true        (基线 true)
TODO / FIXME        7 / 0       (基线 7 / 0)
e.printStackTrace   1           — 仅 ServletUtils.java
空 catch 块         0           — 已核实
权限注解覆盖率      28/63 ≈ 44% — 新增量化
```

**Top 5 文件（行数）**
1. `ruoyi-modules/ruoyi-workflow/.../FlwTaskServiceImpl.java` — 915
2. `ruoyi-modules/ruoyi-system/.../SysUserServiceImpl.java` — 777
3. **`ruoyi-modules/ruoyi-book-admin/.../AdminQuestionServiceImpl.java` — 748** ⭐ 自研
4. `ruoyi-modules/ruoyi-system/.../SysRoleServiceImpl.java` — 614
5. `ruoyi-common/ruoyi-common-redis/.../RedisUtils.java` — 581

**自研代码（ruoyi-book + ruoyi-book-admin）**: 103 文件 / 7,812 行 / 占总量 12% — 健康。

### 1.3 安全 / 合规热点（本次新增）

| 严重度 | 文件 | 问题 |
|---|---|---|
| 🔴 高 | `ruoyi-admin/src/main/resources/application-dev.yml:226-228` | **gitee OAuth `client-secret` 明文**：`02c6fcfd70342980cd8dd2f2c06c1a350645d76c754d7a264c4e125f9ba915ac` — 跟其它 `xxx*****` 占位符不同，看起来是真凭据被提交进 git |
| 🟠 中 | `application-dev.yml:20` | SnailJob `token: "SJ_cKqBTPzCsWA3VyuCfFoccmuIEGXjr5KT"` 也是真值；dev 期可接受，但应迁出代码库 |
| 🟠 中 | `application-dev.yml:207` | maxkey `client-secret: x1Y5MTMwNzIwMjMxNTM4NDc3Mzche8` 同理 |
| 🟡 低 | `pom.xml:46-47` | `fastjson 1.2.83` 仍被声明（虽然只是版本锁，业务无直接 import）— 1.x 系列有反序列化历史，建议升级到 fastjson2 或彻底排除 |
| 🟡 低 | `application-dev.yml:60` | MySQL `password: 123456` — dev 期可接受，但暴露弱密码模式 |
| 🟡 低 | Controller 权限 | 63 个 `@RestController` 中仅 28 个有 `@SaCheckPermission`，44% 覆盖率；新接口很容易漏带 |

---

## 二、前端 book-ui

### 2.1 维度评分

| 维度 | 权重 | 得分 | 一句话 |
|------|------|------|--------|
| 代码结构与复杂度 | 20% | 6/10 | `edit.vue` 1234 行、3 个文件 >500 行 |
| 类型安全 | 20% | 8/10 | strict ✓（继承 @vue/tsconfig）、`any` 仅 3 处 |
| 工程化 | 20% | 6/10 | ESLint + Prettier ✓；无 husky / CI / 测试 |
| 运行时质量 | 20% | 5/10 | 53 处 console、0 测试、4 处 TODO |
| 网络/路由/状态层 | 20% | 7/10 | 拦截器+envelope+401+Bearer 齐全，分层清晰 |

**加权**: (6 + 8 + 6 + 5 + 7) × 0.2 = **6.4 / 10**

### 2.2 关键指标

```
.vue 文件 / 行        32 / 9,518
.ts 文件 / 行         17 / 1,878
总 LOC              11,396
strict              true   ✓  (经核实，继承自 @vue/tsconfig)
any 使用次数         3      ✓  (2× `as any`, 1× `: any`)
@ts-ignore          0      ✓
console.*           53     ⚠️  (17 个文件)
TODO / FIXME        4 / 0
硬编码敏感信息       无     ✓
测试框架            无     ❌
ESLint / Prettier   有 / 有 ✓
Husky / lint-staged 无 / 无 ❌
CI                  无     ❌
```

**Top 5 文件（行数）**
1. `src/views/papers/edit.vue` — **1,234** 🔴
2. `src/views/question/detail.vue` — 970 ⚠️
3. `src/views/papers/source.vue` — 682 ⚠️
4. `src/components/business/PaperPreview/index.vue` — 554
5. `src/views/question/index.vue` — 553

3 个 view 都 >500 行，已经是单文件巨型组件，拆分性价比高。

### 2.3 工程化短板

- 提交侧无 husky/lint-staged → 本地配置好的 ESLint 在 commit 时没强制门禁
- `package.json scripts` 只有 dev/build/preview，没有 `lint` / `lint:fix` / `type-check` / `format` 显式命令
- 无单元/E2E 测试框架 — 业务复杂度上来后回归无保障

---

## 三、通病（共性问题）

1. **零自动化保障**：前后端均无 CI、均无 commit hooks、均无测试门禁。每次 push 都靠人工把关。
2. **巨型文件未拆分**：后端 `AdminQuestionServiceImpl` 748 行、前端 `edit.vue` 1234 行 — 单点变更风险大。
3. **凭据治理不规范**：dev 配置内直接写了几个看起来是真 secret 的值并提交进 git；前端 53 处 console 也算运行期"泄露"。
4. **依赖治理被动**：fastjson 1.x 锁了好几个月没动；前端 dependencies 列表中 `html2canvas`/`jspdf` 这类只在 PDF 导出用的重依赖也未做按需异步加载。

---

## 四、改进建议（按 ROI 排序）

### P0 — 立刻做（合规/安全红线）

1. **吊销并轮换 `application-dev.yml` 中的 gitee/maxkey/snailjob 凭据**，把这些值移到 `.env` 或本地 `application-dev-local.yml`（加 `.gitignore`）。git 历史里的旧值视作已泄漏处理。
2. **前端 53 处 console 一次性清理或包到 dev-only 工具函数**（如 `src/utils/logger.ts`，生产 build 时摇掉）。

### P1 — 本周做（最低门禁）

3. **改 `pom.xml` 顶层 `<skipTests>true</skipTests>` → `false`**，并补 3-5 个 `AdminQuestionServiceImpl.adminEdit` 的 happy-path JUnit。
4. **前端加 husky + lint-staged**：commit 自动跑 `eslint --fix` + `prettier --write`。
5. **前后端各加一个最小 GitHub Actions**：BE 跑 `mvn -B compile test`；FE 跑 `pnpm install && pnpm build`。

### P2 — 两周内（结构/可维护性）

6. **拆 `AdminQuestionServiceImpl.adminEdit`**：按已有 `// ===== Step N =====` 注释拆出 4 个私有方法，INSERT/UPDATE 字段赋值抽 `applyBoToEntity()` 或上 MapStruct-Plus。
7. **拆前端 `edit.vue` 1234 行**：按面板抽 3-4 个子组件（左侧题目列表 / 右侧详情 / 顶部工具栏 / 底部操作）。
8. **接入 `spotless-maven-plugin` + `jacoco-maven-plugin`**（先不卡阈值，只跑报告）。

### P3 — 后续

9. **升级 fastjson 1.x → fastjson2，或在 pom 显式 exclude 后用 Jackson 替代**。
10. **补 Controller 权限注解**：把没有 `@SaCheckPermission` 的 35 个 controller 方法逐一审计（业务公开 / 鉴权遗漏）。
11. **清理 `ruoyi-demo` 模块及未使用的 `ruoyi-common-sms` / `-social` / `-translation`**，减少编译时长与攻击面。
12. **前端引入 vitest**，先给 `src/http/request.ts` 拦截器和 `composables/*` 补单测。

---

## 五、对比基线（2026-05-25 → 2026-06-02）

| 指标 | 上次 | 这次 | 状态 |
|---|---|---|---|
| 后端 .java | 721 | 722 | +1 |
| 后端 LOC | 65,749 | 65,749 | ±0 |
| 后端 >50 行方法 | 23 | 23 | ±0 |
| 后端测试 | 4 | 4 | ±0 |
| 后端 CI | 无 | 无 | ±0 |
| 后端 lint | 无 | 无 | ±0 |
| 后端 TODO | 7 | 7 | ±0 |
| skipTests | true | true | ±0 |

**结论**：过去一周后端代码规模和质量指标几乎完全冻结。上份报告的所有建议均未推进。本次同步给前端首次评分，并把全栈视角合并。

---

*报告生成: 2026-06-02 · 评测方式: 手动深度扫描（Explore agent + 关键点人工核验）*
