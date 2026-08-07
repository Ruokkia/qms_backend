# M0/M1/M2 业务模块接口文档

> 版本：V1.0
> 日期：2026-07-17
> Base URL：`/api/v1`
> 状态：待确认（三端对齐基线）

---

## 通用约定

### 统一响应结构

```json
{
  "code": 0,
  "message": "success",
  "data": { ... },
  "timestamp": "2026-07-17 10:30:00",
  "traceId": "a1b2c3d4e5f67890"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 0=成功，非0=失败 |
| message | string | 描述信息 |
| data | object/null | 业务数据 |
| timestamp | string | 服务器时间戳 |
| traceId | string | 链路追踪ID |

### 统一分页

请求参数：`page`（默认1）、`size`（默认20，≤100）、`plantCode`（R06允许指定，其他从JWT注入）、`keyword`、`status`、`startDate`、`endDate`

响应结构：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [...],
    "total": 100,
    "page": 1,
    "size": 20
  }
}
```

### 认证鉴权

所有业务接口须携带 `Authorization: Bearer <token>`。分公司数据隔离由后端 MyBatis-Plus 拦截器自动注入 `plant_code` 过滤。仅 R06（质量经理）可切换分公司查询。

### 枚举对齐基线（摘自全局约束 §0.9，三端共用）

| 枚举类别 | 取值 | 说明 |
|----------|------|------|
| `plantCode` | `SZ`/`MZ` | 深圳/梅州 |
| `reviewStatus` | `待审核`/`已审核`/`驳回` | 审核状态（中文 VARCHAR） |
| `signatureStatus` | `已签`/`未签` | 签名状态 |
| `handlingMethod` | `退货`/`挑选`/`特采`/`报废` | 物料处理方式 |
| `inspectionResult` | `合格`/`不合格` | 检验结果 |
| `booleanFlag` | `是`/`否` | 是否类字段 |
| `severity` | `严重`/`一般` | 异常等级 |
| `exceptionStatus` | `待整改`/`整改中`/`待验证`/`已闭环` | 异常单状态 |
| `actionType` | `临时措施`/`纠正措施`/`预防措施` | 改善措施类型（8D D3/D4/D5对应） |
| `verifyType` | `供应商自证`/`内部确认`/`连续N批` | 验证方式 |
| `verifyResult` | `通过`/`不通过` | 验证结果 |
| `escalationStatus` | `ACTIVE`/`CLOSED` | 升级状态（保留英文） |
| `auditOperationType` | `CREATE`/`UPDATE`/`DELETE` | 审计操作类型（保留英文） |
| `nodeType` | `SN`/`部件`/`关键物料`/`非关键物料`/`来料批次`/`生产批次` | 追溯节点类型 |
| `iqcStatus` | `待检`/`在检`/`已检`/`异常` | IQC 检验状态 |

### M2 异常整改：发起流程与人员选择规则

> 2026-08-04 更新

- **发起整改流程类型由人员手动选择**：调用 `POST /api/v1/exceptions/{id}/initiate`（请求体 `processType` 取 `CAPA`/`8D`/`BOTH`）时，由相关整改人员主动选择 8D / CAPA / 8D+CAPA。系统自动判定（`QualityExceptionRuleEvaluator` 依据严重度）仅作为列表页「自动判定依据」建议展示，不强制覆盖人员选择。仅 `待发起`（capaStatus=待发起、status=待发起）状态可发起。
- **发起整改记录责任人**：发起成功后，`qms.exception_order` 的 `initiated_by`（发起人姓名）与 `initiated_at`（发起时间）被写入当前登录用户，与 `createdBy`/`updatedBy` 区分，用于审计「谁发起整改」。详情接口 `GET /api/v1/exceptions/{id}` 返回 VO 含上述字段。
- **改善措施负责人 / 验证人为可配置选择**：新增或编辑改善措施（`POST/PUT /api/v1/improvement-actions`）、新增验证记录（`POST /api/v1/verification-records`）时，**禁止自由填写**负责人/验证人姓名与 ID。前端从 `GET /api/v1/admin/users`（返回含 `id`/`realName`/`account`/`roleCode`/`plantCode` 的 `AdminUserVO`）拉取人员列表，按当前登录人 `plantCode` 过滤后以下拉供选择，选中即联动回填 `ownerId`/`ownerName`（改善措施）与 `verifierId`/`verifierName`（验证），保证 ID 与姓名一致。
- **权限放宽说明**：`GET /api/v1/admin/users` 原需 `systemAdmin` 模块权限，现对登录态的 M2 整改相关人员（R03/R04/R05/R06，R00 绕过）放行只读查询，其余 admin 写操作仍受 `systemAdmin` 约束，权限面最小。

---

## 第一篇：M0 全链路追溯底座

### m0-1 审计日志查询

```
GET /api/v1/audit-logs?page=1&size=20&tableName=&recordId=&operatorId=&plantCode=&operationType=&startTime=&endTime=
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| page | int | 否 | 页码（默认1） |
| size | int | 否 | 每页条数（默认20） |
| tableName | string | 否 | 操作表名 |
| recordId | long | 否 | 记录ID |
| operatorId | long | 否 | 操作人ID |
| plantCode | string | 否 | 分公司编码 |
| operationType | string | 否 | 操作类型：CREATE/UPDATE/DELETE |
| startTime | string | 否 | 起始时间 yyyy-MM-dd HH:mm:ss |
| endTime | string | 否 | 截止时间 |

**响应 data.list 元素：**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 日志ID |
| tableName | string | 操作表名 |
| recordId | long | 行记录ID |
| operationType | string | CREATE/UPDATE/DELETE |
| beforeData | object | 变更前数据(JSONB，UPDATE/DELETE时有值) |
| afterData | object | 变更后数据(JSONB，CREATE/UPDATE时有值) |
| operatorId | long | 操作人ID |
| operatorName | string | 操作人姓名 |
| plantCode | string | 分公司编码 |
| ipAddress | string | 操作IP |
| operationTime | string | 操作时间 |
| reason | string | 操作原因/备注 |

---

## 第二篇：M1 来料数据管理

### m1-1 物料检验入库（material_inspection）

```
POST   /api/v1/material-inspections            新增
GET    /api/v1/material-inspections?page=&size=&plantCode=&keyword=&reviewStatus=&inspectionResult=&supplierCode=&startDate=&endDate=    分页列表
GET    /api/v1/material-inspections/{id}       详情
PUT    /api/v1/material-inspections/{id}       更新
DELETE /api/v1/material-inspections/{id}       逻辑删除
```

**请求体（创建/更新）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| isCustomerSupplied | string | 否 | 是否客供料：是/否（默认否） |
| isUrgent | string | 否 | 是否急料：是/否（默认否） |
| recordNo | string | 是 | 记录编号（唯一，自动生成规则见业务规则） |
| purchaseOrder | string | 否 | 采购订单 |
| inboundNo | string | 否 | 入库单号 |
| inspectionRequestNo | string | 否 | 送检单号 |
| mesInspectionNo | string | 否 | MES检验单号 |
| inspectionDate | string | 否 | 检验日期 yyyy-MM-dd |
| judgementDate | string | 否 | 判定日期 |
| inspector | string | 否 | 检验人员 |
| inspectionResult | string | 否 | 检验结果：合格/不合格 |
| supplierName | string | 否 | 供应商名称 |
| supplierCode | string | 否 | 供应商编号 |
| materialCode | string | 否 | 物料代码 |
| materialName | string | 否 | 物料名称 |
| specModel | string | 否 | 规格型号 |
| materialBatchNo | string | 否 | 物料批号（追溯核心键） |
| qualifiedQty | decimal | 否 | 合格数量 |
| unqualifiedQty | decimal | 否 | 不合格数量 |
| submittedQty | decimal | 否 | 送检数量 |
| lossQty | decimal | 否 | 损耗数量 |
| unit | string | 否 | 单位 |
| defectDesc | text | 否 | 不合格描述（自由文本，不做下拉框枚举） |
| handlingMethod | string | 否 | 处理方式：退货/挑选/特采/报废 |
| unqualifiedFinalStatus | string | 否 | 不合格最终状态 |
| unqualifiedReview | string | 否 | 不合格评审 |
| unqualifiedReviewNo | string | 否 | 不合格评审单号 |
| inspectionCategory | string | 否 | 检验类别 |
| arrivalDate | string | 否 | 来料日期 |
| receivingNo | string | 否 | 收料单号 |
| poLineNo | string | 否 | 采购订单行号 |
| receivingLineNo | string | 否 | 收料单行号 |
| shelfLifeDays | int | 否 | 保质期天 |
| reinspectRemark | text | 否 | 复检备注 |
| judge | string | 否 | 判定人 |
| inspectionEndDate | string | 否 | 检验结束日期 |
| reviewStatus | string | 否 | 审核状态：待审核/已审核/驳回（默认待审核） |
| signatureStatus | string | 否 | 签名状态：已签/未签（默认未签） |
| reviewer | string | 否 | 审核人 |
| reviewDate | string | 否 | 审核日期 |
| submitter | string | 否 | 送检人 |
| submitDate | string | 否 | 送检日期 |
| memo | text | 否 | 备注 |
| remark | text | 否 | 扩展备注 |

**响应 data（含所有字段 + 系统列）：**

数据形状与请求体一致，追加：`id`, `plantCode`, `plantName`, `createdBy`, `createdAt`, `updatedBy`, `updatedAt`, `version`

---

### m1-2 物料检验看板统计

```
GET /api/v1/material-inspections/stats?plantCode=&startDate=&endDate=
```

**响应 data：**

| 字段 | 类型 | 说明 |
|------|------|------|
| totalBatches | int | 总批次数 |
| qualifiedBatches | int | 合格批次数 |
| unqualifiedBatches | int | 不合格批次数 |
| qualifiedRate | decimal | 合格率（%） |
| pendingReviewCount | int | 待审核数 |
| urgentCount | int | 急料数 |
| topDefectDesc | array | 高频不合格描述 TOP10：[{defectDesc, count}]（按 defec_desc GROUP BY） |
| supplierRank | array | 供应商合格率排名：[{supplierName, supplierCode, totalBatches, passRate}] |
| dailyTrend | array | 日统计趋势：[{date, totalBatches, passRate}] |

---

### m1-3 关键物料绑定（critical_material_binding）

```
POST   /api/v1/material-bindings
GET    /api/v1/material-bindings?page=&size=&plantCode=&workOrderNo=&productBarcode=&materialCode=&processCode=
GET    /api/v1/material-bindings/{id}
PUT    /api/v1/material-bindings/{id}
DELETE /api/v1/material-bindings/{id}
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| category | string | 否 | 分类 |
| workOrderNo | string | 是 | 工单号 |
| productBarcode | string | 是 | 产品条码/SN |
| productMaterialNo | string | 否 | 产品料号 |
| productName | string | 否 | 产品名称 |
| workOrderQty | decimal | 否 | 工单数量 |
| materialBarcode | string | 否 | 物料条码 |
| materialCode | string | 是 | 物料代码 |
| materialName | string | 否 | 物料名称 |
| specModel | string | 否 | 规格型号 |
| scanner | string | 否 | 扫描人 |
| scanTime | string | 否 | 扫描时间 |
| processCode | string | 否 | 工序编码 |
| processName | string | 否 | 工序名称（仅装配/焊接/检测三类） |
| isActive | string | 否 | 是否生效：是/否（默认是） |
| deactivateOperator | string | 否 | 失效操作人 |
| deactivateTime | string | 否 | 失效时间 |
| remark | text | 否 | 备注 |

---

### m1-4 成品入库检验（finished_goods_inspection）

```
POST   /api/v1/finished-goods                   新增（含电子签名预留）
GET    /api/v1/finished-goods?page=&size=&plantCode=&keyword=&inspectionResult=&qcReview=&mgrApproval=&startDate=&endDate=    分页列表
GET    /api/v1/finished-goods/{id}              详情
PUT    /api/v1/finished-goods/{id}              更新
DELETE /api/v1/finished-goods/{id}              逻辑删除
```

**请求体（核心字段）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| isUrgent | string | 否 | 是否加急：是/否（默认否） |
| qcReview | string | 否 | 品管审核：待审核/已审核/驳回（默认待审核） |
| mgrApproval | string | 否 | 管代批准：待审核/已审核/驳回（默认待审核） |
| isValid | string | 否 | 是否有效：是/否（默认是） |
| inspectionResult | string | 否 | 检验结果：合格/不合格 |
| reportNo | string | 是 | 报告编号（唯一） |
| inspectionRequestNo | string | 否 | 送检单号 |
| productionOrderNo | string | 否 | 生产订单号 |
| materialCode | string | 否 | 物料编码 |
| productName | string | 否 | 产品名称 |
| modelSpec | string | 否 | 型号规格 |
| prodBatchOrSn | string | 否 | 生产批号或产品编号 |
| productionDate | string | 否 | 生产日期 |
| expiryDate | string | 否 | 有效期至 |
| submittedQty | decimal | 否 | 送检数量 |
| inspectedQty | decimal | 否 | 检验数量 |
| qualifiedQty | decimal | 否 | 合格数量 |
| unqualifiedQty | decimal | 否 | 不合格数量 |
| unit | string | 否 | 单位 |
| inspectorName | string | 否 | 检验名字 |
| category | string | 否 | 分类 |
| qcReviewer | string | 否 | 品管复核人 |
| qcReviewTime | string | 否 | 品管复核时间 |
| mgrRepresentative | string | 否 | 管代 |
| mgrApprovalTime | string | 否 | 管代批准时间 |
| isEntrusted | string | 否 | 是否委托：是/否（默认否） |
| drugRegNo | string | 否 | 药监批号 |
| perfTestMethod | string | 否 | 性能检验方式 |
| perfSampleBatchNo | string | 否 | 性能抽检批次编号 |
| signatureUser | string | 否 | 签名人（电子签名） |
| signatureTime | string | 否 | 签名时间 |
| signatureReason | string | 否 | 签名原因 |

---

### m1-5 生产维修记录（production_repair）

```
POST   /api/v1/production-repairs
GET    /api/v1/production-repairs?page=&size=&plantCode=&keyword=&repairStatus=&defectCode=&process=&startDate=&endDate=
GET    /api/v1/production-repairs/{id}
PUT    /api/v1/production-repairs/{id}
DELETE /api/v1/production-repairs/{id}
```

**请求体（核心字段）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| repairNo | string | 是 | 维修编号（唯一） |
| productNo | string | 否 | 产品编号 |
| productName | string | 否 | 产品名称 |
| specModel | string | 否 | 规格型号 |
| workOrderNo | string | 否 | 生产工单号 |
| productBatchOrSn | string | 否 | 产品批号/序列号 |
| process | string | 否 | 生产工序 |
| equipment | string | 否 | 生产设备 |
| operator | string | 否 | 当班操作员 |
| defectQty | decimal | 否 | 不良数量 |
| defectPhenomenon | text | 否 | 不良现象 |
| defectCode | string | 否 | 不良代码 |
| sendRepairDate | string | 否 | 送修日期 |
| repairDate | string | 否 | 维修日期 |
| repairJudgmentResult | string | 否 | 维修判定结果 |
| repairStatus | string | 否 | 维修状态 |
| repairRecord | text | 否 | 维修记录 |
| sendRepairer | string | 否 | 送修人 |
| repairer | string | 否 | 维修人 |
| auditor | string | 否 | 审核人 |
| auditStatus | string | 否 | 审核状态：待审核/已审核/驳回 |
| auditDate | string | 否 | 审核日期 |
| remark | text | 否 | 备注 |

---

## 第三篇：M2 异常与整改

### m2-1 供应商基础（supplier）

```
POST   /api/v1/suppliers
GET    /api/v1/suppliers?page=&size=&plantCode=&keyword=
GET    /api/v1/suppliers/{id}
PUT    /api/v1/suppliers/{id}
DELETE /api/v1/suppliers/{id}
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| supplierCode | string | 是 | 供应商编号（唯一） |
| supplierName | string | 是 | 供应商名称 |
| contactPerson | string | 否 | 联系人 |
| contactPhone | string | 否 | 联系电话 |
| address | string | 否 | 地址 |
| riskLevel | string | 否 | 风险等级：高/中/低 |
| status | string | 否 | 状态：启用/停用（默认启用） |
| remark | text | 否 | 备注 |

---

### m2-2 异常单（exception_order）

```
POST   /api/v1/exceptions                        新增异常单
GET    /api/v1/exceptions?page=&size=&plantCode=&severity=&status=&supplierId=&sourceType=&startDate=&endDate=    分页列表
GET    /api/v1/exceptions/{id}                   详情（含关联改善措施+验证记录）
PUT    /api/v1/exceptions/{id}                   更新
DELETE /api/v1/exceptions/{id}                   逻辑删除
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| exceptionNo | string | 是 | 异常单号（唯一，自动生成） |
| sourceType | string | 是 | 来源：来料不良/制程不良/审核问题/客户投诉/重复问题 |
| sourceId | long | 否 | 来源记录ID（关联 material_inspection/production_repair 等） |
| severity | string | 是 | 严重等级：严重/一般 |
| status | string | 否 | 状态：待整改/整改中/待验证/已闭环（默认待整改） |
| supplierId | long | 否 | 供应商ID |
| workOrderId | long | 否 | 关联工单ID |
| materialCode | string | 否 | 物料代码 |
| defectDesc | text | 是 | 不良描述（自由文本，不做下拉框） |
| defectQty | decimal | 否 | 不良数量 |
| totalQty | decimal | 否 | 总数量 |
| handlerId | long | 否 | 处理人ID |
| deadline | string | 否 | 整改截止日期 |
| closedAt | string | 否 | 闭环时间 |
| remark | text | 否 | 备注 |

**详情响应（含关联数据）：**

扩展字段 `improvementActions` 和 `verificationRecords` 作为子数组返回。

---

### m2-3 改善措施（improvement_action）

```
POST   /api/v1/improvement-actions               新增措施
GET    /api/v1/improvement-actions?exceptionId=&page=&size=    按异常单查询
GET    /api/v1/improvement-actions/{id}          详情
PUT    /api/v1/improvement-actions/{id}          更新
DELETE /api/v1/improvement-actions/{id}          逻辑删除
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| exceptionId | long | 是 | 关联异常单ID |
| actionType | string | 是 | 措施类型：临时措施/纠正措施/预防措施 |
| content | text | 是 | 措施内容 |
| ownerId | long | 是 | 责任人ID |
| ownerName | string | 否 | 责任人姓名 |
| dueDate | string | 否 | 截止日期 |
| status | string | 否 | 状态：PENDING/DONE（默认PENDING） |
| completedAt | string | 否 | 完成时间 |
| remark | text | 否 | 备注 |

---

### m2-4 验证记录（verification_record）

```
POST   /api/v1/verification-records              新增验证
GET    /api/v1/verification-records?exceptionId=&page=&size=    按异常单查询
GET    /api/v1/verification-records/{id}         详情
PUT    /api/v1/verification-records/{id}         更新
DELETE /api/v1/verification-records/{id}         逻辑删除
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| exceptionId | long | 是 | 关联异常单ID |
| verifyType | string | 是 | 验证方式：供应商自证/内部确认/连续N批 |
| result | string | 否 | 验证结果：通过/不通过 |
| verifierId | long | 否 | 验证人ID |
| verifierName | string | 否 | 验证人姓名 |
| verifyDate | string | 否 | 验证日期 |
| evidence | text | 否 | 验证证据（附件URL或描述） |
| remark | text | 否 | 备注 |

---

### m2-5 异常升级（escalation）

```
POST   /api/v1/escalations                       发起升级
GET    /api/v1/escalations?page=&size=&plantCode=&status=&supplierId=    分页列表
GET    /api/v1/escalations/{id}                  详情
PUT    /api/v1/escalations/{id}                  更新状态
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| supplierId | long | 是 | 供应商ID |
| escalationReason | string | 是 | 升级原因（如：90天内同类不良≥3次） |
| relatedExceptionIds | string | 否 | 关联异常单ID列表（逗号分隔） |
| escalationAction | string | 是 | 升级动作：加密审核/暂停供货/专项CAPA |
| status | string | 否 | 状态：ACTIVE/CLOSED（默认ACTIVE） |
| closedAt | string | 否 | 关闭时间 |
| remark | text | 否 | 备注 |

---

### m2-6 异常统计分析

```
GET /api/v1/exceptions/stats?plantCode=&startDate=&endDate=     KPI看板
GET /api/v1/exceptions/analysis?plantCode=&startDate=&endDate=&dimension=defectDesc    多维度分析
```

**stats 响应 data：**

| 字段 | 类型 | 说明 |
|------|------|------|
| totalExceptions | int | 异常总数 |
| pendingCount | int | 待整改数 |
| inProgressCount | int | 整改中数 |
| pendingVerifyCount | int | 待验证数 |
| closedCount | int | 已闭环数 |
| closureRate | decimal | 闭环率（%） |
| severityBreakdown | array | 按等级分布：[{severity, count}] |
| sourceBreakdown | array | 按来源分布：[{sourceType, count}] |
| overdueCount | int | 超期未闭环数 |
| escalationCount | int | 当前活跃升级数 |

**analysis 响应 data（按 dimension 返回聚合结果）：**

dimension 可选值：`defectDesc`（不良描述，GROUP BY 文本，可点击下钻）/ `supplier`（供应商）/ `material`（物料）/ `time`（时间趋势）

```json
{
  "dimension": "defectDesc",
  "items": [
    { "defectDesc": "虚焊", "count": 12, "ratio": 24.5 },
    { "defectDesc": "外观划痕", "count": 8, "ratio": 16.3 }
  ]
}
```

> 前端 M2 多维分析中"按不良类型"维度不做下拉框，聚合出的不良描述作为可点击项，点击弹出该类型明细值。

---

### m2-7 异常闭环

```
POST /api/v1/exceptions/{id}/close
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| closeReason | string | 是 | 闭环原因/总结 |

**业务规则：**
- 闭环前置条件：至少一条改善措施 status=DONE + 至少一条验证记录 result=通过
- 闭环后 exception_order.status='已闭环' + closedAt=当前时间

---

### m2-8 批量升级检查

```
POST /api/v1/escalations/check
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| supplierId | long | 否 | 若不传则检查全部供应商 |
| daysWindow | int | 否 | 重复问题窗口天数（默认90） |
| minRepeatCount | int | 否 | 触发阈值（默认3次） |

**响应 data：**

```json
{
  "totalChecked": 5,
  "triggeredSuppliers": [
    {
      "supplierId": 1,
      "supplierName": "盛X",
      "defectDesc": "虚焊",
      "repeatCount": 4,
      "windowDays": 90,
      "shouldEscalate": true,
      "relatedExceptionIds": [1, 3, 5, 7]
    }
  ]
}
```

---

## 附录：状态码扩展

| code | 枚举 | 说明 |
|------|------|------|
| 0 | SUCCESS | 成功 |
| 400 | BAD_REQUEST | 参数校验失败 |
| 401 | UNAUTHORIZED | 未认证 |
| 403 | FORBIDDEN | 无权限 |
| 404 | NOT_FOUND | 资源不存在 |
| 409 | CONFLICT | 乐观锁冲突/数据已被修改 |
| 500 | INTERNAL_ERROR | 服务器内部错误 |
| 2001 | EXCEPTION_CLOSE_DENIED | 闭环前置条件不满足（缺少措施或验证） |
| 2002 | TRACE_LEVEL_EXCEEDED | 追溯层级超过上限（8层） |
| 2003 | NODE_SELF_REFERENCE | 追溯节点自引用（parentId==id） |
| 2004 | CIRCULAR_REFERENCE | 追溯节点循环引用检测 |

---

> **文档结束 · M0/M1/M2 API V1.0（2026-07-17）**
> 下一步：数据库 Agent 据此输出 DDL → 前端 Agent 据此输出类型/Mock/页面 → 后端 Agent 据此输出接口实现。
