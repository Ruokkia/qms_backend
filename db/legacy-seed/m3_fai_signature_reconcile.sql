-- M3 签名数据对账：修正「判定结论=待判定 但 签名状态=已签」的历史矛盾记录
-- 依据新规则：只有提交判定得出合格/不合格后才能电子签名。
-- 处理：将此类记录的 signature_status 回退为「未签」；保留 fai_signature 历史签名行（满足签名留痕、历史报告可查）。
UPDATE qms.fai_inspection_record
SET signature_status = '未签', updated_at = now()
WHERE inspection_result = '待判定'
  AND signature_status = '已签'
  AND is_deleted = 0;

SELECT '--- 回退记录数 ---' AS info;
SELECT count(*) AS reset_count
FROM qms.fai_inspection_record
WHERE inspection_result = '待判定' AND signature_status = '已签' AND is_deleted = 0;

SELECT '--- 当前主单 状态分布 ---' AS info;
SELECT inspection_result, signature_status, count(*) AS cnt
FROM qms.fai_inspection_record
WHERE is_deleted = 0
GROUP BY inspection_result, signature_status
ORDER BY inspection_result, signature_status;

SELECT '--- 历史签名行（保留，可在报告查看）---' AS info;
SELECT fai_record_id, signer_name, sign_type, signed_at
FROM qms.fai_signature
WHERE is_deleted = 0
ORDER BY fai_record_id;
