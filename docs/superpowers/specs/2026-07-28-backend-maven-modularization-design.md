# 后端 Maven 多模块改造设计

## 目标与边界

本次仅改造 `cornley-qms-server`。`cornley-qms-web` 保持现状，不移动、不修改代码，也不变更其 Git 仓库。

后端从单 Maven 模块改造成一个可独立部署的 Maven 多模块（模块化单体）：运行时仍只有一个 Spring Boot 进程、一个数据库连接配置和一套 Flyway 迁移。它不拆分微服务，也不改变现有 HTTP 接口、数据库表结构或业务流程。

当前后端 Git 仓库继续以 `cornley-qms-server` 为根目录；本次不合并前端与后端的 Git 历史，也不改远程仓库。

## 目标目录

```text
cornley-qms-server/
├── pom.xml                         # reactor 父 POM：版本、依赖与模块声明
├── qms-common/                     # 与业务无关的公共能力
├── qms-domain/                     # 实体、Mapper、Repository
├── qms-service/                    # 业务服务及其实现
├── qms-api/                        # Controller、请求/响应 DTO、VO
├── qms-bootstrap/                  # Spring Boot 启动、配置、安全、资源
├── db/migrations/                  # Flyway SQL 的唯一源码位置
└── docs/
```

Java 基础包保持为 `com.kangli.qms`，不使用 `com.konli.qms`。

## Maven 依赖方向

```text
qms-common  <-  qms-domain  <-  qms-service  <-  qms-api  <-  qms-bootstrap
```

- `qms-common`：不依赖其他业务模块。
- `qms-domain`：依赖 `qms-common`，承载与数据库直接对应的类型。
- `qms-service`：依赖 `qms-domain` 和 `qms-common`，承载事务与业务编排。
- `qms-api`：依赖 `qms-service` 和 `qms-common`，暴露 REST 接口。
- `qms-bootstrap`：依赖所有业务模块，承载 Spring Boot 启动类、配置、安全组件以及运行资源；最终仅该模块打包为可运行 JAR。

不允许反向依赖：domain 不调用 service/api，service 不调用 controller，common 不依赖任何业务模块。

## 代码迁移规则

| 当前目录 | 目标模块 | 说明 |
| --- | --- | --- |
| `common/`、通用 `util/`、通用 `enums/` | `qms-common` | `R`、业务异常、基础实体、审计能力、跨模块工具与枚举 |
| `entity/`、`mapper/` | `qms-domain` | 按业务域子包组织：`{module}/entity`、`{module}/mapper`、`{module}/repository` |
| `service/` | `qms-service` | 按业务域组织接口、实现与模块内部 DTO |
| `controller/`、对外 DTO、`vo/` | `qms-api` | 按业务域组织 Controller、请求响应 DTO 与 VO |
| `config/`、`security/`、`QmsApplication` | `qms-bootstrap` | 组装应用、Web/JWT/Redis/MyBatis/Swagger/Flyway 配置 |
| `src/main/resources/mapper/` | `qms-domain` 资源 | 保持 Mapper XML 与 Mapper 接口同一模块 |
| `application.yml` | `qms-bootstrap` 资源 | 最终运行配置 |

建议采用以下业务域子包，而非继续所有 Controller/Service/Mapper 平铺：

- `system`：认证、账号、权限、菜单、通知、审计；
- `incoming`：供应商、来料检验、关键物料绑定、来料追溯；
- `quality`：异常、升级、改善、纠正、验证、8D；
- `fai`：首件检验、首件标准与首件变更触发；
- `spc`：SPC 过程、参数、子组、图表、能力分析；
- `production`：生产不良分析、返修；
- `finishedgoods`：成品检验。

包移动不改变 Java 完整类名以外的公开 API 语义；所有 import、MyBatis 扫描路径和 XML namespace 将同步更新。

## 数据库与 Flyway

- 已发布的 `V20260727161504201__baseline_schema.sql`、`V20260727161504202__reference_seed_data.sql` 保持字节内容不变，避免 Flyway 校验和失效。
- 将迁移脚本保留为仓库根目录的 `db/migrations/` 源文件，并在 `qms-bootstrap` 构建时作为 classpath `db/migration` 资源打包。
- `application.yml` 的 Flyway `locations` 继续使用 `classpath:db/migration`。
- 后续数据库变更只新增带时间戳的迁移文件，不修改已发布文件。

## 分阶段实施与验证

1. 建立父 POM 与五个子模块 POM，集中版本和依赖。
2. 先移动 `common` 与 domain 类型、Mapper XML，修正 MyBatis 扫描和依赖。
3. 迁移 service，再迁移 api，最后迁移 bootstrap 及 resources。
4. 保留现有包名根 `com.kangli.qms`，按域完成包调整并修复编译引用。
5. 执行 `mvn clean test`；构建 `qms-bootstrap` 可执行 JAR；以临时数据库验证 Flyway 的 baseline 与 seed 可正常启动。
6. 不在本次变更中提交、推送或修改前端；任何 Git push 都在展示理由、文件和影响后等待明确授权。

## 风险与回滚

- 最大风险是移动类型后出现遗漏 import、Spring 扫描范围或 MyBatis XML namespace 不一致；由全量编译、测试和启动验证覆盖。
- Flyway 资源路径改变可能导致启动时找不到迁移；以最终 JAR 的 `BOOT-INF/classes/db/migration` 内容和临时数据库启动验证。
- Git 文件移动量较大但不涉及数据库数据变更。若需要回滚，使用一个独立重构提交整体回退即可。
