# qms-backend

康立质量管理系统（QMS）后端工程。

## 技术栈
- Spring Boot 2.7
- MyBatis-Plus 3.5
- PostgreSQL（驱动，SQL 由 `qms-db` 模块维护，不使用 MySQL 语法）
- JWT（登录鉴权）
- Swagger / Knife4j（接口文档）

## 规范约束
- 严格遵循 `D:\CodeBuddy_project\QMS-代码规范文档-V1.0.md`
- 后端**不写页面**、**不做物理删除**（统一逻辑删除 `deleted`）、**不直接返回实体类**（返回 VO/DTO）
- 每个接口必须输出 Swagger 注解（`@ApiOperation` / `@ApiParam`）并同步输出 Markdown 接口文档
- 固化工序/参数/CPK 公式/分公司编码见 `QMS-多智能体开发提示词-V1.0.md` 6.3，不得修改

## 目录结构
```
src/main/java/com/kangli/qms/
├── config/     # MyBatis-Plus / JWT / Swagger 配置
├── controller/ # 控制器（各模块 M0~M8）
├── service/    # 业务接口与实现
├── mapper/     # MyBatis-Plus Mapper
├── entity/     # 实体（仅映射表）
├── vo/ dto/    # 视图对象 / 数据传输对象
├── enums/      # 枚举（工序/不良类型/SPC 类型等，值来自 DB 配置）
└── QmsApplication.java
```

## 启动
```bash
mvn spring-boot:run
```

## 打包与运行

```bash
mvn clean package
java -jar target/qms-backend-1.0.0.jar
```

打包命令会执行项目测试，并生成可独立运行的 Spring Boot JAR。运行前请按 `src/main/resources/application.yml` 配置 PostgreSQL 与 Redis 连接信息。
