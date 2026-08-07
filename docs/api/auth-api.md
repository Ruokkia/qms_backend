# 认证模块接口文档

> 模块：UOP-Auth（用户认证）  
> 版本：1.0.0  
> 日期：2026-07-17  
> Base URL：`/api/v1/auth`

---

## 1. 通用约定

### 1.1 统一响应结构

```json
{
  "code": 0,
  "message": "登录成功",
  "data": { ... },
  "timestamp": "2026-07-17 10:30:00",
  "traceId": "a1b2c3d4e5f67890"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 0=成功，非0=失败（业务错误码 1001~1008） |
| message | string | 描述信息 |
| data | object/null | 业务数据 |
| timestamp | string | 服务器时间戳（yyyy-MM-dd HH:mm:ss） |
| traceId | string | 链路追踪ID（贯穿整条请求） |

### 1.2 认证机制

**双 Token 机制**：
- **Access Token**：JWT，有效期 2 小时（7200s），后续请求放入 `Authorization: Bearer <token>`
- **Refresh Token**：JWT，有效期 7 天（604800s），用于无感刷新 Access Token
- Access Token 过期 → 用 Refresh Token 静默刷新
- Refresh Token 过期 → 强制重新登录

**JWT Payload**（Access Token）：

```json
{
  "userId": 1,
  "account": "sz_op01",
  "roleCode": "R01",
  "plantCode": "SZ",
  "canSwitchArea": false,
  "type": "ACCESS",
  "iss": "kangli-qms",
  "iat": 1721188200,
  "exp": 1721195400
}
```

### 1.3 多站点数据隔离

- JWT payload 携带 `userId`、`roleCode`、`plantCode`、`canSwitchArea`
- 深圳（SZ）和梅州（MZ）共用一个数据库，通过 `plant_code` 字段做数据隔离
- **R06（质量经理）** 的 `canSwitchArea: true`，可以跨区查看
- 其他角色 `canSwitchArea: false`，仅能访问本分公司数据

### 1.4 密码安全

- BCrypt 加密存储，每次加盐不同，不可逆
- 种子数据统一密码为 `123456`（上线前须重置为强密码）

### 1.5 Redis 存储三项状态

| 用途 | Redis Key | TTL | 说明 |
|------|-----------|-----|------|
| Refresh Token | `qms:auth:refresh:{token}` | 7天 | value=userId，登出时主动删除 |
| 登录失败计数 | `qms:auth:fail:{account}` | 15分钟 | 滚动窗口，达到5次触发锁定 |
| 账号锁定标记 | `qms:auth:lock:{account}` | 30分钟 | TTL到期自动解锁 |
| Token 黑名单 | `qms:auth:blacklist:{token}` | Token剩余有效期 | 登出时写入，主动失效 |

---

## 2. 接口列表

### 2.1 登录

```
POST /api/v1/auth/login
```

**请求体：**

```json
{
  "account": "sz_op01",
  "password": "123456",
  "plantCode": "SZ"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| account | string | 是 | 登录账号 |
| password | string | 是 | 密码 |
| plantCode | string | 是 | 分公司编码：`SZ`=深圳 / `MZ`=梅州 |

**成功响应（code=0）：**

```json
{
  "code": 0,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenExpireIn": 7200,
    "userInfo": {
      "userId": 1,
      "account": "sz_op01",
      "realName": "张三",
      "roleCode": "R01",
      "roleName": "操作工",
      "plantCode": "SZ",
      "plantName": "深圳",
      "status": 1
    }
  },
  "timestamp": "2026-07-17 10:30:00",
  "traceId": "a1b2c3d4e5f67890"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| data.token | string | JWT Access Token，2h 有效期 |
| data.refreshToken | string | JWT Refresh Token，7d 有效期 |
| data.tokenExpireIn | long | Access Token 过期时间（秒），7200 |
| data.userInfo.userId | long | 用户主键ID |
| data.userInfo.account | string | 登录账号 |
| data.userInfo.realName | string | 真实姓名 |
| data.userInfo.roleCode | string | 角色编码 R01~R06 |
| data.userInfo.roleName | string | 角色中文名称 |
| data.userInfo.plantCode | string | 分公司编码 SZ/MZ |
| data.userInfo.plantName | string | 分公司名称 |
| data.userInfo.status | short | 状态 1=启用 0=禁用 |

**失败响应：**

| code | message | 触发条件 |
|------|---------|----------|
| 1001 | 账号或密码错误 | 账号不存在 / 密码不匹配 |
| 1002 | 账号已锁定，请 N 分钟后重试 | 连续5次失败触发锁定（30分钟） |
| 1003 | 账号已禁用 | status=0 |
| 1004 | 分公司不匹配 | 用户所属分公司与请求 plantCode 不一致 |
| 400 | 分公司编码非法，仅支持 SZ/MZ | plantCode 非 SZ/MZ |

---

### 2.2 刷新 Token

```
POST /api/v1/auth/refresh
```

> 无需携带 `Authorization` 头，通过请求体传递 refreshToken。

**请求体：**

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**成功响应：** 与登录成功响应结构一致，返回新的 `token` + `refreshToken`。  
> 滚动刷新策略：旧的 refreshToken 即时失效。

**失败响应：**

| code | message | 触发条件 |
|------|---------|----------|
| 1005 | Refresh Token无效或已过期 | token 不存在 / 已过期 / 已被使用 |
| 1003 | 账号已禁用 | 用户 status=0 |

---

### 2.3 退出登录

```
POST /api/v1/auth/logout
```

**请求头：** `Authorization: Bearer <token>`（必填）

**说明：**
- 服务端将当前 Access Token 加入 Redis 黑名单（TTL=剩余有效期）
- 后续携带该 Token 的请求返回 401
- 同时失效该用户对应的 Refresh Token

**成功响应：**

```json
{
  "code": 0,
  "message": "已退出登录",
  "data": null,
  "timestamp": "2026-07-17 12:00:00",
  "traceId": "b2c3d4e5f6789012"
}
```

---

### 2.4 获取当前用户信息

```
GET /api/v1/auth/me
```

**请求头：** `Authorization: Bearer <token>`（必填）

**成功响应：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": 1,
    "account": "sz_op01",
    "realName": "张三",
    "roleCode": "R01",
    "roleName": "操作工",
    "plantCode": "SZ",
    "plantName": "深圳",
    "status": 1,
    "lastLoginAt": "2026-07-17 10:30:00"
  },
  "timestamp": "2026-07-17 10:35:00",
  "traceId": "c3d4e5f678901234"
}
```

---

## 3. 账号锁定规则

| 参数 | 值 | 说明 |
|------|-----|------|
| 最大失败次数 | 5 次 | 15 分钟窗口内连续失败计数 |
| 失败窗口 | 15 分钟 | 滚动窗口，由 Redis 控制 |
| 锁定时长 | 30 分钟 | 到达最大失败次数后自动锁定 |
| 锁定存储 | Redis（主控）+ sys_user.locked_until（兜底） | Redis 不可用时降级到 DB |

所有登录失败均写入 `qms.sys_login_log` 表，`login_status='失败'`，`fail_reason` 记录具体原因。

---

## 4. 错误码汇总

| code | 枚举 | 说明 |
|------|------|------|
| 0 | SUCCESS | 成功 |
| 400 | BAD_REQUEST | 请求参数错误 |
| 401 | UNAUTHORIZED | 未认证或Token已失效 |
| 403 | FORBIDDEN | 无访问权限 |
| 404 | NOT_FOUND | 资源不存在 |
| 500 | INTERNAL_ERROR | 服务器内部错误 |
| 1001 | ACCOUNT_OR_PASSWORD_ERROR | 账号或密码错误 |
| 1002 | ACCOUNT_LOCKED | 账号已锁定 |
| 1003 | ACCOUNT_DISABLED | 账号已禁用 |
| 1004 | PLANT_CODE_MISMATCH | 分公司不匹配 |
| 1005 | REFRESH_TOKEN_INVALID | Refresh Token无效或已过期 |
| 1006 | CAPTCHA_ERROR | 验证码错误或已过期 |
| 1007 | TOKEN_INVALID | Token无效 |
| 1008 | TOKEN_EXPIRED | Token已过期 |

---

## 5. 种子账号列表

| 分公司 | 账号 | 姓名 | 角色编码 | 角色名称 | 密码 |
|--------|------|------|----------|----------|------|
| SZ 深圳 | sz_op01 | 张三 | R01 | 操作工 | 123456 |
| SZ 深圳 | sz_insp01 | 李四 | R02 | 检验员 | 123456 |
| SZ 深圳 | sz_lead01 | 王五 | R03 | 班组长 | 123456 |
| SZ 深圳 | sz_qe01 | 赵六 | R04 | 质量工程师 | 123456 |
| SZ 深圳 | sz_sqe01 | 钱七 | R05 | SQE供应商质量 | 123456 |
| SZ 深圳 | sz_mgr01 | 孙八 | R06 | 质量经理（可切换分公司） | 123456 |
| MZ 梅州 | mz_op01 | 陈一 | R01 | 操作工 | 123456 |
| MZ 梅州 | mz_insp01 | 周二 | R02 | 检验员 | 123456 |
| MZ 梅州 | mz_lead01 | 吴三 | R03 | 班组长 | 123456 |
| MZ 梅州 | mz_qe01 | 郑四 | R04 | 质量工程师 | 123456 |
| MZ 梅州 | mz_sqe01 | 冯五 | R05 | SQE供应商质量 | 123456 |
| MZ 梅州 | mz_mgr01 | 褚六 | R06 | 质量经理（可切换分公司） | 123456 |
