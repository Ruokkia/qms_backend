-- M3 电子签名合规整改（风险一/二）：fai_signature 新增 content_hash 列
-- 用于存储「绑定完整检验记录内容」的 SHA-256 摘要（含逐项实际值/判定/标准值/上下限 + 主表结论），
-- 满足 21 CFR Part 11「电子签名须与所签记录内容绑定、防事后篡改、可审计追溯」要求。
-- 历史签名行 content_hash 保持 NULL，按 LEGACY 祖父条款认可（见 SignatureIntegrity.LEGACY_UNVERIFIABLE），
-- 不强制重签，控制爆炸半径。

ALTER TABLE qms.fai_signature ADD COLUMN content_hash character varying(64);

COMMENT ON COLUMN qms.fai_signature.content_hash IS
  '内容绑定哈希（SHA-256）：覆盖 faiNo|signerId|signedAt|signReason|inspectionResult|逐项(actualValue|result|standardValue|upperLimit|lowerLimit)，用于读取时完整性复核';
