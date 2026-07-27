-- M3 签名数据补录：为「已签但缺失 fai_signature 行」的检验单补建签名记录
-- 使签名状态(已签)与签名历史表一致，历史报告可正常展示签名信息。
-- hash 算法与后端一致：SHA256(fai_no|signer_id|signedAt(yyyy-MM-dd HH:mm:ss)|sign_reason)

WITH ins AS (
  SELECT 'FAI-MZ-20260719-0001' AS fno,
         'mz_insp01' AS sid,
         to_char(now(), 'YYYY-MM-DD HH24:MI:SS') AS sat
)
INSERT INTO qms.fai_signature
  (fai_record_id, signer_id, signer_name, sign_type, signature_hash, signed_at, sign_reason, plant_code, plant_name, created_by, updated_by)
SELECT
  10, ins.sid, ins.sid, '检验签',
  encode(sha256((ins.fno || '|' || ins.sid || '|' || ins.sat || '|' || '历史数据迁移补录（原签名记录缺失）')::bytea), 'hex'),
  now(), '历史数据迁移补录（原签名记录缺失）', 'MZ', '梅州', 'mz_insp01', 'mz_insp01'
FROM ins
WHERE NOT EXISTS (
  SELECT 1 FROM qms.fai_signature s WHERE s.fai_record_id = 10 AND s.is_deleted = 0
);

SELECT '--- 校验：已签记录应均有签名行 ---' AS info;
SELECT r.id, r.fai_no, r.inspection_result, r.signature_status,
       (SELECT count(*) FROM qms.fai_signature s WHERE s.fai_record_id = r.id AND s.is_deleted = 0) AS sig_cnt
FROM qms.fai_inspection_record r
WHERE r.signature_status = '已签' AND r.is_deleted = 0
ORDER BY r.id;
