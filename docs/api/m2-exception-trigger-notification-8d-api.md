# M2 异常触发 / 通知底座 / 8D / 供应商审核频次 接口文档

> 版本：V1.0  
> 日期：2026-07-17  
> Base URL：`/api/v1`  
> 状态：待确认（三端对齐基线）  
> 依赖：本文档基于 M0/M1/M2 API V1.0 扩展，新增/修改接口以 `*` 标注。

---

## 通用约定

与 M0/M1/M2 API V1.0 保持一致：

- 统一响应体：`{ code, message, data, timestamp, traceId }`，`code=0` 成功。
- 统一分页：`{ list, total, page, size }`。
- JWT 认证 + 分公司隔离（`plantCode`）。

## 枚举对齐基线（新增/复用）

| 枚举类别 | 取值 | 说明 |
|----------|------|------|
| `inspectionResult` | `合格` / `不合格` | 来料检验结果 |
| `severity` | `严重` / `一般` | 异常单默认 `一般` |
| `exceptionStatus` | `待整改` / `整改中` / `待验证` / `已闭环` | 异常单状态 |
| `sourceType` | `来料不良` / `制程不良` / `审核问题` / `客户投诉` / `重复问题` | 异常来源 |
| `capaStatus` | `待发起` / `进行中` / `已完成` | 8D/CAPA 状态（默认 `待发起`） |
| `notificationType` | `EXCEPTION_CREATED` / `EXCEPTION_STATUS_CHANGED` / `ESCALATION_TRIGGERED` | 通知类型 |
| `notificationBusinessType` | `EXCEPTION_ORDER` / `ESCALATION` | 通知关联业务 |
| `eightDStep` | `D1` / `D2` / `D3` / `D4` / `D5` / `D6` / `D7` / `D8` | 8D 步骤 |

---

## 第一篇：M1 来料数据导入与自动触发

> 背景：M1 不再走人工 `judge` 接口；`material_inspection.inspection_result=不合格` 落库时自动建 `exception_order`（来源类型=来料不良），同时发通知。

### m1-import-1 批量导入物料检验记录

```
POST /api/v1/material-inspections/import
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| list | array | 是 | 物料检验记录列表（结构与 `POST /api/v1/material-inspections` 单条一致） |
| autoCreateException | boolean | 否 | 是否自动建异常单，默认 `true` |

**请求示例：**

```json
{
  "list": [
    {
      "recordNo": "IQC-20260717-001",
      "inspectionResult": "不合格",
      "supplierCode": "S001",
      "supplierName": "深圳电子元件有限公司",
      "materialCode": "MAT-001",
      "materialName": "电阻",
      "materialBatchNo": "B-SZ-20260717-001",
      "submittedQty": 1000,
      "unqualifiedQty": 50,
      "defectDesc": "外观划痕"
    }
  ],
  "autoCreateException": true
}
```

**响应 data：**

| 字段 | 类型 | 说明 |
|------|------|------|
| totalCount | int | 导入总数 |
| successCount | int | 成功落库数 |
| failCount | int | 失败数 |
| failList | array | 失败明细：`{ index, recordNo, reason }` |
| createdExceptionCount | int | 本次自动创建异常单数 |
| createdExceptionIds | array | 自动创建的异常单ID列表 |

---

### m1-import-2 手动对账（兜底直写库/ETL）

```
POST /api/v1/material-inspections/reconcile
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| startDate | string | 否 | 对账起始日期 `yyyy-MM-dd`（默认 30 天前） |
| endDate | string | 否 | 对账截止日期（默认今天） |
| plantCode | string | 否 | R06 可指定，其他从 JWT 注入 |

**业务说明：**

- 扫描 `material_inspection` 中 `inspection_result='不合格'` 且未关联 `exception_order`（按 `source_type='来料不良' + source_id` 防重）的记录。
- 对每条命中记录自动建 `exception_order` + 发通知。

**响应 data：**

| 字段 | 类型 | 说明 |
|------|------|------|
| scannedCount | int | 扫描到的不合格记录数 |
| createdCount | int | 本次新建的异常单数 |
| createdExceptionIds | array | 新建异常单ID列表 |

---

## 第二篇：M2 异常单扩展

### m2-2-1 异常单字段扩展

`exception_order` 表新增两列：

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| reviewerId | long | 否 | 审核人/复核人ID（录入人指定） |
| capaStatus | string | 否 | 8D/CAPA 状态：待发起/进行中/已完成（默认 `待发起`） |

### m2-2-2 新增异常单（含 reviewerId / capaStatus）

```
POST /api/v1/exceptions
```

**请求体新增字段：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| reviewerId | long | 否 | 审核人ID |
| capaStatus | string | 否 | 默认 `待发起` |

其他字段与 M0/M1/M2 API V1.0 一致。

---

### m2-2-3 更新异常单（含 reviewerId / capaStatus）

```
PUT /api/v1/exceptions/{id}
```

**请求体新增字段：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| reviewerId | long | 否 | 审核人ID |
| capaStatus | string | 否 | 8D/CAPA 状态 |

**业务规则：**

- `status` / `closedAt` 仍不可通过本接口修改。
- `capaStatus` 在 8D 第一步保存后自动变为 `进行中`，8D 闭环后变为 `已完成`。

---

### m2-2-4 异常单详情扩展（* 修改）

```
GET /api/v1/exceptions/{id}
```

**响应 data 在原有基础上扩展：**

| 字段 | 类型 | 说明 |
|------|------|------|
| reviewerId | long | 审核人ID |
| reviewerName | string | 审核人姓名（连表填充） |
| capaStatus | string | 8D/CAPA 状态 |
| eightD | object | 关联 8D 报告对象（见第三篇） |
| notificationCount | int | 已发送通知数 |
| materialInspection | object | 关联来料检验记录（仅 sourceType=来料不良 时填充，字段同 material_inspection） |

---

## 第三篇：通知底座（Notification）

> 表名：`qms.notification`。站内信 + WebSocket 实时推送。异常单创建/状态变更/升级触发时自动写入。

### notif-1 查询当前用户通知列表

```
GET /api/v1/notifications?page=&size=&isRead=&businessType=&businessId=
```

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| page | int | 否 | 默认 1 |
| size | int | 否 | 默认 20 |
| isRead | int | 否 | 是否已读：0/1 |
| businessType | string | 否 | `EXCEPTION_ORDER` / `ESCALATION` |
| businessId | long | 否 | 业务ID |

**响应 data.list 元素：**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 通知ID |
| type | string | 通知类型 |
| title | string | 标题 |
| content | string | 内容 |
| businessType | string | 业务类型 |
| businessId | long | 业务ID |
| isRead | int | 0=未读，1=已读 |
| readAt | string | 读取时间 |
| createdAt | string | 发送时间 |
| plantCode | string | 分公司 |

---

### notif-2 未读通知数

```
GET /api/v1/notifications/unread-count
```

**响应 data：**

| 字段 | 类型 | 说明 |
|------|------|------|
| total | int | 未读总数 |
| byType | array | 按类型汇总：`{ type, count }` |

---

### notif-3 标记已读

```
POST /api/v1/notifications/{id}/read
```

**响应：** `R.ok()`

---

### notif-4 全部已读

```
POST /api/v1/notifications/read-all
```

**响应：** `R.ok()`

---

### notif-5 内部创建通知（仅后端调用，前端不直接调用）

```
POST /api/v1/notifications
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| userId | long | 是 | 接收人ID |
| type | string | 是 | 通知类型 |
| title | string | 是 | 标题 |
| content | string | 是 | 内容 |
| businessType | string | 否 | 业务类型 |
| businessId | long | 否 | 业务ID |

**响应 data：** 通知对象。

---

### notif-6 WebSocket 实时推送

- 连接：`wss://host/ws/notifications?token={jwt}`（或 `ws://` 开发环境）。
- 服务端在异常单创建/状态变更/升级触发时推送消息：`{ type: 'NOTIFICATION', data: { ... } }`。
- 前端收到后刷新未读数及通知列表。

---

## 第四篇：8D 报告（exception_8d）

> 表名：`qms.exception_8d`。1 个异常单对应 1 份 8D 报告（1:1）。保存第一步时自动将 `exception_order.capaStatus` 更新为 `进行中`。

### 8d-1 查询 8D 报告

```
GET /api/v1/exceptions/{exceptionId}/eight-d
```

**响应 data：**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 8D 报告ID |
| exceptionId | long | 异常单ID |
| currentStep | string | 当前步骤：D1-D8 |
| d1Team | string | D1 团队成立（成员/负责人） |
| d2ProblemDesc | string | D2 问题描述（5W2H） |
| d3Containment | string | D3 临时遏制措施 |
| d4RootCause | string | D4 根本原因分析 |
| d5Corrective | string | D5 纠正措施 |
| d6Implementation | string | D6 实施与验证 |
| d7Preventive | string | D7 预防措施 |
| d8Closure | string | D8 团队表彰/闭环总结 |
| plantCode | string | 分公司 |
| createdAt | string | 创建时间 |
| updatedAt | string | 更新时间 |
| createdBy | string | 创建人 |
| updatedBy | string | 更新人 |

---

### 8d-2 保存/更新 8D 报告

```
PUT /api/v1/exceptions/{exceptionId}/eight-d
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| currentStep | string | 是 | 当前步骤：D1-D8 |
| d1Team | string | 否 | D1 团队 |
| d2ProblemDesc | string | 否 | D2 问题描述 |
| d3Containment | string | 否 | D3 临时遏制 |
| d4RootCause | string | 否 | D4 根本原因 |
| d5Corrective | string | 否 | D5 纠正措施 |
| d6Implementation | string | 否 | D6 实施验证 |
| d7Preventive | string | 否 | D7 预防措施 |
| d8Closure | string | 否 | D8 闭环总结 |

**业务规则：**

- 首次保存时自动创建 `exception_8d` 记录，并将 `exception_order.capaStatus` 更新为 `进行中`。
- `currentStep` 只能前进或保持（不能回退），例如当前 D3 可更新为 D3/D4/D5…，不允许改为 D1/D2。
- 当 `currentStep=D8` 且保存成功后，若 `exception_order.status=已闭环`，则 `capaStatus` 更新为 `已完成`。

**响应 data：** 8D 报告对象。

---

### 8d-3 提交 8D 到下一步

```
POST /api/v1/exceptions/{exceptionId}/eight-d/next-step
```

**业务说明：**

- 将 `currentStep` 自动推进到下一步（D1→D2→…→D8）。
- 若已到达 D8，返回业务错误 `400`：已到达最后一步。

**响应 data：** 更新后的 8D 报告对象。

---

## 第五篇：供应商审核频次（下钻查看）

> 按供应商统计来料不良异常单次数（数据源统一为 `exception_order` 中 `source_type='来料不良'`）。前端点击“审核频次”后下钻展示该供应商所有发生记录。

### supplier-freq-1 供应商问题频次汇总

```
GET /api/v1/exceptions/supplier-summary?supplierId=&startDate=&endDate=&minCount=
```

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| supplierId | long | 否 | 供应商ID（不传则返回全部） |
| startDate | string | 否 | 起始日期 |
| endDate | string | 否 | 结束日期 |
| minCount | int | 否 | 最少发生次数过滤（默认 1） |
| page | int | 否 | 默认 1 |
| size | int | 否 | 默认 20 |

**响应 data.list 元素：**

| 字段 | 类型 | 说明 |
|------|------|------|
| supplierId | long | 供应商ID |
| supplierName | string | 供应商名称 |
| supplierCode | string | 供应商编号 |
| occurrenceCount | int | 来料不良异常单发生次数 |
| relatedExceptionIds | array | 关联异常单ID列表 |
| latestOccurrenceAt | string | 最近发生时间 |
| topDefectDesc | string | 最高频不良描述 |

---

### supplier-freq-2 下钻：指定供应商发生明细

复用现有接口：

```
GET /api/v1/exceptions?supplierId={supplierId}&sourceType=来料不良&startDate=&endDate=&page=&size=
```

返回该供应商所有来料不良异常单明细列表。

---

## 附录 A：配套数据模型说明（供数据库 Agent 参考）

### A.1 exception_order 扩展字段

```sql
ALTER TABLE qms.exception_order
    ADD COLUMN reviewer_id BIGINT,
    ADD COLUMN capa_status VARCHAR(20) DEFAULT '待发起';
COMMENT ON COLUMN qms.exception_order.reviewer_id IS '审核人/复核人ID';
COMMENT ON COLUMN qms.exception_order.capa_status IS '8D/CAPA状态：待发起/进行中/已完成';
```

### A.2 qms.notification

```sql
CREATE TABLE qms.notification (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    type            VARCHAR(64) NOT NULL,
    title           VARCHAR(256) NOT NULL,
    content         TEXT,
    business_type   VARCHAR(64),
    business_id     BIGINT,
    is_read         SMALLINT NOT NULL DEFAULT 0,
    read_at         TIMESTAMP,
    plant_code      VARCHAR(8) NOT NULL,
    plant_name      VARCHAR(32) NOT NULL,
    created_by      VARCHAR(64),
    updated_by      VARCHAR(64),
    is_deleted      SMALLINT NOT NULL DEFAULT 0,
    version         INTEGER NOT NULL DEFAULT 1,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_user_read ON qms.notification(user_id, is_read) WHERE is_deleted = 0;
CREATE INDEX idx_notification_business ON qms.notification(business_type, business_id) WHERE is_deleted = 0;
CREATE INDEX idx_notification_plant ON qms.notification(plant_code) WHERE is_deleted = 0;

COMMENT ON TABLE qms.notification IS '站内通知/消息中心';
```

### A.3 qms.exception_8d

```sql
CREATE TABLE qms.exception_8d (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    exception_id     BIGINT NOT NULL,
    current_step     VARCHAR(4) NOT NULL DEFAULT 'D1',
    d1_team          TEXT,
    d2_problem_desc  TEXT,
    d3_containment   TEXT,
    d4_root_cause    TEXT,
    d5_corrective    TEXT,
    d6_implementation TEXT,
    d7_preventive    TEXT,
    d8_closure       TEXT,
    plant_code       VARCHAR(8) NOT NULL,
    plant_name       VARCHAR(32) NOT NULL,
    created_by       VARCHAR(64),
    updated_by       VARCHAR(64),
    is_deleted       SMALLINT NOT NULL DEFAULT 0,
    version          INTEGER NOT NULL DEFAULT 1,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uq_exception_8d_exception_id UNIQUE (exception_id)
);

CREATE INDEX idx_exception_8d_exception_id ON qms.exception_8d(exception_id);
CREATE INDEX idx_exception_8d_plant ON qms.exception_8d(plant_code) WHERE is_deleted = 0;

COMMENT ON TABLE qms.exception_8d IS '8D/CAPA 报告（D1-D8）';
```

---

## 附录 B：状态码扩展

| code | 枚举 | 说明 |
|------|------|------|
| 2005 | IMPORT_RECORD_INVALID | 导入记录校验失败（字段缺失/格式错误） |
| 2006 | EIGHT_D_STEP_INVALID | 8D 步骤无法回退 |
| 2007 | EIGHT_D_ALREADY_CLOSED | 8D 已闭环 |

---

> **文档结束 · M2 异常触发/通知/8D/供应商频次 API V1.0（2026-07-17）**  
> 下一步：确认本接口文档 → 数据库 Agent 输出正式 DDL → 后端 Agent 实现接口 → 前端 Agent 对接页面。
