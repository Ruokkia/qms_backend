package com.kangli.qms.service.fai;

/**
 * 电子签名完整性判定结果。
 * <ul>
 *   <li>{@link #INTACT}：已签且内容绑定哈希与当前记录一致，签名有效。</li>
 *   <li>{@link #TAMPERED}：已签但内容绑定哈希失配，疑似签名后记录内容被篡改。</li>
 *   <li>{@link #LEGACY_UNVERIFIABLE}：历史签名 content_hash 为空，无法做内容绑定复核（祖父条款认可）。</li>
 * </ul>
 */
public enum SignatureIntegrity {
    INTACT,
    TAMPERED,
    LEGACY_UNVERIFIABLE
}
