# 第一阶段（登录注册模块 + 首页模块）开发计划

> **面向 AI 执行者：** 本计划的每一步均使用复选框（`- [ ]`）语法，请按顺序逐任务（Task）执行。每个 Task 结束都必须通过其验证步骤，并完成一次 git 提交后再进入下一个 Task。

**目标（Goal）：** 交付可运行的「登录注册模块」与「首页模块」垂直切片——普通用户可注册、用户与管理员可共用同一登录页登录、按既定角色进入用户首页或管理后台入口；用户首页展示固定项目介绍、明确的资讯空状态和体验／历史入口；所有接口严格实现《接口文档 V1.0》与 `openapi.yaml` 中 API-01～API-07 的契约。

**架构（Architecture）：** Spring Boot 模块化单体后端（按业务能力分包：`auth`、`home`、`common`）＋ Vue 3 SPA 前端，单一 MySQL 8.0.45 存储，Flyway 管理版本化迁移。身份采用服务端 Cookie Session（`JSESSIONID`，Path=`/anti.scam/v1`、HttpOnly、SameSite=Lax）＋ `X-CSRF-TOKEN` 双提交校验，不使用 JWT。所有普通响应统一 `code/message/data/requestId` 封套；角色与归属一律由服务端会话判定，前端不做权限结论。

**技术栈（Tech Stack）：** Java 21（LTS）＋ Spring Boot 3.x（含 spring-boot-starter-web / data-jpa / security / validation / flyway-mysql）＋ MySQL 8.0.45 ＋ Flyway；前端 Vue 3 ＋ Vite ＋ TypeScript ＋ Vue Router ＋ Pinia ＋ 原生 `fetch` 封装；后端测试 JUnit 5 ＋ Spring Boot Test（MockMvc）＋ Testcontainers(MySQL 8.0.45) ＋ spring-security-test；前端测试 Vitest ＋ Vue Test Utils ＋ MSW；契约校验 Node 脚本（`@apidevtools/swagger-parser`）。

**Spec（本计划实现的规格文档，执行者必须同时阅读）：**
- `AI反诈情景推演平台_PRD_V3.1_正式开发基线版.md`（业务基线，特别是 §8.1、§8.2、§4、§7、§12.2、§13.1 AC-01/02/08、§13.2 AP-08）
- `AI反诈情景推演平台_系统架构设计文档_V1.0.md`（§3.1、§5.1、§13.1、§15.5）
- `AI反诈情景推演平台 数据库设计文档 V1.0.md`（§2、§5.1、§6.1、§11.1）
- `AI反诈情景推演平台_接口文档_V1.0.md`（§2、§6 的 API-01～API-07、§7 字段字典、§8 错误码）
- `openapi.yaml`（机器可读契约，**接口字段与状态码的最终依据**）

---

## 一、本阶段范围与模块边界

### 1.1 本阶段实现的接口（唯一交付范围）

| 编号 | 方法 | 路径 | 用途 | 权限 |
|---|---|---|---|---|
| API-01 | GET | `/anti.scam/v1/auth/csrf` | 获取 CSRF 令牌 | PUBLIC |
| API-02 | POST | `/anti.scam/v1/auth/register` | 普通用户注册 | PUBLIC |
| API-03 | POST | `/anti.scam/v1/auth/login` | 共用登录 | PUBLIC |
| API-04 | POST | `/anti.scam/v1/auth/logout` | 登出 | USER\|ADMIN |
| API-05 | GET | `/anti.scam/v1/auth/me` | 当前账号 | USER\|ADMIN |
| API-06 | GET | `/anti.scam/v1/home` | 用户首页 | USER |
| API-07 | GET | `/anti.scam/v1/news` | 资讯空结构 | USER |

### 1.2 本阶段明确不做（禁止提前实现、禁止预留占位接口）

- API-08 及之后全部接口：场景／画像读取、头像读取、开始演绎、演绎状态、SSE、三类呈现回执、退出、临时语音、报告与重试、历史列表与详情、全部 `/admin/**`、`/timing/samples`。
- 数据库表只建 `sys_user`。`fraud_scenario`、`scammer_profile`、`victim_profile`、`simulation_session`、`simulation_round`、`simulation_message`、`simulation_report`、`agent_prompt` 一律**不在本阶段创建**，也不允许先建空表占位。
- 前端 `/configuration`、`/history`、`/admin` 仅提供「本阶段未实现」说明页，不含任何表单、列表或请求逻辑。
- 不实现短信／邮箱验证、密码找回、角色切换、用户管理、真实资讯加载（PRD §5.3 明确排除）。
- 不引入 JWT、Redis、微服务、消息中间件、SSE（本阶段无推送需求）。

---

## 二、全局约束（Global Constraints，所有任务默认包含）

以下数值来自规格文档，**必须逐字实现，不得「优化」**：

1. 接口前缀固定 `/anti.scam/v1`；JSON 字段 `lowerCamelCase`；成功 `code="OK"`，失败 `code` 为稳定英文码。
2. 统一响应封套四个顶层字段始终存在：`code`、`message`、`data`、`requestId`；`requestId` 为服务端生成 UUID。
3. 业务 ID 传输为**正整数十进制字符串**（`pattern ^[1-9][0-9]{0,19}$`，如 `"101"`），不是 JSON number。
4. 时间传输为 UTC ISO 8601 **含三位毫秒和 Z**（如 `2026-10-15T02:00:00.000Z`）。
5. 用户名校验顺序：**先去掉首尾空格**，再按 **Unicode 码点**计数 `2—24`；允许字符仅为**中文汉字、ASCII 英文字母、数字、下划线**（`\u4E00-\u9FFF` 与 `A-Za-z0-9_`）；**英文字母全局大小写不敏感**；唯一性在**普通用户与管理员之间全局成立**。
6. 密码：按 **Unicode 码点**计数 `6—24`；**不 trim、不做 Unicode 规范化、不做大小写转换**；允许空格与常见弱密码（`123456` 必须可注册可登录）；**不得因哈希实现而截断或改写**；`confirmPassword` 必须与 `password` **原样完全一致**。
7. 注册只创建角色 `USER`；请求中出现 `role`、`userId`、`outcome`、`riskScore` 等越权字段一律 **400**，不得忽略后采信。注册成功 **不自动登录**，返回登录页。
8. 登录失败统一 **401 `INVALID_CREDENTIALS`**，`message` 为「账号或密码错误」，**不得**暴露账号是否存在（不得返回字段级错误指向 username）。
9. 未登录 **401 `UNAUTHENTICATED`**；已确认角色但无权 **403 `FORBIDDEN`**；资源不存在或不属于本人 **404 `NOT_FOUND`**。
10. ADMIN 访问 `/home`、`/news`、演绎、历史、报告、音频等用户接口一律 **403**（先按角色拒绝，再谈资源）；USER 访问 `/admin/**` 一律 **403**。隐藏导航不构成权限控制，服务端必须实际拦截。
11. 写操作（register / login / logout）必须同时具备有效 Session Cookie 与请求头 `X-CSRF-TOKEN`；CSRF 失败 **403 `CSRF_INVALID`**。
12. 认证与私有 JSON 响应用 `Cache-Control: no-store`；不返回 HTML 重定向（受保护接口未登录时返回 JSON 401，不 302 到登录页）。
13. 受保护资源响应体、日志、错误 `message` 中**不得**出现密码、密码哈希、会话值、CSRF 令牌、堆栈、SQL 或供应商原文。
14. `JSESSIONID` Cookie 属性：`Path=/anti.scam/v1`、`HttpOnly`、`SameSite=Lax`、不设置 `Domain`；生产置于 HTTPS 时 `Secure=true`（本地 HTTP 开发由 `dev` profile 关闭，见 Task 3）。
15. 数据库基线：MySQL **8.0.45**、InnoDB、`utf8mb4`、默认排序规则 `utf8mb4_0900_bin`、时间列 `DATETIME(3)` 且统一写 UTC、枚举用 `VARCHAR + CHECK`、无软删除字段、无级联删除、应用业务事务显式使用 `READ COMMITTED`。
16. 迁移必须版本化且**提交后不可原地修改已执行版本**；建表 SQL 只通过 Flyway 迁移文件产生，不使用 `ddl-auto`。
17. 密码哈希使用 Spring Security 带盐自适应方案（`BCryptPasswordEncoder`），只存编码后字符串（`VARCHAR(255)`），不存明文、不存可逆密文、不存确认密码。
18. 版本策略：依赖版本在实施时锁定并在 `README.md` 记录；不在代码中散落未记录的版本假设。

---

## 三、复审重点（Review Focus）

以下五类输入或失败模式，规格文档暗示了但没有任何任务的测试会自然覆盖；它们是「最可能伤害真实使用者」的位置。**每条已在下方括号注明其测试落在哪个任务的步骤中**，实现这些任务时必须写出对应测试，不得省略：

1. **用户名唯一性的边界输入**——仅英文大小写不同的重复名（`ZhangSan` / `zhangsan`）、带首尾空格的名字（`" 李雷 "` 应先 trim 再校验并成功）、恰好 24 个汉字的名字、包含 `%`、全角字母 `Ａ`、emoji、或中间夹空格的用户名。合理预期：前三种成功，后四种按字段错误拒绝并给出可读中文说明；理想情况下绝不出现 500。（Task 4 步骤 1/3、Task 12 步骤 3）
2. **密码的原样语义**——`" abc123 "`（首尾空格）与 `"abc123"` 必须是两个不同密码：注册用前者则登录必须用前者；`123456` 必须可注册可登录；24 个多字节字符的密码不得被静默截断。合理预期：注册与登录行为完全一致，不 trim、不规范化。（Task 4 步骤 1、Task 5 步骤 1）
3. **登录后的 CSRF 轮换**——登录成功会轮换 Session 标识，登录前取得的 CSRF 令牌随即失效。合理预期：前端在登录成功与登出之后自动重新 `GET /auth/csrf`，用户不会看到一次莫名的 403。（Task 6 步骤 1/3、Task 8 步骤 1）
4. **注册成功仍处于未登录态**——注册返回 201 后仍是匿名会话。合理预期：用户停留在注册成功反馈并跳转／点击进入登录页，`GET /auth/me` 此时应返回 401，任何页面都不得因 201 而显示为「已登录」。（Task 4 步骤 1、Task 7 步骤 1/3）
5. **首页静态内容真实性与降级**——介绍文案与资讯空状态必须来自服务端 `GET /home`、`GET /news`，不得由前端硬编码伪造。合理预期：接口失败时显示明确的加载失败，而**不是**被渲染成「暂无资讯」这种看似成功的空状态。（Task 10 步骤 1/3）

补充要求（同样必须在对应任务中测试）：`GET /auth/me` 只依据服务端会话判定身份，请求中携带或伪造 `userId` 参数不得改变返回账号（Task 5 步骤 1/3）。

---

## 四、文件结构（先定边界，再拆任务）

```
D:\AI-anti-scam\
├── coding plan\
│   └── 2026-10-17-phase1-auth-and-home.md          ← 本计划
├── tools\
│   └── validate-openapi.mjs                         ← openapi.yaml 静态校验脚本
├── backend\
│   ├── pom.xml
│   ├── docker-compose.yml                           ← 本地 MySQL 8.0.45
│   └── src\
│       ├── main\
│       │   ├── java\com\aiscam\antiscam\
│       │   │   ├── AntiScamApplication.java
│       │   │   ├── common\
│       │   │   │   ├── api\ApiResponse.java                 ← 统一成功封套
│       │   │   │   ├── api\ErrorCode.java                   ← 接口文档 §8 错误码枚举
│       │   │   │   ├── api\ErrorDetail.java / FieldError.java
│       │   │   │   ├── error\ApiException.java
│       │   │   │   ├── error\GlobalExceptionHandler.java    ← @RestControllerAdvice
│       │   │   │   ├── error\ValidationException.java
│       │   │   │   ├── web\RequestIdFilter.java             ← 生成/注入 requestId
│       │   │   │   └── web\RequestIds.java                  ← requestId 线程/请求作用域取值
│       │   │   ├── config\SecurityConfig.java               ← Session + CSRF + 401/403 出口
│       │   │   ├── config\JacksonConfig.java                ← 未知字段拒绝、字符串 ID 序列化
│       │   │   ├── config\AppProperties.java                ← cookie secure 等可配项
│       │   │   ├── auth\
│       │   │   │   ├── domain\UserRole.java                 ← USER / ADMIN
│       │   │   │   ├── domain\User.java                     ← @Entity sys_user
│       │   │   │   ├── domain\UserRepository.java
│       │   │   │   ├── username\Username.java               ← 规范化 + 校验（trim、码点、字符集）
│       │   │   │   ├── username\PasswordPolicy.java         ← 码点长度校验（不 trim）
│       │   │   │   ├── service\AuthService.java             ← 注册/登录/登出/当前账号
│       │   │   │   ├── service\CurrentUser.java             ← 已认证身份读取
│       │   │   │   └── web\AuthController.java              ← API-01～API-05
│       │   │   │   └── web\dto\RegisterRequest.java / LoginRequest.java / Account.java / CsrfToken.java / Registration.java / LogoutResult.java
│       │   │   ├── home\
│       │   │   │   ├── service\HomeService.java             ← 固定介绍文案 + 空资讯
│       │   │   │   ├── web\HomeController.java              ← API-06 / API-07
│       │   │   │   └── web\dto\Home.java / News.java
│       │   │   └── bootstrap\AdminAccountInitializer.java   ← 预开通管理员（环境变量注入）
│       │   └── resources\
│       │       ├── application.yml
│       │       ├── application-dev.yml
│       │       └── db\migration\V1__create_sys_user.sql
│       └── test\java\com\aiscam\antiscam\
│           ├── support\MySqlTestBase.java           ← Testcontainers MySQL 8.0.45 + Flyway
│           ├── auth\AuthApiIT.java                  ← API-01～API-05 集成测试
│           ├── auth\UsernameRuleTest.java           ← 纯单元：用户名/密码规则
│           ├── auth\UserRepositoryIT.java           ← username_key 唯一性（DBV-01）
│           ├── home\HomeApiIT.java                  ← API-06 / API-07
│           └── contract\OpenApiContractIT.java      ← 响应形状与契约一致
└── frontend\
    ├── package.json / vite.config.ts / tsconfig.json / index.html
    ├── .env.development                             ← VITE_API_BASE=/anti.scam/v1
    └── src\
        ├── main.ts / App.vue
        ├── router\index.ts                          ← 路由表 + 守卫
        ├── api\http.ts                              ← fetch 封装 + CSRF 自动注入 + 错误归一
        ├── api\csrf.ts                              ← CSRF 令牌缓存与刷新
        ├── api\types.ts                             ← 与 openapi.yaml 对齐的 TS 类型
        ├── api\auth.ts / api\home.ts                ← 各接口调用函数
        ├── stores\auth.ts                           ← 当前账号、角色、bootstrap
        ├── views\LoginView.vue / RegisterView.vue / HomeView.vue
        ├── views\PlaceholderView.vue                ← 配置/历史/后台的未实现说明页
        ├── components\AppNav.vue / NewsSection.vue / FormFieldError.vue
        └── __tests__\*.spec.ts
```

**结构约束：** 一个文件一个职责；`auth` 与 `home` 不互相 import 实体，只通过 `common` 与 `service` 接口交互；`common/api` 只放封套与错误码，不放业务逻辑；前端 `api/types.ts` 是唯一类型来源，视图组件不得自行声明接口响应类型。

---

## 五、任务清单

### Task 1：仓库骨架、MySQL 8.0.45 迁移与契约校验脚本

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/docker-compose.yml`
- Create: `backend/src/main/java/com/aiscam/antiscam/AntiScamApplication.java`
- Create: `backend/src/main/resources/application.yml`、`backend/src/main/resources/application-dev.yml`
- Create: `backend/src/main/resources/db/migration/V1__create_sys_user.sql`
- Create: `backend/src/test/java/com/aiscam/antiscam/support/MySqlTestBase.java`
- Create: `backend/src/test/java/com/aiscam/antiscam/support/MigrationIT.java`
- Create: `tools/validate-openapi.mjs`
- Create: `backend/.gitignore`、`frontend/.gitignore`、`.gitignore`

**Interfaces:**
- Consumes: 无（起始任务）
- Produces:
  - Maven 模块：`com.aiscam:anti-scam-backend`，Java 21，Spring Boot parent 管理依赖；主类 `com.aiscam.antiscam.AntiScamApplication`
  - 表 `sys_user`（列名与类型见 Step 3，后续所有任务直接使用）
  - `MySqlTestBase`（抽象基类，`@SpringBootTest` + Testcontainers `mysql:8.0.45` + Flyway 自动迁移，供后续所有 IT 继承）
  - `tools/validate-openapi.mjs`：`node tools/validate-openapi.mjs` 退出码 0 表示契约静态有效

- [ ] **Step 1：初始化目录与 Maven 工程**

在 `D:\AI-anti-scam` 下创建 `backend`、`frontend`、`tools` 目录与根 `.gitignore`（忽略 `node_modules/`、`dist/`、`target/`、`*.log`、`.env.local`）。

`backend/pom.xml` 的依赖清单（不写版本号，由 Spring Boot parent 的 BOM 决定）：`spring-boot-starter-web`、`spring-boot-starter-data-jpa`、`spring-boot-starter-security`、`spring-boot-starter-validation`、`flyway-core`、`flyway-mysql`、`mysql-connector-j`、`spring-boot-starter-test`(test)、`spring-security-test`(test)、`org.testcontainers:mysql`(test)、`org.testcontainers:junit-jupiter`(test)。构建插件：`spring-boot-maven-plugin`、`maven-surefire-plugin`（执行 `*Test`）、`maven-failsafe-plugin`（执行 `*IT`）。

- [ ] **Step 2：编写迁移文件 `V1__create_sys_user.sql`**

只有一个 `CREATE TABLE`，字段必须与下表逐字一致（含生成列与 CHECK）：

```sql
CREATE TABLE sys_user (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username      VARCHAR(24)     NOT NULL,
  username_key  VARCHAR(24)     GENERATED ALWAYS AS (LOWER(username)) STORED,
  password_hash VARCHAR(255)    NOT NULL,
  role          VARCHAR(16)     NOT NULL DEFAULT 'USER',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_user_username_key (username_key),
  CONSTRAINT ck_user_username_len CHECK (CHAR_LENGTH(username) BETWEEN 2 AND 24),
  CONSTRAINT ck_user_role CHECK (role IN ('USER','ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
```

同时在 `application.yml` 设置 `spring.flyway.enabled=true`、`spring.jpa.hibernate.ddl-auto=validate`、`spring.jpa.properties.hibernate.connection.isolation=2`（READ COMMITTED）、JDBC URL 带 `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`；`application-dev.yml` 指向 `docker-compose.yml` 暴露的本地库。

- [ ] **Step 3：编写 `docker-compose.yml`**

```yaml
services:
  mysql:
    image: mysql:8.0.45
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_bin --transaction-isolation=READ-COMMITTED
    environment:
      MYSQL_DATABASE: anti_scam
      MYSQL_USER: anti_scam_app
      MYSQL_PASSWORD: ${MYSQL_APP_PASSWORD:?set MYSQL_APP_PASSWORD}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?set MYSQL_ROOT_PASSWORD}
      TZ: UTC
    ports: ["3306:3306"]
```

- [ ] **Step 4：写 `MySqlTestBase` 与 `MigrationIT`（先写测试）**

`MigrationIT` 断言三件事：`sys_user` 存在且列集合与 Step 2 完全一致；插入同名不同大小写（`ZhangSan` 与 `zhangsan`）的第二行触发唯一键冲突；插入 1 字符与 25 字符用户名触发 CHECK 失败。

```java
@Test
void usernameKey_is_caseInsensitiveUnique() {
    // 插入 ZhangSan 成功；再插入 zhangsan 必须抛 DataIntegrityViolationException
}
```

- [ ] **Step 5：运行测试，确认当前失败/通过状态**

Run: `cd backend && ./mvnw -q verify -Dtest=MigrationIT -DfailIfNoTests=false`
Expected: 若镜像未就绪，先 `docker compose up -d mysql` 并等待健康；随后 `MigrationIT` **PASS**。若本机无 Docker，记录该事实并在本步骤备注「DB 相关验证未执行」，**不得**改用 H2 或 SQLite 替代来宣称通过。

- [ ] **Step 6：创建并验证 OpenAPI 契约校验脚本**

`tools/validate-openapi.mjs` 用 `@apidevtools/swagger-parser` 校验 `../openapi.yaml`：解析成功、`openapi` 以 `3.1` 开头、`servers[0].url === "/anti.scam/v1"`、`paths` 至少包含本阶段的 7 条路径且方法正确、不存在悬空 `$ref`。

Run: `node tools/validate-openapi.mjs`
Expected: 退出码 0，输出 `openapi.yaml OK: 7 phase-1 paths validated`。

- [ ] **Step 7：提交**

```bash
git add .gitignore backend tools
git commit -m "chore(phase1): backend skeleton, sys_user migration, openapi contract check"
```

---

### Task 2：统一响应封套、错误码与全局异常处理

**Files:**
- Create: `backend/src/main/java/com/aiscam/antiscam/common/api/ApiResponse.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/common/api/ErrorCode.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/common/api/ErrorDetail.java`、`FieldError.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/common/error/ApiException.java`、`ValidationException.java`、`GlobalExceptionHandler.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/common/web/RequestIdFilter.java`、`RequestIds.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/config/JacksonConfig.java`
- Test: `backend/src/test/java/com/aiscam/antiscam/common/ApiEnvelopeIT.java`

**Interfaces:**
- Consumes: Task 1 的 Spring Boot 骨架与 `MySqlTestBase`
- Produces:
  - `ApiResponse.ok(T data)` → `ApiResponse<T>`，序列化为 `{"code":"OK","message":"成功","data":...,"requestId":"<uuid>"}`
  - `ApiResponse<T>` 只含 `code/message/data/requestId` 四字段，禁止额外字段
  - `ErrorCode` 枚举（本阶段使用）：`VALIDATION_FAILED(400)`、`UNAUTHENTICATED(401)`、`INVALID_CREDENTIALS(401)`、`FORBIDDEN(403)`、`CSRF_INVALID(403)`、`NOT_FOUND(404)`、`USERNAME_TAKEN(409)`、`INTERNAL_ERROR(500)`、`SERVICE_UNAVAILABLE(503)`
  - `ApiException(ErrorCode code, String message, ErrorDetail detail)`（`detail` 可为 `null`）
  - `ValidationException(List<FieldError>)` → 400 `VALIDATION_FAILED`
  - 字段错误码常量：`REQUIRED`、`TYPE_INVALID`、`LENGTH_INVALID`、`VALUE_INVALID`、`PASSWORD_MISMATCH`、`UNKNOWN_FIELD`
  - `RequestIds.current()` → 当前请求的 requestId 字符串

- [ ] **Step 1：写失败测试 `ApiEnvelopeIT`**

```java
@Test
void unauthenticatedRequest_returnsEnvelopeWithRequestIdAndNoDetails() throws Exception {
    mockMvc.perform(get("/anti.scam/v1/home"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.message").value("请重新登录"))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(jsonPath("$.requestId").value(Matchers.matchesPattern(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")));

@Test
void validationFailure_returnsFieldErrorsWithoutEchoingValues() throws Exception { /* 见 Task 4 */ }

@Test
void unknownJsonField_isRejectedWithUnknownFieldError() throws Exception {
    // POST /auth/register 携带 "role":"ADMIN" → 400 VALIDATION_FAILED，
    // data.fieldErrors[0].field == "role"、code == "UNKNOWN_FIELD"
}
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && ./mvnw -q verify -Dit.test=ApiEnvelopeIT -DfailIfNoTests=false`
Expected: FAIL（`/anti.scam/v1/home` 尚不存在，响应不是 JSON 封套）。

- [ ] **Step 3：实现封套、错误码与异常处理**

要点：`RequestIdFilter` 为每个请求生成 UUID 放入请求属性（`OncePerRequestFilter`，`order` 设为最高优先级），`ApiResponse` 通过 `RequestIds.current()` 填充 `requestId`。`GlobalExceptionHandler` 必须覆盖：`ApiException`、`ValidationException`、`MethodArgumentNotValidException`、`HttpMessageNotReadableException`（→400 `TYPE_INVALID`）、`HttpRequestMethodNotSupportedException`（→405，空体不允许）、以及兜底 `Exception`（→500 `INTERNAL_ERROR`，`message` 固定为「服务暂时无法完成请求」，**不**透传异常文本）。`JacksonConfig` 设置 `FAIL_ON_UNKNOWN_PROPERTIES=true`，并由 `UnknownFieldException` 映射为 `UNKNOWN_FIELD` 字段错误。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && ./mvnw -q verify -Dit.test=ApiEnvelopeIT -DfailIfNoTests=false`
Expected: PASS（此时 `/home` 的 401 由 Spring Security 默认出口产生即可，Task 3 再替换为统一 JSON 出口；若尚未接入 Security 导致返回 404 而非 401，允许把该断言暂时改为对 `/anti.scam/v1/does-not-exist` 的 404 封套断言，并在 Task 3 补回 401 断言）。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(common): unified response envelope, error codes and global exception handling"
```

---

### Task 3：Spring Security 会话／CSRF 配置与 API-01 获取 CSRF 令牌

**Files:**
- Create: `backend/src/main/java/com/aiscam/antiscam/config/SecurityConfig.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/config/AppProperties.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/auth/web/AuthController.java`（本任务只实现 `GET /auth/csrf` 一个方法）
- Create: `backend/src/main/java/com/aiscam/antiscam/auth/web/dto/CsrfToken.java`
- Test: `backend/src/test/java/com/aiscam/antiscam/auth/CsrfApiIT.java`

**Interfaces:**
- Consumes: Task 2 的 `ApiResponse`、`ErrorCode`、`ApiException`
- Produces:
  - `GET /anti.scam/v1/auth/csrf` → 200 `{"code":"OK","message":"成功","data":{"token":"<opaque>","headerName":"X-CSRF-TOKEN"},"requestId":"..."}`，响应头 `Cache-Control: no-store`
  - 安全链固定行为：所有响应为 JSON；未认证 → 401 `UNAUTHENTICATED`；角色不足 → 403 `FORBIDDEN`；CSRF 失败 → 403 `CSRF_INVALID`；不重定向到 HTML
  - `CookieCsrfTokenRepository` 对外暴露头名为 `X-CSRF-TOKEN`；`JSESSIONID` Cookie 按全局约束 14 配置，`Secure` 由 `app.security.session-cookie-secure` 控制（默认 `true`，`dev` profile 为 `false`）
  - 会话策略：`SessionCreationPolicy.IF_REQUIRED`，最多 1 个会话（`maximumSessions(1)`）

- [ ] **Step 1：写失败测试 `CsrfApiIT`**

```java
@Test
void csrf_returnsTokenAndHeaderName_withNoStore() throws Exception {
    mockMvc.perform(get("/anti.scam/v1/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", containsString("no-store")))
        .andExpect(jsonPath("$.data.headerName").value("X-CSRF-TOKEN"))
        .andExpect(jsonPath("$.data.token").isNotEmpty())
        .andExpect(jsonPath("$.data.length()").value(2)); // 禁止额外字段

@Test
void csrf_isAllowedAnonymouslyAndDoesNotAuthenticate() throws Exception {
    // 同一 Session 再次 GET /auth/me 必须仍是 401
}

@Test
void postWithoutCsrfHeader_isRejectedAsCsrfInvalid() throws Exception {
    // 用任意 POST（如 /auth/logout）验证 → 403 CSRF_INVALID
}
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && ./mvnw -q verify -Dit.test=CsrfApiIT -DfailIfNoTests=false`
Expected: FAIL（404 或 403，尚无 `/auth/csrf`）。

- [ ] **Step 3：实现 `SecurityConfig`、`AppProperties` 与 `AuthController#getCsrf`**

要点：`authorizeHttpRequests` 显式列出 `/auth/csrf`、`/auth/register`、`/auth/login` 为 `permitAll()`，`/admin/**` 为 `hasRole("ADMIN")`，其余 `authenticated()`；CSRF 采用 `CookieCsrfTokenRepository.withHttpOnlyFalse()` 并显式装配 `CsrfTokenRequestAttributeHandler`（默认的 XOR 处理器会让普通 JSON 客户端无法回传原始令牌，必须显式覆盖为直接读取请求头的处理器，否则本阶段所有写请求都会 403）；`exceptionHandling` 三个出口（`authenticationEntryPoint`、`accessDeniedHandler`、`invalidSessionStrategy`）全部写 JSON 封套；`sessionManagement` 设置 `sessionFixation().changeSessionId()`；禁用 `formLogin`、`httpBasic`、框架默认 `logout`、`requestCache` 与匿名重定向。`AppProperties` 用 `@ConfigurationProperties("app.security")` 承载 `session-cookie-secure`，通过 `server.servlet.session.cookie.*` 生效。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && ./mvnw -q verify -Dit.test=CsrfApiIT -DfailIfNoTests=false`
Expected: PASS，同时 Task 2 的 `ApiEnvelopeIT` 401 断言恢复为通过。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(auth): session cookie security config and CSRF token endpoint (API-01)"
```

---

### Task 4：API-02 普通用户注册

**Files:**
- Create: `backend/src/main/java/com/aiscam/antiscam/auth/domain/UserRole.java`、`User.java`、`UserRepository.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/auth/username/Username.java`、`PasswordPolicy.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/auth/service/AuthService.java`（本任务只实现 `register`）
- Create: `backend/src/main/java/com/aiscam/antiscam/auth/web/dto/RegisterRequest.java`、`Account.java`、`Registration.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/auth/web/AuthController.java`（新增 `POST /auth/register`）
- Test: `backend/src/test/java/com/aiscam/antiscam/auth/UsernameRuleTest.java`（surefire，`*Test`）、`RegisterApiIT.java`、`UserRepositoryIT.java`（failsafe，`*IT`）

**Interfaces:**
- Consumes: Task 3 的安全链（`/auth/register` 已 `permitAll` 且要求 CSRF）
- Produces:
  - `Username.normalize(String raw) -> String`（去首尾空格）与 `Username.validate(String normalized) -> void`（抛 `ValidationException`，字段名 `username`）
  - `PasswordPolicy.validate(String password, String confirmPassword) -> void`（抛 `ValidationException`，字段名 `password` / `confirmPassword`）
  - `AuthService.register(String username, String password, String confirmPassword) -> Account`
  - `POST /anti.scam/v1/auth/register` → 201 `Registration`（`account` + `nextAction:"LOGIN"`）
  - `UserRepository.findByUsernameKey(String lowercasedUsername) -> Optional<User>`、`UserRepository.existsByUsernameKey(String) -> boolean`

- [ ] **Step 1：写纯单元测试 `UsernameRuleTest`（先失败）**

必须包含下表全部用例，期望值逐字固定：

| 输入 | 期望 |
|---|---|
| `" 李雷 "` | 规范化后 `"李雷"`，校验通过 |
| `"a"` | 拒绝，`LENGTH_INVALID` |
| `"ab"` | 通过 |
| 24 个汉字 | 通过 |
| 25 个汉字 | 拒绝，`LENGTH_INVALID` |
| `"张 三"`（中间空格） | 拒绝，`VALUE_INVALID` |
| `"user%"` / `"user@"` / `"user."` | 拒绝，`VALUE_INVALID` |
| `"ＡＢＣ"`（全角字母） | 拒绝，`VALUE_INVALID` |
| `"user😀"` | 拒绝，`VALUE_INVALID` |
| `"abc_123"` | 通过 |
| 密码 `"12345"` | 拒绝，`LENGTH_INVALID` |
| 密码 `"123456"` | 通过 |
| 密码 `" abc123 "` | 通过，且 `PasswordPolicy` 返回的字符串与输入**逐字符相等**（含首尾空格） |
| 密码 25 个字符 | 拒绝，`LENGTH_INVALID` |
| `password="abc123"`, `confirmPassword=" abc123 "` | 拒绝，字段 `confirmPassword`，码 `PASSWORD_MISMATCH` |

- [ ] **Step 2：运行单元测试确认失败**

Run: `cd backend && ./mvnw -q test -Dtest=UsernameRuleTest`
Expected: FAIL（`Username`、`PasswordPolicy` 尚不存在，测试编译失败）。此步骤证明测试确实在约束尚未实现的行为。

- [ ] **Step 3：实现 `Username` 与 `PasswordPolicy`**

要点：长度一律用 `codePointCount` 计数；字符白名单用码点判断（`[A-Za-z0-9_]` 或 `\u4E00-\u9FFF`）。**不得**在密码上调用 `trim()`、`strip()`、`normalize()`、`toLowerCase()`。校验只做「长度 + 字符集」，不做弱密码拦截。

- [ ] **Step 4：运行单元测试确认通过**

Run: `cd backend && ./mvnw -q test -Dtest=UsernameRuleTest`
Expected: PASS。

- [ ] **Step 5：写集成测试 `RegisterApiIT` 与 `UserRepositoryIT`（先失败）**

```java
@Test void register_createsUserAndDoesNotLogIn() throws Exception {
    // 201；data.account.role == "USER"；data.nextAction == "LOGIN"；
    // data.account.id 匹配 ^[1-9][0-9]{0,19}$ 且为字符串；
    // 随后同一 Session GET /auth/me → 401
}
@Test void register_withRoleField_rejected400UnknownField() { /* "role":"ADMIN" */ }
@Test void register_duplicateUsernameDifferentCase_returns409() {
    // 先注册 "ZhangSan"，再注册 "zhangsan" → 409 USERNAME_TAKEN，message "用户名已被使用"
}
@Test void register_weakPassword123456_succeeds() { /* 201 */ }
@Test void register_passwordWithSurroundingSpaces_isStoredVerbatim() {
    // 注册 " abc123 "；UserRepository 读回后用 BCryptPasswordEncoder.matches(" abc123 ", hash) == true
    // 且 matches("abc123", hash) == false
}
@Test void register_passwordMismatch_returns400WithConfirmPasswordField() { /* PASSWORD_MISMATCH */ }
@Test void register_usernameWithSurroundingSpaces_isTrimmedAndStored() {
    // 提交 " 李雷 " → 201；data.account.username == "李雷"
}
@Test void register_duplicateUsernameSameCase_returns409() { /* 409 */ }
@Test void register_password25Codepoints_returns400() { /* LENGTH_INVALID */ }
@Test void register_missingCsrf_returns403CsrfInvalid() { /* 403 CSRF_INVALID */ }
```

`UserRepositoryIT` 再断言：并发插入同一 `username_key`（两个独立连接、`CountDownLatch` 对齐后同时提交）时**恰好一行成功**，另一行抛 `DataIntegrityViolationException`。这是 DBV-01 的数据库层证据，不能只靠「先查后写」。

- [ ] **Step 6：运行集成测试确认失败**

Run: `cd backend && ./mvnw -q verify -Dit.test='RegisterApiIT+UserRepositoryIT' -DfailIfNoTests=false`
Expected: FAIL（`AuthService#register` 未实现 / 接口 404）。

- [ ] **Step 7：实现 `User` 实体、`UserRepository`、`AuthService#register` 与 `POST /auth/register`**

要点：`Account.id` 用 `String.valueOf(id)` 序列化为字符串；插入使用 `saveAndFlush`；`@Transactional` 内的 `DataIntegrityViolationException` 必须捕获后重新抛出 `ApiException(USERNAME_TAKEN)`（不能把唯一键冲突透传成 500）；角色硬编码 `UserRole.USER`；密码用注入的 `PasswordEncoder`（`BCryptPasswordEncoder`）编码；**不写入任何登录态**。为 `ApiException` 的 409 提供 `message="用户名已被使用"`。

- [ ] **Step 8：运行集成测试确认通过**

Run: `cd backend && ./mvnw -q verify -Dit.test='RegisterApiIT+UserRepositoryIT' -DfailIfNoTests=false`
Expected: PASS。

- [ ] **Step 9：提交**

```bash
git add backend/src
git commit -m "feat(auth): user registration with PRD username and password rules (API-02)"
```

---

### Task 5：API-03 共用登录、API-04 登出、API-05 当前账号

**Files:**
- Modify: `backend/src/main/java/com/aiscam/antiscam/auth/service/AuthService.java`（新增 `login` / `logout` / `currentAccount`）
- Create: `backend/src/main/java/com/aiscam/antiscam/auth/service/CurrentUser.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/auth/web/dto/LoginRequest.java`、`LogoutResult.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/auth/web/AuthController.java`（新增登录/登出/当前账号）
- Modify: `backend/src/main/java/com/aiscam/antiscam/config/SecurityConfig.java`（登出必须由控制器实现，不使用框架默认登出）
- Test: `backend/src/test/java/com/aiscam/antiscam/auth/LoginLogoutApiIT.java`

**Interfaces:**
- Consumes: Task 4 的 `AuthService.register`、`UserRepository`、`Username`/`PasswordPolicy`
- Produces:
  - `AuthService.login(String rawUsername, String rawPassword, HttpServletRequest request) -> Account`（成功时 `request.changeSessionId()` 轮换会话标识）
  - `AuthService.logout(HttpServletRequest request) -> void`（`request.getSession(false).invalidate()`）
  - `AuthService.currentAccount(Authentication auth) -> Account`
  - `POST /auth/login` → 200 `Account`；`POST /auth/logout` → 200 `{"loggedOut":true}`；`GET /auth/me` → 200 `Account`

- [ ] **Step 1：写集成测试 `LoginLogoutApiIT`（先失败）**

必须覆盖：

```java
@Test void login_userAndAdmin_shareSameEndpointAndReflectRole()
@Test void login_wrongPassword_returns401InvalidCredentialsWithUnifiedMessage()  // message 恰为 "账号或密码错误"
@Test void login_unknownUsername_returns401AndIndistinguishableFromWrongPassword()
    // 两次响应的 code/message 完全一致；data 均为 null；不得出现 fieldErrors 指向 username
@Test void login_usernameWithSurroundingSpaces_succeeds()                        // " 李雷 " → 200
@Test void login_usernameDifferentCase_succeeds()                                // "ZHANGSAN" → 200
@Test void login_passwordWithSurroundingSpaces_requiresExactMatch()
    // 注册 " abc123 " 后：用 " abc123 " → 200；用 "abc123" → 401
@Test void login_rotatesSessionId_soOldCsrfTokenIsRejected()
    // 登录前取 csrfA；登录成功后用 csrfA 调 POST /auth/logout → 403 CSRF_INVALID
@Test void logout_destroysSessionAndProtectedEndpointsReturn401()
    // 登出后 GET /auth/me → 401；再带旧 Cookie 调 GET /home → 401
@Test void me_ignoresClientSuppliedUserId()
    // 用户A 已登录，GET /auth/me?userId=999 与 /auth/me 返回同一账号；不得返回 999 对应账号
@Test void login_asAdmin_returnsAdminRole()                                // data.account.role == "ADMIN"
@Test void register_thenMe_returns401UntilLogin()
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && ./mvnw -q verify -Dit.test=LoginLogoutApiIT -DfailIfNoTests=false`
Expected: FAIL。

- [ ] **Step 3：实现登录、登出、当前账号**

要点：登录先 `Username.normalize` 再按 `username_key` 查询；**无论账号不存在还是密码不匹配，都抛同一个** `ApiException(INVALID_CREDENTIALS, "账号或密码错误")`，且 `detail=null`；成功时把 `UserRole` 映射为权限 `ROLE_USER` / `ROLE_ADMIN` 写入 `SecurityContext`，并调用 `request.changeSessionId()`；`logout` 使用 `SecurityContextLogoutHandler` 清理上下文后使会话失效，并以相同 `Path=/anti.scam/v1` 写 `Max-Age=0` 的清除 Cookie；`me` 只读 `Authentication`，**不接收任何身份参数**。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && ./mvnw -q verify -Dit.test=LoginLogoutApiIT -DfailIfNoTests=false`
Expected: PASS。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(auth): shared login, logout and current account (API-03/04/05)"
```

---

### Task 6：前端骨架、HTTP 封装与 CSRF 令牌自动管理

**Files:**
- Create: `frontend/package.json`、`vite.config.ts`、`tsconfig.json`、`index.html`、`.env.development`
- Create: `frontend/src/main.ts`、`frontend/src/App.vue`
- Create: `frontend/src/api/types.ts`、`frontend/src/api/http.ts`、`frontend/src/api/csrf.ts`
- Create: `frontend/src/api/auth.ts`
- Create: `frontend/vitest.config.ts`、`frontend/src/__tests__/http.spec.ts`、`frontend/src/__tests__/csrf.spec.ts`

**Interfaces:**
- Consumes: Task 3 的 `GET /auth/csrf`（`{token, headerName}`）
- Produces:
  - `http.get<T>(path: string): Promise<T>`、`http.post<T>(path: string, body?: unknown): Promise<T>`，`path` 为相对前缀的路径（如 `/auth/me`）
  - `ApiError` 类：`{ status: number; code: string; message: string; fieldErrors: FieldError[] }`
  - `csrf.ensure(): Promise<string>`、`csrf.refresh(): Promise<string>`、`csrf.clear(): void`
  - `authApi.getCsrf()/register()/login()/logout()/me()`
  - `types.ts` 导出 `Role`、`Account`、`CsrfToken`、`Registration`、`LogoutResult`、`HomePayload`、`NewsPayload`、`FieldError`

- [ ] **Step 1：写失败测试 `csrf.spec.ts` 与 `http.spec.ts`**

```ts
it('posts include X-CSRF-TOKEN from the cached token', async () => {
  // 先 GET /auth/csrf 返回 {token:'t1',headerName:'X-CSRF-TOKEN'}
  // 再 POST /auth/login：断言请求头含 'X-CSRF-TOKEN': 't1'
});
it('fetches the csrf token lazily and only once for concurrent posts', async () => {});
it('refresh() re-fetches after login so the stale token is not reused', async () => {
  // 登录成功处理里调用 csrf.refresh()；随后 POST 携带的是 't2' 而不是 't1'
});
it('maps a non-OK envelope to ApiError with code and fieldErrors', async () => {
  // 返回 400 {code:'VALIDATION_FAILED',data:{fieldErrors:[...]}} → ApiError.code/fieldErrors 可读
});
it('never throws on 401 but surfaces UNAUTHENTICATED for the caller to redirect', async () => {});
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd frontend && npm install && npx vitest run src/__tests__/csrf.spec.ts`
Expected: FAIL（模块不存在）。

- [ ] **Step 3：实现 `http.ts` 与 `csrf.ts`**

要点：基于原生 `fetch`，`credentials: 'same-origin'`；`baseURL` 取 `import.meta.env.VITE_API_BASE`（默认 `/anti.scam/v1`）；写操作（POST/PUT/DELETE）先 `await csrf.ensure()` 再注入请求头，头名取自 `headerName` 字段而非硬编码；`Content-Type: application/json`；解析响应时先判 HTTP 状态再判 `code`；401/403/409/400 统一转 `ApiError`；不自动重试写请求；令牌只存内存，不写 `localStorage`、不写日志。

- [ ] **Step 4：运行测试与类型检查，确认通过**

Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit`
Expected: 两条命令均退出码 0（`vue-tsc` 在 `package.json` 的 `dependencies`/`devDependencies` 中声明，并由 `npm run typecheck` 脚本暴露）。

- [ ] **Step 5：配置 Vite 开发代理以保持同源**

`vite.config.ts` 中 `server.proxy['/anti.scam/v1'] = { target: 'http://localhost:8080', changeOrigin: false }`，使 Cookie 与 CSRF 在本地开发也满足同源前提（浏览器所见源仍是 `http://localhost:5173`，Cookie 为第一方）；`vitest.config.ts` 使用 `environment: 'jsdom'`。

- [ ] **Step 6：提交**

```bash
git add frontend
git commit -m "feat(frontend): vite skeleton, fetch wrapper and CSRF token handling"
```

---

### Task 7：登录页与注册页

**Files:**
- Create: `frontend/src/views/LoginView.vue`、`frontend/src/views/RegisterView.vue`
- Create: `frontend/src/components/FormFieldError.vue`
- Modify: `frontend/src/api/auth.ts`（补齐 `register/login/me/logout` 调用）
- Test: `frontend/src/__tests__/login-view.spec.ts`、`frontend/src/__tests__/register-view.spec.ts`、`frontend/src/__tests__/phase1-flow.spec.ts`（整条前端串联流程，MSW 驱动）

**Interfaces:**
- Consumes: Task 6 的 `http`、`csrf`、`authApi`、`types.ts`
- Produces:
  - `LoginView`：`data-testid="login-form"`、字段 `login-username` / `login-password`、提交按钮 `login-submit`、错误区 `login-error`
  - `RegisterView`：`data-testid="register-form"`、字段 `register-username` / `register-password` / `register-confirm`、提交按钮 `register-submit`、字段错误区 `register-field-errors`、成功区 `register-success`
  - 两个组件通过 `router.push` 跳转，不直接操作会话 Cookie

- [ ] **Step 1：写失败测试（先写测试）**

```ts
// login-view.spec.ts
it('submits credentials and routes USER to /home, ADMIN to /admin', async () => {});
it('shows the unified "账号或密码错误" message on 401 and does not reveal account existence', async () => {});
it('refreshes CSRF after a successful login before the next write', async () => {});
// register-view.spec.ts
it('shows a field error for username/confirmPassword from the API fieldErrors payload', async () => {});
it('shows "用户名已被使用" on 409 USERNAME_TAKEN', async () => {});
it('after 201 shows a success notice and a link to login, and stays unauthenticated', async () => {
  // 断言：未调用 router.push('/home')；authStore.current === null
});
it('does not render any role selector input', () => {});
// phase1-flow.spec.ts（串联：注册页 → 登录页 → 首页）
it('register then stay anonymous, then log in and land on the home view with server content', async () => {
  // 1) 注册成功：断言仍在注册页、显示成功提示、未发生登录跳转
  // 2) 跳转登录并提交：断言 CSRF 已刷新、按角色跳转到 /home
  // 3) 首页渲染服务端返回的 introduction 与资讯空状态；断言页面无硬编码介绍文案
});
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd frontend && npx vitest run src/__tests__/login-view.spec.ts src/__tests__/register-view.spec.ts`
Expected: FAIL。

- [ ] **Step 3：实现两个页面与字段错误组件**

要点：提交前做与后端一致的**轻量前端预校验**（trim 后 2—24 码点、字符集、密码 6—24、两次一致），但它只是体验优化，**服务端 400 的 `fieldErrors` 必须逐条渲染到对应字段**；密码输入框 `type="password"` 且 `autocomplete="current-password"` / `"new-password"`；登录失败提示固定为「账号或密码错误」；注册成功不触发任何登录跳转，显示「注册成功，请登录」并提供跳转链接；两个页面互相提供导航链接；全页面不出现角色选择控件。

- [ ] **Step 4：运行测试确认通过**

Run: `cd frontend && npx vitest run`
Expected: PASS。

- [ ] **Step 5：提交**

```bash
git add frontend/src
git commit -m "feat(frontend): shared login and registration views"
```

---

### Task 8：会话引导与角色路由守卫

**Files:**
- Create: `frontend/src/stores/auth.ts`
- Create: `frontend/src/router/index.ts`
- Modify: `frontend/src/main.ts`（挂载 Pinia 与 router，启动时执行 `authStore.bootstrap()`）
- Test: `frontend/src/__tests__/router-guard.spec.ts`、`frontend/src/__tests__/auth-store.spec.ts`

**Interfaces:**
- Consumes: Task 6 的 `authApi.me()`、Task 7 的两个视图
- Produces:
  - `useAuthStore()` 暴露 `current: Account | null`、`role: Role | null`、`bootstrapped: boolean`、`bootstrap(): Promise<void>`（调用 `GET /auth/me`，401 时置空且不抛异常）、`signIn(account)`、`signOut()`
  - 路由表：`/login`（匿名）、`/register`（匿名）、`/home`（USER）、`/admin`（ADMIN）、`/configuration` 与 `/history`（USER）、`/` 重定向规则
  - 守卫行为：`/home` 与 `/` 未登录 → `/login`；ADMIN 访问 `/home`、`/configuration`、`/history` → 重定向 `/admin`；USER 访问 `/admin` → 重定向 `/home`；已登录访问 `/login`、`/register` → 按角色重定向

- [ ] **Step 1：写失败测试**

```ts
it('/home without session redirects to /login', async () => {});
it('/ redirects USER to /home and ADMIN to /admin', async () => {});
it('ADMIN cannot enter /home and is redirected to /admin', async () => {});
it('USER cannot enter /admin and is redirected to /home', async () => {});
it('bootstrap treats 401 as anonymous without throwing or logging out loudly', async () => {});
it('guard never grants access based only on a client-side flag when /auth/me returns 401', async () => {});
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd frontend && npx vitest run src/__tests__/router-guard.spec.ts`
Expected: FAIL。

- [ ] **Step 3：实现 store 与守卫**

要点：守卫内的身份结论**只来自** `authStore.bootstrap()` 的 `GET /auth/me` 结果（一次会话内缓存，`bootstrapped` 后不再重复请求，登出后重置）；前端重定向只是体验，服务端 403/401 才是权限事实；遇到 `ApiError.code === 'UNAUTHENTICATED'` 时清空 store 并跳转 `/login`；不在守卫中吞掉网络错误（网络错误显示为可重试的页面状态）。

- [ ] **Step 4：运行测试确认通过**

Run: `cd frontend && npx vitest run`
Expected: PASS。

- [ ] **Step 5：提交**

```bash
git add frontend/src
git commit -m "feat(frontend): auth store, session bootstrap and role-based route guards"
```

---

### Task 9：API-06 用户首页与 API-07 资讯空结构

**Files:**
- Create: `backend/src/main/java/com/aiscam/antiscam/home/web/dto/Home.java`、`News.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/home/service/HomeService.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/home/web/HomeController.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/bootstrap/AdminAccountInitializer.java`
- Modify: `backend/src/main/resources/application.yml`（新增 `app.admin.*` 配置项，默认不创建管理员）
- Test: `backend/src/test/java/com/aiscam/antiscam/home/HomeApiIT.java`、`backend/src/test/java/com/aiscam/antiscam/bootstrap/AdminAccountInitializerIT.java`

**Interfaces:**
- Consumes: Task 5 的登录能力（IT 需要一个 USER 与一个 ADMIN 账号）
- Produces:
  - `HomeService.getHome() -> Home`，固定值：
    - `title = "AI反诈情景推演平台"`
    - `introduction = "观看AI情景演绎，结合逐轮解说、风险与复盘学习反诈知识。"`
    - `newsState = "EMPTY"`
    - `entries = ["CONFIGURATION", "HISTORY"]`（顺序固定）
  - `HomeService.listNews() -> List<Object>` 恒为空列表
  - `GET /home` → 200 `Home`；`GET /news` → 200 `[]`，两者响应头 `Cache-Control: no-store`
  - `AdminAccountInitializer implements ApplicationRunner`：读 `app.admin.username` / `app.admin.password`，仅当两者非空且库中无该 `username_key` 时插入 `role='ADMIN'`；密码经 `PasswordEncoder` 编码；**日志只输出用户名与「创建／已存在」，绝不输出密码**

- [ ] **Step 1：写失败测试 `HomeApiIT`**

```java
@Test void home_returnsFixedContentForUser() {
    // 200；data.title/introduction/newsState 与上面固定值逐字一致；
    // data.entries == ["CONFIGURATION","HISTORY"]；data 只含 4 个字段
}
@Test void home_asAdmin_returns403Forbidden()      // 管理员不获得用户体验权限
@Test void home_anonymously_returns401Unauthenticated()
@Test void news_returnsEmptyArrayForUser()         // data == []；且不是 null
@Test void news_asAdmin_returns403Forbidden()
@Test void home_setsNoStoreCacheHeader()
```

`AdminAccountInitializerIT` 断言：配置为空时不创建任何行；配置有效时创建 1 行 `role='ADMIN'`；重复启动不产生第二行；能用一个**非 `sys_user` 表**的方式验证密码已哈希（`password_hash` 不等于明文且 `matches(明文, hash)` 为 true）。

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && ./mvnw -q verify -Dit.test='HomeApiIT+AdminAccountInitializerIT' -DfailIfNoTests=false`
Expected: FAIL（404）。

- [ ] **Step 3：实现 HomeService、HomeController 与 AdminAccountInitializer**

要点：`Home` 与 `News` DTO 用固定字段的 record 或不可变类，**禁止**把 Map 直接返回（否则无法保证「禁止额外字段」）；`entries` 用枚举 `HomeEntry { CONFIGURATION, HISTORY }` 序列化为字符串；`/home`、`/news` 在 `SecurityConfig` 中保持默认「已认证」，并额外用方法级注解或显式规则限制为 `hasRole("USER")`，确保 ADMIN 得到 403 而非 200。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && ./mvnw -q verify -Dit.test='HomeApiIT+AdminAccountInitializerIT' -DfailIfNoTests=false`
Expected: PASS。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(home): user home and empty news contract with admin bootstrap (API-06/07)"
```

---

### Task 10：首页视图、资讯空状态与未实现模块占位页

**Files:**
- Create: `frontend/src/api/home.ts`、`frontend/src/views/HomeView.vue`、`frontend/src/views/PlaceholderView.vue`
- Create: `frontend/src/components/AppNav.vue`、`frontend/src/components/NewsSection.vue`
- Test: `frontend/src/__tests__/home-view.spec.ts`

**Interfaces:**
- Consumes: Task 9 的 `GET /home`、`GET /news`；Task 8 的 router 与 auth store
- Produces:
  - `homeApi.getHome(): Promise<HomePayload>`、`homeApi.getNews(): Promise<NewsPayload>`
  - `HomeView`：`data-testid="home-introduction"`、`home-news-empty`、`home-entry-configuration`、`home-entry-history`、`home-error`
  - `PlaceholderView`：props `{ title: string; description: string }`，`data-testid="placeholder-view"`

- [ ] **Step 1：写失败测试 `home-view.spec.ts`**

```ts
it('renders the introduction text exactly as returned by GET /home', async () => {
  // mock 返回 introduction 为 "服务端介绍文案" → 断言页面出现该文本，
  // 且页面不包含任何硬编码介绍文案
});
it('renders the news empty state from the server payload', async () => {
  // newsState === 'EMPTY' 且 GET /news 返回 [] → 显示"暂无资讯"，
  // 且断言页面上不存在任何虚构资讯条目（列表项数量为 0）
});
it('shows a load failure instead of an empty state when GET /home fails', async () => {
  // 500 → 显示 home-error；断言"暂无资讯"文案不出现
});
it('shows a retryable failure when GET /home returns 403', async () => {});
it('renders both entries as links to /configuration and /history', async () => {});
it('renders the placeholder page content for /configuration and /history', async () => {
  // 断言占位页明确写出该模块尚未实现，不含表单或请求
});
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd frontend && npx vitest run src/__tests__/home-view.spec.ts`
Expected: FAIL。

- [ ] **Step 3：实现首页与占位页**

要点：介绍文案与资讯条目全部来自服务端响应，视图内**不得**出现硬编码的介绍或资讯占位文本；`newsState === 'EMPTY'` 时才渲染「暂无资讯」；`GET /home` 或 `GET /news` 失败时渲染 `home-error` 与重试按钮；导航区显示当前用户名与登出按钮（登出实现见 Task 11）；`/configuration`、`/history` 指向 `PlaceholderView`，文案为「该模块将在后续阶段实现」，并**不得**发起任何请求。

- [ ] **Step 4：运行测试确认通过**

Run: `cd frontend && npx vitest run`
Expected: PASS。

- [ ] **Step 5：提交**

```bash
git add frontend/src
git commit -m "feat(frontend): home view with server-driven introduction and empty news state"
```

---

### Task 11：导航登出与管理后台占位页

**Files:**
- Modify: `frontend/src/components/AppNav.vue`（登出按钮 `data-testid="nav-logout"`）
- Modify: `frontend/src/stores/auth.ts`（`signOut()` 调用 `POST /auth/logout` → `csrf.clear()` → 重置 store → `router.replace('/login')`）
- Create: `frontend/src/views/AdminHomeView.vue`（`data-testid="admin-home"`，仅显示「管理后台将在后续阶段实现」与登出按钮，**不发起任何 `/admin/**` 请求**）
- Modify: `frontend/src/router/index.ts`（`/admin` 指向 `AdminHomeView`）
- Test: `frontend/src/__tests__/logout.spec.ts`、`frontend/src/__tests__/admin-home.spec.ts`

**Interfaces:**
- Consumes: Task 5 的 `POST /auth/logout`、Task 8 的 store 与守卫
- Produces: 登出后进入 `/login`，store 清空，CSRF 缓存清空；`/admin` 仅为说明页

- [ ] **Step 1：写失败测试**

```ts
it('logout clears the session, csrf cache and routes to /login', async () => {});
it('after logout a protected request is not attempted and /home redirects to /login', async () => {});
it('admin home renders a placeholder and issues no /admin API calls', async () => {
  // 断言 fetch 调用次数为 0（除 /auth/me 的一次引导）
});
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd frontend && npx vitest run src/__tests__/logout.spec.ts src/__tests__/admin-home.spec.ts`
Expected: FAIL。

- [ ] **Step 3：实现登出与后台占位页**

要点：登出必须**先**调用 `POST /auth/logout`（带 CSRF）再清理本地状态；服务端返回 401（会话已失效）时仍按已登出处理并清理本地状态、跳转 `/login`；`csrf.clear()` 之后下一次登录会重新获取令牌。

- [ ] **Step 4：运行测试确认通过**

Run: `cd frontend && npx vitest run`
Expected: PASS。

- [ ] **Step 5：提交**

```bash
git add frontend/src
git commit -m "feat(frontend): logout flow and admin placeholder entry"
```

---

### Task 12：端到端验收、契约一致性核对与运行说明

**Files:**
- Create: `backend/src/test/java/com/aiscam/antiscam/contract/OpenApiContractIT.java`
- Create: `backend/src/test/java/com/aiscam/antiscam/e2e/AuthAndHomeE2EIT.java`
- Create: `README.md`（根目录）

**Interfaces:**
- Consumes: 前 11 个任务的全部产物
- Produces: 一份可复现的验收记录（命令与输出），以及 `README.md` 中的本地运行步骤与锁定版本

- [ ] **Step 1：写契约一致性测试 `OpenApiContractIT`**

思路：加载 `../openapi.yaml`（用 `swagger-parser` 的 Java 替代方案不可用时，改为在测试中读取 YAML 文件并断言固定字符串/结构），对本阶段 7 个接口逐个断言：

- 成功响应恰好是 `code/message/data/requestId` 四字段；
- `POST /auth/register` 的 400/403/409、`POST /auth/login` 的 400/401/403、`GET /home` 与 `/news` 的 401/403 全部可达且 `code` 与接口文档 §8 一致；
- 错误响应的 `data` 为 `null`，或为仅含 `fieldErrors/activeSessionId/currentVersion` 三字段的 `ErrorDetail`；
- `Account.id` 为字符串且匹配 `^[1-9][0-9]{0,19}$`；
- 请求体携带 `role`/`userId`/`outcome`/`riskScore` 时返回 400 而非被忽略。

```java
@Test void errorResponses_matchErrorCodeTable() throws Exception { /* 遍历上表逐项断言 code */ }
@Test void successEnvelopes_haveExactlyFourTopLevelFields() throws Exception {}
@Test void accountId_isTransmittedAsStringId() throws Exception {}
@Test void privilegedWriteFields_areRejected() throws Exception {}
```

- [ ] **Step 2：运行契约测试确认失败后再通过**

Run: `cd backend && ./mvnw -q verify -Dit.test=OpenApiContractIT -DfailIfNoTests=false`
Expected: 若发现与 `openapi.yaml` 不一致的字段（例如 `message` 文案、`entries` 顺序、状态码），**先修实现**再重跑，直到 PASS。任何「改测试而不是改实现」的调整都必须在提交信息中说明理由。

- [ ] **Step 3：写端到端验收测试 `AuthAndHomeE2EIT`（覆盖 AC-01、AC-02、AC-08、AP-08）**

```java
@Test void ac01_registerThenLoginThenHome() {
    // 注册（201，未自动登录）→ GET /auth/me = 401 → 登录（200，role=USER）
    // → GET /home = 200 且 newsState=EMPTY → GET /news = []
}
@Test void ac02_userAndAdminEnterTheirOwnAreas() {
    // 同一路径登录两个账号：
    //   USER  → 200 且 role="USER"；GET /home=200、GET /news=200
    //   ADMIN → 200 且 role="ADMIN"；GET /home=403、GET /news=403、POST /auth/logout=200
    // 角色分流结论以服务端响应为准（本阶段 /admin/** 接口尚未实现，不做该路径断言）
}
@Test void ac02_adminCannotReadUserHomeEvenAfterLogin() { /* 补充固化：403 FORBIDDEN */ }
@Test void ac08_homeShowsIntroductionEmptyNewsAndBothEntries() {}
@Test void ap08_usernameAndPasswordBoundaryMatrix() {
    // 用户名长度 1/2/24/25、大小写重名、首尾空格、中文/字母/数字/下划线；
    // 密码长度 5/6/24/25、123456、首尾空格、两次不一致 —— 逐项断言 code
}
@Test void dbv01_usernameUniquenessIsGlobalAcrossRoles() {
    // 预开通 ADMIN 名为 "AdminOne"；再注册 "adminone" 必须 409
}
```

- [ ] **Step 4：运行端到端测试**

Run: `cd backend && ./mvnw -q verify -Dit.test=AuthAndHomeE2EIT -DfailIfNoTests=false`
Expected: PASS（需要 Docker 提供 MySQL 8.0.45）。

- [ ] **Step 5：前后端联调冒烟（人工步骤，记录输出）**

1. `docker compose up -d mysql`
2. `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--app.admin.username=admin_demo,--app.admin.password=<自定强口令>`
3. `cd frontend && npm run dev`
4. 浏览器（Chrome/Edge）访问 `http://localhost:5173`：注册 → 回登录 → 登录 → 进入首页 → 看到介绍与「暂无资讯」→ 点击两个入口 → 打开 DevTools 确认 `/home`、`/news` 响应为统一封套，且 `JSESSIONID` 的 `Path=/anti.scam/v1`、`HttpOnly`、`SameSite=Lax` 存在。

将实际观察结果（含失败项）写入 `README.md` 的「验收记录」小节。

- [ ] **Step 6：写 `README.md`**

必须包含：项目结构、锁定的版本清单（Java、Spring Boot、MySQL、Node、Vue、Vite 等实际使用版本）、`docker compose` 起库命令、后端与前端启动命令、管理员预开通方式（环境变量注入，**不含任何默认口令**）、测试命令（`./mvnw verify`、`npx vitest run`、`node tools/validate-openapi.mjs`）、本阶段已实现／未实现模块清单、以及「Docker 不可用时哪些验证未执行」的如实声明。

- [ ] **Step 7：全量回归**

Run: `cd backend && ./mvnw -q verify` 与 `cd frontend && npx vitest run` 与 `node tools/validate-openapi.mjs`
Expected: 全部通过（或如实记录未执行项）。

- [ ] **Step 8：提交**

```bash
git add backend/src README.md frontend/src coding\ plan
git commit -m "test(phase1): contract consistency, AC-01/02/08 and AP-08 acceptance coverage"
```

---

## 六、完成判据（Definition of Done）

1. `./mvnw verify` 中 `*Test` 与 `*IT` 全部通过；`npx vitest run` 全部通过；`node tools/validate-openapi.mjs` 退出码 0。
2. Task 12 Step 3 的 `AuthAndHomeE2EIT` 全绿，且每条断言都对应 PRD 的 AC-01、AC-02、AC-08、AP-08 之一。
3. 「复审重点」五条各自有至少一个通过的测试，且测试名能直接读出它守的是什么行为。
4. `git log` 中每个 Task 恰好一次提交，提交信息以 Task 编号可追溯。
5. 数据库只有 `sys_user` 一张业务表（外加 Flyway 自身的迁移元数据表），不存在任何为后续模块预留的空表或占位字段。
6. `README.md` 中的启动步骤可被未参与开发的人在干净环境复现；未执行的验证项如实列出。

## 七、风险与阻塞项（执行者必须回报而非自行决定）

- **Docker 不可用**：Task 1、4、5、9、12 的数据库相关断言无法执行。处理方式是在对应步骤停留并回报，**不得**改用 H2／SQLite 宣称通过（数据库设计文档 §12.1 明确禁止用替代库证明生成列、排序规则和锁行为）。
- **`utf8mb4_0900_bin` 与生成列 `LOWER()` 的组合行为**：属数据库设计文档 DBV-01 要求实测项。若 Task 1 Step 4 未按预期生效，回报并暂停，不要自行改成应用层 `toLowerCase()` 生成唯一键。
- **Spring Boot / 依赖版本未锁定**：按全局约束 18 在 Task 1 锁定并写入 README；若某版本与 Spring Security 6 的 CSRF 默认行为不兼容（例如默认使用 `XorCsrfTokenRequestAttributeHandler` 导致前端必须回传原始令牌），回报实际行为再决定配置方式，不要静默放宽 CSRF。
- **接口文档 TBD-02（Session 有效期与续期策略）尚未关闭**：本阶段采用 Spring Boot 默认会话超时并在 README 记录当前值，**不**擅自宣称该策略已确认。
