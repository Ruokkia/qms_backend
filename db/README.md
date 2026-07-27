# 数据库初始化脚本

本目录用于在本地或测试环境初始化 QMS PostgreSQL 数据库。

- `ddl/00-qms-schema.sql`：从本机 `qms` 数据库导出的完整 `qms` schema，包含表、索引、约束、序列、视图和函数；不包含业务数据。
- `seed/00-qms-reference-data.sql`：从当前本机 `qms` 导出的参考数据快照，包含账号、角色、权限及各模块数据；不含 `audit_log` 与 `sys_login_log`。
- `legacy-seed/`：从原项目 `qms-db/seed` 保留的全部历史 Seed 脚本，用于追溯。

## 使用方式

请在空数据库中执行。创建数据库后，先导入结构，再导入与当前 schema 匹配的参考数据快照：

```powershell
$env:PGPASSWORD = '<password>'
& 'psql' -h 127.0.0.1 -p 5432 -U postgres -d qms -f db/ddl/00-qms-schema.sql
& 'psql' -h 127.0.0.1 -p 5432 -U postgres -d qms -f db/seed/00-qms-reference-data.sql
```

`legacy-seed/` 中的旧脚本与当前 schema 存在版本差异，不能作为一键初始化脚本；如需使用，请按模块单独审查和执行。重复导入参考数据前请先清空目标库，避免与已有数据冲突。
