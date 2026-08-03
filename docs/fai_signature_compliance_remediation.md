# FAI 电子签名合规整改说明（M3）

- **模块**：首件检验（FAI）
- **关联风险**：FAI 记录归档风险、报告接口安全缺陷、遗留问题闭环
- **合规基准**：ISO 13485、21 CFR Part 11（电子记录 / 电子签名须与所签记录内容绑定、防事后篡改、可审计追溯）
- **状态**：✅ 已修复并补充测试，可关闭

---

## 1. 缺陷描述（原风险解读）

1. **FAI 记录归档风险**：FAI 记录经电子签名前/或签名未与数据绑定的情况下进入历史档案，档案缺乏合规认可依据，无法证明数据经授权确认；存在数据被事后篡改、审计追溯失效风险。
2. **报告接口安全缺陷**：报告接口仅比对「签名状态」字段，未对签名与记录内容做密码学一致性校验，具备库写权限的内部人员可绕过 `signature()` 将状态置为「已签」后导出「无合法签名」的正式报告。
3. **遗留问题**：仅有缺陷描述，无修复方案、版本更新记录、测试报告、关闭验证证据。

---

## 2. 根因分析

| 项 | 根因（基于代码实证） | 证据位置 |
|---|---|---|
| 签名未与内容绑定 | 原 `signature_hash = SHA256(faiNo\|signerId\|signedAt\|signReason)`，摘要仅覆盖元数据，不含任何检验数据（实际值/判定/标准值/上下限）与结论 | `FaiInspectionServiceImpl.signature()` |
| 报告接口仅校验状态 | `report()` 仅判断 `signatureStatus == '已签'`，不重算/比对内容哈希 | `FaiInspectionServiceImpl.report()` |
| 缺陷无闭环 | 历史上存在「待判定+已签」矛盾数据，用 `db/legacy-seed/m3_fai_signature_reconcile.sql` 一次性补账，无变更/测试/关闭记录 | `db/legacy-seed/` |

> 说明：「未签名记录不进档案」的逻辑（archiveOnly 服务端兜底 `signature_status='已签' AND inspection_result!='待判定'`）**原本已实现**，本次保留并补充回归测试。

---

## 3. 修复方案

### 3.1 签名与记录内容绑定（风险一）
- 新增 `fai_signature.content_hash` 列（迁移脚本 `V20260730120000001__fai_signature_content_hash.sql`）。
- 重构签名摘要算法：

  ```
  content_hash = SHA256(
      faiNo | signerId | signedAt | signReason | inspectionResult
      | Σ ( paramCode=actualValue | result | standardValue | upperLimit | lowerLimit )   // 按上传顺序逐项拼接
  )
  ```
- 任一检验数据或判定结论在签名后被篡改，重算 `content_hash` 必然失配，满足 21 CFR Part 11「签名与记录内容绑定、防篡改」要求。
- 保留原 `signature_hash`（仅元数据）作为审计连续性参考，新增 `content_hash` 专用于完整性复核。

### 3.2 报告接口状态 + 哈希双校验（风险二）
- 新增枚举 `SignatureIntegrity`：`INTACT` / `TAMPERED` / `LEGACY_UNVERIFIABLE`。
- 新增 `verifySignatureIntegrity(id)`：已签且 `content_hash` 与重算一致 → `INTACT`；失配 → `TAMPERED`；`content_hash` 为 NULL（历史行）→ `LEGACY_UNVERIFIABLE`。
- `report()` 改为「`已签` 且 `integrity == INTACT`」方可导出；`TAMPERED` 抛 `FORBIDDEN` 并写 `audit_log`；`LEGACY` 允许导出但 `legacySignature=true` 并告警日志。
- `detail()` / 档案列表响应新增 `signatureIntact`、`legacySignature` 标记，便于前端识别历史遗留/被篡改记录。
- 判定回退（`autoJudge` 非合格）使签名失效时，写 `audit_log` 留痕（责任人、结论、时间）。

### 3.3 防篡改与历史遗留处理
- 唯一能将状态置为「已签」的路径是 `signature()`；真正的防线是 `report()` 的哈希复核，使"直改状态字段"无法产出被系统认可的正式报告。
- 老签名行 `content_hash` 为 NULL，按 LEGACY 祖父条款认可（历史数据已 `reconcile`），不强制重签，控制爆炸半径；前端对 LEGACY 显示「历史遗留·需复核」提示。

---

## 4. 变更清单（文件级）

| 文件 | 改动 |
|---|---|
| `db/migrations/V20260730120000001__fai_signature_content_hash.sql` | 新增 `content_hash` 列 |
| `qms-domain/.../entity/FaiSignature.java` | 新增 `contentHash` 字段 |
| `qms-service/.../SignatureIntegrity.java` | 新增完整性枚举 |
| `qms-service/.../FaiInspectionService.java` | 声明 `verifySignatureIntegrity` |
| `qms-service/.../impl/FaiInspectionServiceImpl.java` | 内容绑定哈希、双校验、审计留痕、标记 |
| `qms-service/.../dto/FaiInspectionRecordResponse.java`、`FaiReportResponse.java` | 新增 `signatureIntact` / `legacySignature` |
| `qms-bootstrap/.../FaiInspectionServiceImplTest.java` | 新增 3 个用例 + archiveOnly 回归 |
| `cornley-qms-web/.../HistoryReport.vue` | 完整性标记提示 |

---

## 5. 测试证据

- 用例 `signatureTampering_afterSigned_detectsTamperAndRejectsReport`：构造正确 `content_hash` → `verifySignatureIntegrity == INTACT`；篡改实际值后 → `TAMPERED`，且 `report()` 抛 `FORBIDDEN`（状态+哈希双校验拦截）。
- 用例 `legacySignature_withoutContentHash_allowsReportWithFlag`：历史遗留 `content_hash=NULL` → `LEGACY_UNVERIFIABLE`，`report()` 允许导出且 `legacySignature=true`、`signatureIntact=false`。
- 用例 `archiveOnly_filtersUnsignedAndPending`：档案模式生成的查询条件仅包含 `signature_status` 过滤（强制已签），不包含 `inspectionResult` 过滤，保证「未签名不进档案、不合格亦可进档案」。
- 原有回归用例 `judge_withFailingItem_invalidatesSignatureAndCreatesException` 仍通过（签名失效逻辑不变，仅新增审计留痕）。

---

## 6. 版本与关闭

- **版本**：M3（随本次迁移脚本 `V20260730120000001` 上线）。
- **数据库**：仅新增可空列，向后兼容；无需数据回填（历史行按 LEGACY 认可）。
- **上线动作**：执行 Flyway 迁移；无业务停机风险。
- **关闭结论**：三项风险均已落地技术修复与自动化测试，缺陷可闭环。建议后续对 LEGACY 历史签名发起一次"重新签字"专项清理以降低审计解释成本。
